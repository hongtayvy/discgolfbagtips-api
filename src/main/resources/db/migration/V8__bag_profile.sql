-- Named bag setups a player can keep and re-analyse — "wooded / East Coast" against
-- "open / West Coast" — rather than retyping a bag every visit.
--
-- `owner_key` is deliberately an opaque string rather than a foreign key to a user table. Today it
-- holds "session:<id>"; once Supabase auth lands it holds "user:<uuid>", and claiming a session's
-- profiles at sign-up is an UPDATE rather than a migration. Building the user table first would mean
-- either blocking this on auth or throwing the schema away when auth arrives.
create table bag_profile (
    id             uuid          primary key default gen_random_uuid(),
    owner_key      varchar(128)  not null,
    name           varchar(120)  not null,
    description    varchar(512),

    -- The bag exactly as the analysis endpoints accept it, so a profile can be replayed without
    -- re-resolving anything: discs with plastic, weight and wear, plus profile and conditions.
    payload        jsonb         not null,

    -- Denormalised for listing without parsing every payload.
    disc_count     integer       not null default 0,
    bag_model_id   bigint        references bag_model (id),

    created_at     timestamptz   not null default now(),
    updated_at     timestamptz   not null default now(),
    last_viewed_at timestamptz,

    constraint bag_profile_name_uq unique (owner_key, name),
    constraint bag_profile_name_ck check (length(trim(name)) > 0)
);

create index bag_profile_owner_idx on bag_profile (owner_key, updated_at desc);
