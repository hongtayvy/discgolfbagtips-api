# Disc Golf Bag Tips API

A retrieval-augmented (RAG) disc golf bag analysis service. Send it your current bag — mold,
plastic, weight and wear per disc — three questions' worth of player profile, and today's
conditions; it tells you which disc to add next, in what weight, and shows its work: the exact
vector matches and flight-number deltas that produced the answer.

Java 25 · Spring Boot 4.1 (Spring Framework 7) · Supabase Postgres + pgvector · Hugging Face hosted
embeddings · Groq / OpenRouter for reasoning.

---

## What it does

| Endpoint | Purpose |
| --- | --- |
| `POST /api/v1/recommendations` | Analyse a bag and recommend the next disc, with an explainability payload |
| `POST /api/v1/lineup` | The whole bag slot by slot — putter / midrange / fairway / distance — with per-slot gaps and suggestions |
| `GET /api/v1/recommendations/latest` | Re-read this session's last recommendation |
| `GET /api/v1/discs/search?q=buzz&limit=10` | Type-ahead disc search (backs the front end's disc picker) |
| `GET /api/v1/discs/{id}` | One disc by catalog id |
| `GET /api/v1/plastics?brand=Discraft` | Plastic blends, optionally per manufacturer |
| `GET /api/v1/brands` | Disc manufacturers, for populating a filter control |
| `GET /api/v1/bags?capacity=18&brand=GRIPeq` | Physical bag models — capacity, weight, dimensions |
| `GET /api/v1/bags/brands`, `GET /api/v1/bags/{id}` | Bag manufacturers; one bag model |
| `GET/POST /api/v1/profiles`, `GET/DELETE /api/v1/profiles/{id}` | Named bag setups saved per session |
| `GET /api/v1/status` | Catalog / embedding / generation readiness |
| `GET /api/v1/session`, `POST /api/v1/session/bag`, `DELETE /api/v1/session` | Session-scoped bag state |
| `POST /api/v1/admin/catalog/sync`, `POST /api/v1/admin/embeddings/backfill` | Manual pipeline triggers (`X-Admin-Token`) |

Swagger UI is at `/docs`, the OpenAPI document at `/v3/api-docs`, health at `/actuator/health`. A
Postman collection generated from that document lives in
[docs/postman](docs/postman/disc-golf-bag-tips.postman_collection.json) — import it and every request
is runnable as-is. Regenerate with `python3 docs/postman/generate.py` rather than editing it by hand.

## Request and response

```jsonc
// POST /api/v1/recommendations
{
  "bag": [
    // plastic, weightGrams and wear are all optional; each one supplied sharpens the analysis
    { "discId": "aa24b5ff-…", "plastic": "Champion", "weightGrams": 175, "wear": "NEW" },
    { "name": "Buzzz", "brand": "Discraft", "plastic": "ESP", "weightGrams": 177, "wear": "BEAT_IN" },
    { "name": "Teebird", "brand": "Innova" }
  ],
  "profile": {
    "skillLevel": "INTERMEDIATE",      // BEGINNER | INTERMEDIATE | ADVANCED | PROFESSIONAL
    "throwingStyle": "FOREHAND",       // BACKHAND | FOREHAND | BOTH
    "courseType": "WOODED"             // WOODED | OPEN | MIXED
  },
  // wear: NEW | SEASONED | BEAT_IN | WELL_WORN
  "conditions": { "weather": "WINDY" } // HOT | NORMAL | RAINY | COLD | WINDY
}
```

The response carries the pick (including a suggested weight window), three alternatives, the bag
analysis with per-disc stability arithmetic, and — the part that matters — an `explainability` block:

```jsonc
"explainability": {
  "retrievalMode": "VECTOR_SIMILARITY",
  "retrievalQuery": "Bag gap: looking for an overstable putter / approach to add to the bag…",
  "embedding": { "provider": "huggingface-inference-api", "model": "sentence-transformers/all-MiniLM-L6-v2",
                 "dimensions": 384, "stubbed": false },
  "generation": { "provider": "groq", "model": "llama-3.3-70b-versatile", "source": "LANGUAGE_MODEL" },
  "vectorMatches": [
    { "rank": 1, "name": "Zone", "brand": "Discraft", "similarity": 0.83,
      "matchedDescriptors": ["overstable putter / approach", "forehand flex shots"],
      "deltaVsGapTarget": { "speed": 1, "glide": 0, "turn": 0, "fade": 0 },
      "passageExcerpt": "Disc: Discraft Zone, a putter…", "chosen": true }
  ],
  "flightGaps": [
    { "slot": "PUTT_AND_APPROACH", "stabilityClass": "OVERSTABLE", "kind": "MISSING", "severity": 1.55,
      "reason": "Nothing in the bag covers the putter / approach slot with overstable stability…",
      "target": { "speed": 3, "glide": 4, "turn": 0, "fade": 3 },
      "nearestInBag": { "name": "Buzzz", "flight": { "speed": 5, "glide": 4, "turn": -1, "fade": 1 } },
      "delta": { "speed": -2, "glide": 0, "turn": 1, "fade": 2 }, "selected": true }
  ],
  "citations": ["flight-gap:PUTT_AND_APPROACH/OVERSTABLE severity 1.55 — …",
                "vector-match#1:Discraft Zone — cosine similarity 0.8300 — shared terms: … [chosen]"],
  "timings": { "analyzeMs": 3, "retrieveMs": 210, "generateMs": 780, "totalMs": 995 },
  "degradations": []
}
```

`degradations` is never silently empty when something went wrong: a fallback path always names
itself there and in `retrievalMode` / `generation.source`.

## Two views of a bag

`POST /api/v1/recommendations` answers *"what should I buy next?"* — one disc, with a written case
for it from the reasoning model.

`POST /api/v1/lineup` answers *"how complete is my bag?"* — the same analysis reshaped into four
tabs, one per speed slot, each carrying the discs already there, a stability coverage grid, and
suggestions for every hole:

```
BAG LINEUP — 3 discs, coverage 19/100        VECTOR_SIMILARITY | 10/12 gaps retrieved | 942ms

=== PUTTER / APPROACH  [THIN]
    have: Innova Aviar Classic 2/3/0/0 -> plays very understable
    [x]very [ ]unde [ ]stab [ ]over [ ]very
    GAP overstable   sev=3.342  -> Zone, Pace, Roost

=== MIDRANGE  [THIN]
    have: Discraft Buzzz 5/4/-1/1 -> plays stable
    [ ]very [ ]unde [x]stab [ ]over [ ]very
    GAP overstable   sev=3.145  -> Buzzz OS, Zone, Ultra

=== DISTANCE DRIVER  [EMPTY]
    Nothing in the bag covers the distance driver slot.
    GAP overstable   sev=0.68   -> Skyway, Reach, Defender
```

Each slot reports `COMPLETE`, `THIN` or `EMPTY`, and all four are always present so the UI can render
stable tabs regardless of what is in the bag.

**The lineup deliberately does not call the reasoning model.** Its value is structural — what is
covered, what is missing, what fills it — and answering it needs one retrieval *per gap* rather than
one per request. A language-model call per gap would multiply cost and latency to produce prose the
grid already conveys. Retrieval is also capped: gaps below a severity floor are listed but not
searched for, which is why the sample above retrieves 10 of 12.

## Caching

Analyses are cached in-process with Caffeine for 24 hours, keyed on a hash of the bag, profile,
conditions, filters and carried bag — **not** on the session. Two players with the same bag get the
same answer, so they should share the cached one; per-session keys would almost never hit on a
low-traffic beta.

That matters because conditions are analysed lazily, one at a time. A player comparing windy against
hot and back again pays for each once:

```
same request, twice   : 1.07s -> 0.01s
windy -> hot (new key): 0.67s
```

Two deliberate choices:

- **Used explicitly rather than via `@Cacheable`**, so a degraded answer can be served without being
  stored. A thirty-second model outage must not become a day of quietly worse recommendations.
- **Transient failure is distinguished from missing configuration.** "The model timed out" is a blip
  and is never cached; "no API key is configured" is a settled state and caches normally. Conflating
  them meant the recommendation endpoint never cached at all until a key existed — which is exactly
  what the first version did.

Cache health is on `GET /api/v1/status` (`entries`, `hits`, `misses`, `hitRatePercent`).

**On Cloudflare KV:** complementary, not an alternative. Caffeine sits in the API, costs nothing and
has no quota, but is lost when a free-tier dyno spins down. KV sits at the edge and avoids the hop to
Render entirely, but its free tier limits *writes* (~1,000/day, worth re-checking) and every cache
miss is a write. Edge caching belongs with the front end.

## Bag profiles

Named bag setups — a "wooded / East Coast" bag kept separately from an "open / West Coast" one.
Saving under an existing name replaces it; loading returns the full request body, ready to re-post
to `/recommendations` or `/lineup`.

Owned by the session today. `owner_key` is deliberately an opaque string (`session:<id>` now,
`user:<uuid>` later) rather than a foreign key to a user table, so Supabase auth becomes an `UPDATE`
via `claimSessionProfiles` rather than a schema migration — and profiles ship before accounts do.

## Redundancy: the other half of a bag analysis

Gaps are only half the question. `POST /api/v1/recommendations` and `/lineup` both report discs that
duplicate each other's job, and — crucially — how loudly that is worth saying.

The naive version flags any two discs in the same slot and stability class. That is wrong twice
over: it misses duplicates split across a cell boundary (a 150 g and a 175 g Destroyer fall either
side of one while being the same disc in two weights), and it shouts at pro bags where repeated
molds are deliberate. Two corrections:

- **Separation, not grouping.** Discs are clustered within a slot by how far they actually fly apart
  once plastic, weight and wear are applied — single-linkage, so a chain of three progressively-worn
  copies stays one group instead of splitting into pairs.
- **Scaled to the thrower.** A subtle flight difference only exists if the player can produce it.
  Sensitivity falls with skill, and falls further when wear data shows the duplication is intentional.

The same bag, three Destroyers at different wear levels:

| Skill | Level | Severity | Verdict |
| --- | --- | --- | --- |
| Beginner | `WARNING` | 1.02 | "effectively interchangeable — dropping one frees a slot" |
| Professional | `FYI` | 0.15 | "different wear levels — usually a deliberate spread rather than duplication" |

> Building this surfaced a bug: `BagResolver` had been silently dropping repeated molds as
> "uninformative", which was true while the analysis only looked for holes and exactly backwards once
> it looked for duplication. Duplicates now reach the analyzer.

## Bags, carry weight and fitting

A bag analysis is not only about discs. `bag_model` holds the physical bag — capacity, empty weight,
dimensions, build tier — for 14 models across GRIPeq, Innova, Discraft, Squatch and Zuca.

Pass `carriedBag` on a lineup request and the response gains a carry-weight analysis:

```
CARRIED: GRIPeq G-Series — capacity 12, 2.4 lb empty, PREMIUM
LOAD   : 14 discs = 2452g, + bag 1089g -> 3541g (7.8 lb)
         estimated=true (assumed 1), overpacked=true
   ! 1 of 14 discs had no weight given; the slot's typical weight was assumed.
   ! 14 discs exceeds the 12 this bag is rated for.

BETTER FITTING:
  GRIPeq BX3              +4 spare, 9.6 lb loaded
      1.8 lb heavier than GRIPeq G-Series, holding 18 discs against your 12.
  Innova Adventure Pack  +11 spare, 7.4 lb loaded
      0.4 lb lighter than GRIPeq G-Series, holding 25 discs against your 12
      — but it is a mid build against your premium one, so the saving is not free.
```

Three things this gets deliberately right:

- **`estimated` is not cosmetic.** Disc weight is optional on the wire, so missing entries fall back
  to the slot's reference weight — the same figure the stability model already uses. Any fallback
  flips the flag, because a total presented as measured when part of it was assumed is worse than an
  honest approximation.
- **Suggestions are not ranked by weight alone.** A lighter bag can be a worse bag; build tier
  travels with every comparison and the text says when a saving comes with a downgrade.
- **Unknowns stay null.** Zuca publishes no empty weights and several Innova bags omit them. Those
  rows carry `null`, and the analysis reports disc load only rather than inventing a total.

### Where the specs came from

Hand-researched from manufacturer listings, not scraped — 6 of 14 rows have a published empty
weight, 5 have full dimensions, and the rest are honest gaps. Every row carries a `source` column
(`MANUAL_RESEARCH`) so a later refresh can tell curated rows from fetched ones.

## Filters

Both `/recommendations` and `/lineup` accept an optional `filters` block — brand loyalty, or what the
local shop actually stocks:

```jsonc
"filters": {
  "brands": ["Discraft", "Dynamic Discs"],   // recommend only these
  "excludeBrands": ["Innova"],               // never recommend these
  "maxSpeed": 9                              // on top of the skill-implied ceiling
}
```

Omit the block to search the whole catalog. `GET /api/v1/brands` returns the 51 names the field
accepts, and `explainability.appliedFilters` echoes what was actually used.

**Filters are applied inside the SQL, not by filtering results afterwards** — and that is not a
micro-optimisation. Retrieval over-fetches 40 candidates before ranking. Asking for a small
manufacturer like Dino Discs (8 of 1,206 discs), the best-ranked one sits at position **267** in the
unfiltered search: a post-filter would return nothing at all, while constraining the query lets
similarity rank *within* the allowed set and returns the right answer. It is the same reason the
slot's speed window is a `WHERE` clause rather than a Java `filter`.

A filter that genuinely matches nothing returns **422 `no-match-for-filters`** naming the constraint,
rather than the 503 that means the catalog is not ready — the caller can fix the first by relaxing
their filter, and can do nothing about the second.

## Architecture

```
POST /api/v1/recommendations
  → RecommendationController
      → RecommendationService                         (orchestration only)
          ├─ BagResolver + BagAnalyzer                 deterministic gap analysis
          ├─ (1) EmbeddingService                      passage → Hugging Face → 384-d vector
          ├─ (2) RetrievalService → DiscVectorStore     pgvector cosine search + speed window
          └─ (3) GenerationService → Groq / OpenRouter  grounded reasoning over the candidates
      → ExplainabilityAssembler → assembled response
```

Longer write-up, including why the embedding text is built the way it is:
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Data pipeline

The disc catalog comes from the open-source [DiscIt API](https://discit-api.fly.dev) (~2,400 molds).
It is **synced into our own Postgres on a schedule** (nightly 03:15 UTC, plus once on startup when
the table is empty) rather than called live — DiscIt is a volunteer-run free service, and user
traffic should never depend on a third party being up.

DiscIt publishes molds and flight numbers but **not plastics**, so the plastic axis is curated
locally in `plastic_type` (64 blends across 14 manufacturers plus cross-brand fallbacks, seeded by
[`V4__seed_plastic_types.sql`](src/main/resources/db/migration/V4__seed_plastic_types.sql)). Each
blend carries a `stability_shift` applied to a mold's `turn + fade`, so a Champion Teebird and a DX
Teebird are different discs to the analyzer, as they are to a player.

A second scheduled job embeds any disc whose vector is missing or stale (detected by a content hash
over the fields that feed the passage), capped at 200 discs per run to stay inside a free Hugging
Face account.

## Plastic, weight and wear

Nobody throws a mold — they throw *their* copy of it. Three per-instance axes separate the two, none
of which exist in the upstream catalog:

| Axis | Effect on stability | Example |
| --- | --- | --- |
| **Plastic** | −0.4 to +0.5, from the curated table | DX flies more understable than Champion |
| **Weight** | 0.04 per gram from the slot's reference weight, capped at ±1.5 | a 152 g driver turns over; a 175 g one does not |
| **Wear** | 0 to −1.8, **damped by the plastic's durability** | a season beats in DX; Champion shrugs it off |

The damping is the interesting part. The same Teebird, equally worn:

| | Stability index | Class |
| --- | --- | --- |
| New, Champion, 175 g | 2.44 | overstable |
| Well worn, Champion, 175 g | 1.54 | overstable, softer |
| Well worn, DX, 168 g | −1.06 | **understable** |

The coverage matrix is built from *effective* stability, so a beat-in Firebird stops counting as your
overstable driver and the gap it leaves is found. Every response shows the arithmetic
(`2 published -0.3 plastic -0.4 weight -1.4 wear = -0.1`) so a surprising pick can be traced rather
than argued with. All three axes are optional on the wire.

## Deploying

- **Database — Supabase.** [docs/SUPABASE.md](docs/SUPABASE.md) covers it, including the one trap
  worth knowing: Supabase installs pgvector into an `extensions` schema, so a connection with the
  default `search_path` cannot resolve the `vector` type and the migrations fail with a confusing
  *"type vector does not exist"*. Handled by a Hikari `connection-init-sql`, harmless elsewhere.
  Use the **session pooler** (port 5432), not the transaction pooler — the latter has no prepared
  statements, which Hibernate needs.
- **API — Render.** [render.yaml](render.yaml) is a complete blueprint. `EMBEDDING_PROVIDER` is
  pinned to `huggingface` there because Ollama runs on a developer machine and is unreachable from
  Render. `PUBLIC_BASE_URL` makes Swagger's "Try it out" target the deployed host instead of
  localhost.
- **Web UI — Cloudflare Pages.** Separate repository; `CORS_ALLOWED_ORIGINS` is where its origin goes.

## Running it

You need a Postgres with pgvector, and — for free local embedding — Ollama.

```bash
createdb dgbt && psql -d dgbt -c 'CREATE EXTENSION vector;'   # Postgres.app ships pgvector
psql -d dgbt -c "CREATE ROLE dgbtadmin LOGIN PASSWORD '…'; ALTER DATABASE dgbt OWNER TO dgbtadmin;"
ollama pull snowflake-arctic-embed:33m                        # 67 MB, the configured model
cp config/application-local.yml.example config/application-local.yml   # put the password here
```

(Supabase's free tier also ships pgvector; point `SUPABASE_DB_URL` at it instead. A
`pgvector/pgvector:pg17` container works too if you prefer Docker.)

```bash
mvn spring-boot:run
```

The Maven plugin activates the `local` profile, and Spring Boot loads `./config/` automatically —
so no profile flag and no environment variables. `config/` is gitignored, so the password stays out
of git; `LOCAL_DB_PASSWORD` also works and takes precedence.

On first boot Flyway creates the schema and seeds the plastics, then the catalog syncs itself
because the `disc` table is empty. Embed the whole catalog — one call, resumable, no waiting for the
nightly job:

```bash
curl -X POST -H "X-Admin-Token: local-dev-token" http://localhost:8080/api/v1/admin/embeddings/backfill/all
```

It returns 202 immediately; poll `GET /api/v1/admin/embeddings/progress`. All 1,206 discs take about
25 seconds locally. Then `GET /api/v1/status` should report `coverage: 1.0`.

> The upstream API returns every disc twice under one id, so 2,414 records become **1,206 discs**.
> The sync report counts duplicates separately rather than pretending they were insertions.

### Configuration

Everything is environment-driven; nothing secret has a default.

| Variable | Purpose |
| --- | --- |
| `SUPABASE_DB_URL` / `SUPABASE_DB_USER` / `SUPABASE_DB_PASSWORD` | Postgres connection |
| `HUGGINGFACE_API_TOKEN` | Hosted embeddings. Unset ⇒ local hashing vectorizer, responses marked `stubbed` |
| `EMBEDDING_PROVIDER` | `auto` (default), `huggingface`, `ollama`, or `local` |
| `OLLAMA_URL` / `OLLAMA_EMBEDDING_MODEL` | Local embedding server; defaults to `localhost:11434` and `snowflake-arctic-embed:33m` |
| `GENERATION_API_KEY` | Groq or OpenRouter key. Unset ⇒ deterministic rule-based explanations |
| `GENERATION_BASE_URL` / `GENERATION_MODEL` | Defaults to Groq + `llama-3.3-70b-versatile`; point at `https://openrouter.ai/api/v1` to switch |
| `ADMIN_TOKEN` | Enables the admin endpoints. Unset ⇒ they return 403 |
| `CORS_ALLOWED_ORIGINS` | Comma-separated front-end origins |

**The service boots and answers with no AI credentials at all.** Without a Hugging Face token it
embeds with a local hashing vectorizer; without a generation key it writes the explanation from a
deterministic template. Both are reported honestly in `explainability.degradations` — a demo that
quietly pretends a language model was involved would be worse than no demo.

## Resilience

- **Rate limiting.** Lazily-refilled token buckets per caller (session id, else forwarded client IP),
  with a separate tighter budget on `POST /api/v1/recommendations` because that endpoint spends
  embedding and reasoning-model quota. Responses carry `X-RateLimit-*`; refusals carry `Retry-After`.
- **Circuit breakers.** Resilience4j wraps each upstream independently (`discit`, `embedding`,
  `generation`) with its own retry policy and open-state duration, so a Hugging Face outage cannot
  trip the breaker protecting Groq, and neither affects catalog search. Breaker state is exposed at
  `/actuator/circuitbreakers`.
- **Graceful degradation.** Embeddings down → flight-number nearest-neighbour retrieval. Reasoning
  model down or off-script → deterministic explanation. Model names a disc that was not retrieved →
  overruled, and the response says so.
- **No idempotency keys.** There is no repeatable write-type request that would need one; the only
  state a caller creates is their own session.

## Session state

No user accounts. State lives under a `BAGTIPS_SESSION` cookie, persisted to Postgres via Spring
Session JDBC so a free-tier restart does not lose it. Attributes are stored as JSON rather than Java
serialization, so changing a response DTO cannot break live sessions. Return-visit support would
mean promoting `SessionState` to a real user record.

## Tests

```bash
mvn test
```

107 tests, no Docker or database required: the gap analyzer's scoring, the plastic/weight/wear
stability model, the embedding passage construction, the token bucket's refill maths, the generation
layer's four fallback paths, request validation and malformed-body handling, the rate-limit filter's
per-client accounting, and a context-load test that boots the whole bean graph against H2.

## Choosing an embedding model

`mvn test -Peval` runs a retrieval benchmark against this catalog rather than a general-purpose
leaderboard. It works in memory with no database and no credentials, and adds whichever providers
you have available:

```bash
mvn test -Peval                                                    # baselines only
OLLAMA_MODELS=all-minilm mvn test -Peval                           # + local, free
HF_MODELS=BAAI/bge-small-en-v1.5 HUGGINGFACE_API_TOKEN=hf_... mvn test -Peval
```

Relevance comes from the catalog's upstream human `category` and `stability` labels, which disagree
with the computed flight numbers for 30% of the catalog — so the flight-number baseline has no
access to the answer, and a model that reads the passage does. Output is precision@5/@10, recall@10,
MRR and lift over the base rate, per scenario and averaged.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#measuring-model-choice-instead-of-guessing-at-it).

## Roadmap

Monetization thinking, including the PDGA tournament-prep angle and its attribution obligations, is
in [docs/MONETIZATION.md](docs/MONETIZATION.md).

## Attribution

Disc catalog data from the [DiscIt API](https://github.com/cdleveille/discit-api). Flight numbers
are manufacturer-published figures; the descriptive terminology and plastic reference data in this
repository are original work.
