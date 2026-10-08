# Architecture

## Request flow

```
POST /api/v1/recommendations
│
├── RateLimitFilter                 token bucket per caller, before anything expensive runs
│
└── RecommendationController
    └── RecommendationService                                    orchestration only, no AI logic
        │
        ├── BagResolver             free text / catalog ids → resolved molds + plastic blends
        ├── BagAnalyzer             deterministic (slot × stability) gap scoring
        │
        ├── (1) EmbeddingService    structured input → descriptive passage → 384-d unit vector
        │        └── HuggingFaceEmbeddingClient  |  LocalHashEmbeddingClient (no-token fallback)
        │
        ├── (2) RetrievalService    speed window + pgvector cosine search, bag exclusions
        │        └── DiscVectorStore
        │             ├── search()                  vector path
        │             └── searchByFlightNumbers()   fallback path
        │
        ├── (3) GenerationService   grounded reasoning over the retrieved candidates
        │        └── OpenAiCompatibleChatClient (Groq / OpenRouter)  |  rule-based template
        │
        └── ExplainabilityAssembler assembled response
```

Each sub-layer owns *how*; the service owns *what*. Nothing below the service knows it is serving an
HTTP request, and nothing above it knows which embedding provider is in play.

## The embedding text is the point

This is the design bet the project is built on.

Comparable tools appear to index the four flight numbers and little else. Embedding `12 5 -1 3`
gives a model four tokens with no meaning — at which point cosine similarity is an expensive
reimplementation of `ORDER BY abs(speed - ?)`, and you may as well write the SQL.

`EmbeddingTextBuilder` instead composes one passage per disc from three sources:

1. **The numbers**, stated plainly, plus the derived stability index.
2. **The plastic and its flight effect** — durability, grip, and the `stability_shift` that says how
   the blend moves the mold's flight. This axis does not exist in the upstream catalog at all.
3. **Player vocabulary** — what the disc is *for*. `FlightTerminology` maps numbers onto the language
   players actually use: hyzer flips, spike hyzers, forehand flex lines, tunnel shots, rollers,
   headwind behaviour, who the disc suits.

A resulting passage:

> Disc: Discraft Buzzz, a midrange in ESP plastic. Flight numbers: speed 5, glide 4, turn -1, fade 1
> (stability index 0.1, stable). Speed: moderate speed, reachable by almost any arm. … Stability:
> stable — holds the line it is put on and finishes with a mild fade; the straight workhorse.
> Plastic: ESP is a grippy premium blend (durability 4/5, grip 4/5). … Thrown for: straight tunnel
> shots, point-and-shoot lines, controlled approaches, wooded gap shots. In wind: workable in light
> wind, will turn over if a headwind gets strong. …

The **query is written in the same register** — `forGap()` produces "Bag gap: looking for an
overstable putter / approach … Thrown for: spike hyzers, utility shots out of trouble … In wind: the
headwind and crosswind answer" — so the query vector and the document vectors live in the same
descriptive space. That is what makes the retrieval step earn its keep.

Passages are stored alongside their vectors in `disc_embedding.passage`, so the API can quote the
exact text a match was made against instead of asserting a similarity score on faith.

## Why hybrid retrieval

Pure vector search over 2,400 molds cheerfully returns a speed-13 distance driver for a
putter-shaped question — the prose overlaps, the physics does not. `RetrievalService` therefore
narrows structurally first (a speed window from the gap's slot, capped by what the player can
actually throw as rated) and ranks semantically within it. Discs already in the bag are excluded by
both catalog id and mold name, so "the Buzzz I already own" is not recommended back in another
plastic.

## Why one vector per mold, not per (mold × plastic)

Sixty-four blends across 2,400 molds would be a six-figure index for a hobby project's free tier,
most of it never queried. Instead each mold gets one plastic-agnostic vector, and the plastic the
player names is applied arithmetically at analysis time via `stability_shift`. The plastic still
reaches the model: it is in the bag description inside the generation prompt, and in the
`plasticAdvice` the model is asked for.

## The three per-instance axes

The upstream catalog publishes one set of flight numbers per mold. But nobody throws a mold — they
throw *their* copy of it, and three things about that copy change how it flies:

| Axis | Source | Effect on the stability index | Where it lives |
| --- | --- | --- | --- |
| **Plastic** | Curated `plastic_type` table | `stability_shift`, −0.4 to +0.5 | `PlasticType` |
| **Weight** | Supplied per bag entry | 0.04 points per gram from the slot's reference weight, capped at ±1.5 | `DiscWeight` |
| **Wear** | Supplied per bag entry | 0 to −1.8, damped by the plastic's durability | `WearState` |

All three are optional. A player who knows only "I throw a Buzzz" still gets an answer; each axis
they fill in sharpens it.

