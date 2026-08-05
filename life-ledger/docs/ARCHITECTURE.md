# Architecture

## Why it is shaped this way

Life Ledger has one hard constraint — *no data leaves the device* — and one hard problem —
*turning an unbounded, adversarially inconsistent text format into reliable structured
history*. Almost every structural decision below follows from those two facts.

## Module graph

```
                              ┌──────────┐
                              │   :app   │  single Activity, NavHost, Hilt root
                              └────┬─────┘
        ┌──────────┬───────────┬───┴────┬────────────┬──────────┐
   feature:home  :transactions  :money  :analytics  :search  :settings
        └──────────┴───────────┴───┬────┴────────────┴──────────┘
                              ┌────┴─────┐
                              │ :domain  │  use cases · insights · statistics · NLQ
                              └────┬─────┘
                              ┌────┴─────┐
                              │  :data   │  repositories · mappers · pipeline · import/export
                              └────┬─────┘
                     ┌─────────────┼───────────────┐
                 ┌───┴───┐   ┌─────┴──────┐  ┌─────┴──────┐
                 │ :sms  │   │core:database│  │core:security│
                 └───┬───┘   └─────┬──────┘  └─────┬──────┘
                     └─────────────┼───────────────┘
      core:model · core:common · core:datastore · core:designsystem · core:ui · core:testing
```

Rules enforced by module boundaries rather than convention:

- **`:core:model` is pure Kotlin/JVM.** No Android, no Room, no Compose. The whole domain
  vocabulary — `Money`, `Transaction`, `TransactionQuery`, `Confidence` — is testable
  without an emulator, and no persistence concern can leak into it.
- **Features never see the database.** They depend on `:data`'s repository *interfaces*.
  A feature module cannot import a Room entity even by accident.
- **`:sms` does not depend on `:data`.** Parsers are pure functions; the pipeline that
  writes their output lives in `:data`. This is what lets the parser corpus test run
  thousands of messages in a plain JVM test with no database at all.
- **Nothing depends on `:app`.** It only wires.

## The SMS pipeline

```
 device inbox ──► SmsReader ──► SmsRepository (raw, verbatim, encrypted)
                                     │
                                     ▼
                          ParserRegistry (priority ordered)
                     bank parsers → wallet/UPI → card → life events → generic
                                     │
                              ParsedTransaction
                                     ▼
                          MerchantResolver  (AMZN* → Amazon)
                                     ▼
                        TransactionClassifier (category + reason)
                                     ▼
                          DuplicateDetector (hash, then scored)
                                     ▼
                             UserRule engine (user overrides win)
                                     ▼
                   TransactionRepository ──► TimelineEvent ──► detectors
                                                        (subscriptions, bills, investments)
```

Five properties this shape buys:

1. **Raw SMS is never discarded.** Every stage is replayable. Improving a parser and
   re-running history is a supported operation, not a migration.
2. **Parsers are pure.** `SmsParser.parse(sms, context)` performs no I/O and holds no
   state, so it is trivially unit-testable and safe to run on a background thread pool.
3. **Enrichment is separable.** Merchant resolution improving does not require re-parsing;
   re-classification does not require re-resolving.
4. **Every stage records confidence.** Nothing is silently dropped; low-confidence output
   is stored, surfaced in Parser Logs, and correctable.
5. **User corrections feed back in.** A correction writes a merchant alias and can generate
   a `UserRule`, so the same mistake is not made twice.

### Ingestion modes

| Mode | Trigger | Worker |
|---|---|---|
| Backfill | First run, or after granting permission | `SmsBackfillWorker`, chunked and resumable |
| Incremental | `SMS_RECEIVED` broadcast | `SmsIncrementalWorker`, coalesced |
| Reprocess | Parser version bump or user request | `SmsReprocessWorker` |

All run under WorkManager with battery constraints so a 100,000-message backfill on an old
phone degrades gracefully instead of being killed.

## Data model

Normalised Room schema, `Instant` stored as epoch millis, `LocalDate` as epoch day, enums as
their `name`, money as `Long` minor units plus a currency string. **Floating point is never
used for money anywhere in the app.**

Core tables: `sms`, `transactions` (+ `transactions_fts`), `merchants`, `merchant_aliases`,
`accounts`, `investments`, `investment_transactions`, `subscriptions`, `bills`, `tags`,
`transaction_tags`, `timeline_events`, `user_rules`, `parse_logs`, `insights`,
`parser_state`.

`transactions` carries two denormalised columns that exist purely for speed at 100k+ rows:
`searchBlob` (backs the FTS4 index) and `dedupeHash` (turns duplicate detection's first
pass into an indexed lookup instead of a scan).

### Migration policy

`exportSchema = true`, `schemas/` is committed, and `fallbackToDestructiveMigration` is
**never** used — this database is the user's only copy of years of history. Every version
bump ships an explicit `Migration` plus a `MigrationTestHelper` test that migrates a real
populated database.

## Performance at 100,000+ messages

| Concern | Approach |
|---|---|
| Backfill | Chunked reads from the SMS provider, batch inserts in one transaction per chunk, resumable via a stored cursor |
| Parsing | Pure functions on `Dispatchers.Default`, sender-code pre-filter rejects most parsers before any regex runs |
| Regex | Every pattern compiled once in an `object`, never inside a loop |
| Lists | Paged reads (`limit`/`offset`) — the UI never holds the full history |
| Search | FTS4 over a denormalised blob; structured filters compile to a single `@RawQuery` |
| Aggregates | Reduced in SQL, not in Kotlin — dashboards read pre-summed projections |
| Duplicate check | Indexed hash lookup first; the expensive scored comparison runs only on the narrow candidate set |
| Battery | WorkManager constraints; no polling, no foreground service, no wakelocks |

## Presentation

MVVM. Each screen has a ViewModel exposing a single immutable `UiState` as `StateFlow`,
collected with `collectAsStateWithLifecycle`. Composables are stateless and preview-able;
navigation is type-safe routes in `:app`. Adaptive layout by window size class: bottom bar
(compact) → navigation rail (medium) → permanent drawer plus two-pane detail (expanded), so
tablets and landscape are first-class rather than stretched phone layouts.

## Local AI

The `QueryInterpreter` interface takes a sentence and returns a `TransactionQuery` plus an
explanation. The shipped implementation is a rule engine; `LocalLanguageModel` is the
declared plug point for on-device weights.

The important architectural constraint: **an interpreter produces a query, never a number.**
Only `QueryAnswerer` — reading from repositories — produces figures. A future model can
therefore misunderstand a question, but it structurally cannot fabricate an amount.

## Testing

| Layer | What is tested | Tooling |
|---|---|---|
| `:core:model` | Money parsing/formatting, date ranges, confidence bands | Plain JVM JUnit |
| `:sms` | Every parser against a corpus of real-world message shapes, plus an accuracy-threshold suite | JUnit + Truth |
| `:sms` | Merchant resolution, categorisation, duplicate scoring | JUnit + Truth |
| `:core:database` | Converters, FTS escaping, migrations | Robolectric + `MigrationTestHelper` |
| `:data` | Repositories against an in-memory database | Robolectric + Turbine |
| `:domain` | Insight generators, statistics, NLQ interpretation | JUnit + fakes from `:core:testing` |
| `:feature:*` | Screen state and interaction | Compose UI test |
| Benchmarks | Backfill throughput, search latency, list scrolling | `androidx.benchmark` |
