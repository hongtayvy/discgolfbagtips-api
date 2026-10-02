# Deploying the database to Supabase

The schema is Flyway-managed, so pointing the app at an empty Supabase project is all that is
required — all eight migrations run on first boot, including the pgvector extension, the curated
plastic and bag reference data, and the Spring Session tables.

## 1. Create the project

Create a Supabase project and note the database password. Nothing else needs configuring in the
dashboard: `V1__enable_pgvector.sql` enables the extension itself, and Supabase permits that.

## 2. Use the session pooler, not the transaction pooler

Supabase exposes three connection paths, and only two of them work here:

| Path | Port | Works? |
| --- | --- | --- |
| Session pooler | 5432 | **Yes — use this** |
| Direct connection | 5432 | Yes, but IPv6-only, which many hosts cannot reach |
| Transaction pooler | 6543 | **No** without extra config |

The transaction pooler does not support prepared statements, which Hibernate relies on. If you must
use it, append `?prepareThreshold=0` and expect worse performance. The session pooler has neither
problem.

## 3. Set the environment

```bash
SUPABASE_DB_URL=jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
SUPABASE_DB_USER=postgres.<project-ref>
SUPABASE_DB_PASSWORD=<the database password>
```

Note the username form: the pooler needs `postgres.<project-ref>`, not bare `postgres`.

## 4. Boot it

```bash
java -jar target/discgolfbagtips-api-0.1.0-SNAPSHOT.jar
```

Expect, in order: Flyway applying 8 migrations, then the catalog sync firing because `disc` is empty
(~1,206 discs from DiscIt), then `GET /api/v1/status` reporting `embedding.coverage: 0.0`. Embed the
catalog with one call:

```bash
curl -X POST -H "X-Admin-Token: $ADMIN_TOKEN" https://<host>/api/v1/admin/embeddings/backfill/all
```

Poll `GET /api/v1/admin/embeddings/progress` until it reports `COMPLETED`.

## The one gotcha, already handled

Supabase installs pgvector into an `extensions` schema rather than `public`. A connection using the
default `search_path` therefore cannot resolve the `vector` type, and `V3__disc_embedding.sql` fails
with *"type vector does not exist"* — a confusing error, since the extension plainly is installed.

`application.yml` sets `spring.datasource.hikari.connection-init-sql: SET search_path TO public,
extensions` on every pooled connection, which fixes Flyway and runtime queries together. It is
harmless on a plain Postgres, where Postgres silently ignores the schema that is not there.

## Embedding on a deployed instance

Ollama is not reachable from Render, so a deployed instance needs the hosted provider:

```bash
EMBEDDING_PROVIDER=huggingface
HUGGINGFACE_API_TOKEN=hf_...
```

Vectors embedded locally by Ollama are stored under the same model identity
(`Snowflake/snowflake-arctic-embed-s`), so in principle a locally embedded catalog stays queryable
from a hosted instance — **but that is unverified**. Run `ProviderCompatibilityCheck` before relying
on it, or simply re-run the backfill once against Hugging Face: it takes a couple of minutes and
removes the question entirely.

## Deploying the API to Render

See the Render section of the README. The two settings that are easy to miss:

- `SESSION_COOKIE_SAME_SITE=none` (with `SESSION_COOKIE_SECURE=true`). The front end and API are
  different sites, and browsers do not send `SameSite=Lax` cookies on cross-site fetches, so saved
  bags would silently never persist.
- `PUBLIC_BASE_URL` must match the service's actual URL, or Swagger's "Try it out" targets the wrong host.
