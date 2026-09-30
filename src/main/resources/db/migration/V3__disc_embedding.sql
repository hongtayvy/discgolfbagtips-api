-- One vector per mold. The passage that produced it is stored alongside so the API can quote the
-- exact text a match was made against instead of asserting a similarity score on faith.
create table disc_embedding (
    disc_id     varchar(64)  primary key references disc (id) on delete cascade,
    model       varchar(160) not null,
    dimensions  integer      not null,
    source_hash varchar(64)  not null,
    passage     text         not null,
    descriptors text         not null default '',
    embedding   vector(384)  not null,
    updated_at  timestamptz  not null default now()
);

create index disc_embedding_model_idx on disc_embedding (model);

-- HNSW over cosine distance: the retrieval query orders by `embedding <=> query`.
create index disc_embedding_hnsw_idx on disc_embedding
    using hnsw (embedding vector_cosine_ops);