### Weight

Published numbers describe a max-weight run, so `DiscWeight.referenceGrams(slot)` is the weight the
numbers are *about* (178 g for a midrange, 173 g for a distance driver — mids run heavier). Distance
from that reference becomes stability: lighter flies more understable and carries further for a
slower arm, heavier holds its rated stability and fights wind. The ±1.5 cap stops arithmetic from
turning a Firebird into a roller.

Weight also flows the other way: `DiscWeight.recommend()` produces a suggested weight window for the
disc being recommended, lighter for developing arms and heavier when the wind will punish a light
disc, and that window reaches both the retrieval query and the generation prompt.

### Wear, and why it depends on the plastic

Wear only ever moves a disc toward understable. But "beat in" means something completely different
in DX than in Champion, so `WearState.stabilityShift(durability)` damps the base shift by the
blend's durability (×1.4 at durability 1, ×0.5 at durability 5).

That interaction is the whole reason wear is worth modelling, and it reuses the plastic data already
in the table:

| Same mold, same wear | Stability index | Class |
| --- | --- | --- |
| Teebird 7/5/0/2, new, Champion 175 g | 2.44 | overstable |
| Teebird 7/5/0/2, well worn, Champion 175 g | 1.54 | overstable, but softer |
| Teebird 7/5/0/2, well worn, DX 168 g | −1.06 | **understable** |

The DX one has become a different disc; the Champion one is a milder version of itself. A model that
skipped the damping would call both of them flippy, which is wrong and which any experienced player
would spot immediately.

### Reading the arithmetic

`StabilityBreakdown` keeps the four terms separate rather than collapsing them, so the response can
show its working — `2 published -0.3 plastic -0.4 weight -1.4 wear = -0.1` — and a surprising
recommendation can be traced to the axis that caused it. When the adjustments move a disc into a
different stability class, `movedClass()` flags it, the analyzer raises a bag note, and the
generation prompt warns the model explicitly not to trust the published numbers for that disc.

The coverage matrix is built from **effective** stability, not published. That is what makes a
beat-in Firebird stop counting as the bag's overstable driver — the gap it leaves behind is real,
and flight numbers alone cannot see it.

### Why these stay out of the stored vectors

