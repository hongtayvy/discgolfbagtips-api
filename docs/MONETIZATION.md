# Monetization notes

Not in the initial build. Recorded here so the decisions are not re-derived later.

## Subscription over display advertising

Disc golf bag advice is a niche audience. Display ad revenue scales with traffic volume, and a niche
tool without very high traffic earns effectively nothing from it while degrading the experience of
the people who do show up. A small subscription over a free tier is the better fit: the audience is
enthusiast, already spends money on plastic, and a premium tier can be genuinely useful rather than
merely ad-free.

Shape, if it happens:

- **Free** — bag analysis and the next-disc recommendation, as built today.
- **Premium** — tournament-specific bag prep (below), saved bags across devices, and history.

Premium requires real user accounts, which the current session-only design does not have. That is a
deliberate ordering: session state first, accounts only when something is worth paying for.

## Premium feature: tournament bag prep

Prepare a bag for a *specific* PDGA-sanctioned event — its actual course layouts and the forecast
for those dates — rather than for a generic "wooded, windy" category.

This is the feature that justifies a subscription, because it is the one that cannot be
approximated by picking categories from a dropdown.

### PDGA API

The [PDGA REST API](https://www.pdga.com/dev/api) is free for **non-commercial use by current PDGA
members**. Membership is $50/year amateur, $75/year professional.

Two things follow, and both need settling *before* any code is written:

1. **A paid subscription tier is plausibly commercial use.** The non-commercial grant likely does not
   cover charging for a feature built on their data. This needs an explicit conversation with the
   PDGA about commercial terms — not an assumption, and not something to discover after launch.
2. **The membership is a hard dependency.** If it lapses, the integration stops. Any premium feature
   built on it must degrade to something still worth the subscription price.

### Integration approach

Same shape as the DiscIt pipeline, for the same reasons:

- **Sync on a schedule into our own database**, never live per request. Respects their rate limits,
  survives their downtime, and keeps request latency ours to control.
- **Cache event, course and player data** with sensible TTLs — events and course layouts change
  rarely; registration and results change often.
- **Attribution wherever their data appears**: PDGA copyright notice, and links back to the relevant
  PDGA event, course, or player page. This is their stated expectation and it is also the right
  thing to do.

### Feature sketch

Given a PDGA event id: pull its course layouts, combine hole distances and characteristics with the
forecast for the event dates, and run the existing gap analysis against *that* rather than against a
category. Output is a per-round bag suggestion — which discs to bring, which to leave, what the wind
direction does to holes 4 and 12 — with the same explainability payload.

The RAG pipeline needs no structural change: this is a richer query passage and a course-derived set
of gaps feeding the same embedding, retrieval and generation layers.

## Open questions

- Is a subscription defensible against simply asking a good local player? The bet is that the
  tournament-prep feature is, and generic bag analysis is not — which is why generic stays free.
- Would manufacturers pay for placement? Probably, and it would destroy the only thing the product
  has going for it. The recommendation must stay honest or the explainability payload is a lie.
