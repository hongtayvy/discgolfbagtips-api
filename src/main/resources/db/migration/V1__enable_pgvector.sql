-- Supabase ships pgvector; on a plain Postgres the extension must be installed first.
create extension if not exists vector;