Weight and wear are per-instance, exactly like plastic, so they follow the same rule: one
plastic-agnostic vector per mold, and the player's specifics applied arithmetically at analysis
time. They reach the language model through the bag listing in the prompt, and they reach retrieval
through the query passage ("Preferred weight 159-169 g" for a beginner's fairway slot) — never through the stored document.

## Model choices

**Embeddings — `sentence-transformers/all-MiniLM-L6-v2` via Hugging Face hosted inference.** No
self-hosting and no fine-tuning. A few thousand discs is nowhere near enough data to beat a good
pretrained sentence encoder, and the passages are ordinary English by design. 384 dimensions keeps
the pgvector index small; the column type is fixed at `vector(384)`, so changing model means a
migration.

**Reasoning — Llama 3.3 70B on Groq, or anything OpenAI-compatible.** Groq and OpenRouter speak the
same dialect, so `OpenAiCompatibleChatClient` covers both and `bagtips.generation.base-url` picks.
The model is given the retrieved passages as its only disc knowledge and told so; it is asked for
strict JSON and its choice is validated against the candidate list.

## The model is never trusted blindly

| Failure | Handling |
| --- | --- |
| Model names a disc that was not retrieved | Top candidate substituted, `source: LANGUAGE_MODEL_CORRECTED`, note in the response |
| Model returns prose instead of JSON | Deterministic template writes the answer |
| Model wraps its JSON in a code fence | Fence stripped, answer used |
| Model unreachable, or no API key | Deterministic template, reason recorded in `degradations` |
| Embedding provider unreachable | Flight-number nearest-neighbour retrieval, `retrievalMode` says so |

## Storage

Hibernate maps `disc` and `plastic_type`; it does not map pgvector's `vector` type, so
`disc_embedding` is reached with `JdbcClient` and plain SQL, passing the embedding as a literal
Postgres casts. An HNSW index over `vector_cosine_ops` backs the `<=>` ordering.

Schema is Flyway-managed, including the Spring Session tables — session storage is versioned like
everything else rather than left to `initialize-schema`.

## Scheduled work, not live calls

| Job | Cadence | What it does |
| --- | --- | --- |
| `CatalogSyncJob` | 03:15 UTC nightly, plus on startup when the catalog is empty | Upserts DiscIt into `disc`; molds that vanish upstream are deactivated, not deleted, so an old saved bag still resolves |
| `EmbeddingBackfillJob` | 03:45 UTC nightly | Embeds discs whose vector is missing or whose content hash changed, ≤200 per run |
| `RateLimitService.evictIdleBuckets` | every 10 minutes | Drops buckets idle for 30 minutes, bounding the map without an LRU |

## Measuring model choice instead of guessing at it

`EmbeddingModelEvaluation` (test sources, `-Peval`) compares candidate embedding models on *this*
catalog and *these* questions. MTEB leaderboards rank models on web and Wikipedia retrieval;
"an understable midrange to hyzer-flip on tight wooded lines" is a different distribution, and the
indexed passages are generated prose about flight characteristics.

It runs fully in memory — catalog from DiscIt (cached to disk), cosine over a list — so it needs no
Postgres, no Docker and no credentials to run the baselines. It uses the production
`EmbeddingTextBuilder`, so it measures the real passage construction rather than a copy.

```bash
./mvnw test -Peval                                       # baselines only, zero setup
OLLAMA_MODELS=all-minilm,snowflake-arctic-embed ./mvnw test -Peval
HF_MODELS=BAAI/bge-small-en-v1.5 HUGGINGFACE_API_TOKEN=hf_... ./mvnw test -Peval
```

### Ground truth, and a trap worth recording

Relevance is defined by the catalog's upstream `category` and `stability` labels — assigned by
people, not derived from the four numbers. That distinction is the whole basis of the comparison:
those labels **disagree with computed `turn + fade` for 30% of the catalog** and with the
speed-derived slot for 16%. The labels appear verbatim in the embedded passage, so a model can read
them; the flight-number baseline cannot.

The first version of this harness defined relevance by flight-number thresholds instead, and the
baseline scored a perfect 1.000 on every scenario — because it was being graded by the same rule
that produced its ranking. A benchmark no candidate can lose is not measuring anything. The circular
version is called out in `EvalScenario` so nobody reintroduces it.

### Two baselines, both meaningful

| Baseline | What it tells you |
| --- | --- |
| `flight-numbers` | Whether semantic retrieval beats plain arithmetic on the target flight line |
| `local hashing (lexical)` | Whether a hosted model beats bag-of-words word overlap |

A model that cannot beat both is not earning its latency or its quota.

### Measured result

Run over 1,206 discs and 8 scenarios, all models served locally by Ollama:

| Model | P@5 | P@10 | MRR | lift@5 | Embed all |
| --- | --- | --- | --- | --- | --- |
| `granite-embedding:30m` | **0.775** | 0.775 | 0.828 | 9.0× | 17s |
| `snowflake-arctic-embed:33m` | 0.750 | 0.700 | **1.000** | 8.7× | 25s |
| flight-numbers (baseline) | 0.575 | 0.588 | 0.698 | 6.7× | — |
| local hashing (lexical) | 0.500 | 0.475 | 0.494 | 5.8× | 0.1s |
| `all-minilm:22m` *(the original default)* | 0.200 | 0.250 | 0.313 | 2.3× | 15s |

Three conclusions, in descending order of confidence:

1. **The original model was the worst option available** — 0.200 precision@5, below even bag-of-words
   hashing. Its 256-token limit truncated ~37% of the query, and the discarded tail was the
   discriminating part. Both 512-token models roughly quadruple it.
2. **Semantic retrieval genuinely beats the arithmetic it replaced** (0.775 / 0.750 against the
   baseline's 0.575). Worth stating plainly because the opposite result would have been an argument
   for deleting the embedding layer, and the harness was built to be capable of saying so.
3. **Granite versus arctic is not a real gap.** Eight scenarios at five results each is forty
   judgements; 0.775 against 0.750 is a single position. Arctic is the configured default because
   its MRR of 1.000 means the top-ranked disc was relevant in every scenario, and this API leads with
   one headline recommendation. Granite is the better bet if the alternatives list matters more.
   Distinguishing them properly needs more scenarios than eight.

The two baselines also win on *different* scenarios from each other, which is direct evidence for the
hybrid retrieval the production path already does (structured speed window, semantic rank within it)
rather than committing to either signal alone.

## Known limitations

- The stability shift constants — plastic `stability_shift`, `SHIFT_PER_GRAM`, wear `baseShift` and
  the durability damping curve — are informed judgement, not measurement. Plastics live in a
  migration and the other three are named constants, precisely so they can be revised without
  redesigning anything.
- Wear is a single ordinal axis. A disc worn evenly and a disc with one deep tree-strike dent fly
  differently, and the model cannot tell them apart.
- Weight and wear are per-request, not remembered, unless the player saves the bag as a profile —
  which, signed in, follows them across devices.
- The local hashing vectorizer is a lexical fallback, not a language model. It keeps the pipeline
  demonstrable without credentials; it is not a substitute for one.
