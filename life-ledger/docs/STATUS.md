# Build status

Honest inventory of what is implemented and what is not, as of the current commit.
**The project does not assemble yet** — the gaps below are load-bearing, not polish.

## Complete

| Module | State |
|---|---|
| `:core:model` | Done. Money in minor units with Indian-format parsing, the full transaction/life-event vocabulary, confidence bands, date ranges incl. Indian FY, statistics types, `TransactionQuery`. Pure Kotlin, no Android. |
| `:core:common` | Done. Dispatcher qualifiers, `Outcome`, `TimeProvider`, Jaro–Winkler / token-set similarity, lakh-crore money and relative-date formatting, redacting logger. |
| `:core:database` | Entities, DAOs, converters, aggregate projections, FTS4 content-backed index, `LifeLedgerDatabase`, migration policy, SQLCipher DI with passphrase zeroing, per-DAO bindings. |
| `:core:datastore` | Done. Full preference model + DataStore repository. |
| `:core:security` | Done. Keystore-wrapped random DB passphrase, streaming AES-256-GCM backup crypto with versioned header + PBKDF2, idle-timeout app lock, biometric wrapper. |
| `:core:designsystem` | Done. Palette, typography, spacing/elevation tokens, dynamic colour with hand-built fallback, `Ll*` component set, previews. |
| `:core:ui` | Charts done (line, bar, donut, sparkline, heatmap calendar, stacked area, progress ring, waterfall) with geometry extracted into a tested `ChartMath`. |
| `:core:testing` | Done. Fakes, builders, a 67-message SMS corpus, money assertions. |
| `:sms` | Engine done: parser contract, regex toolkit, lexicon, `BaseBankParser`, 11 bank parsers, 6 payment-app/card/wallet parsers, 4 life-event parsers, merchant catalogue + resolver, category rules + classifier, duplicate detector, registry, `SmsParsingEngine`, reader + broadcast receiver, DI multibindings. |
| `:domain` | Insight engine + 7 generators, rule-based NLQ interpreter, ask service, statistical recurrence detector, DI. |

## Incomplete

| Area | What is missing |
|---|---|
| `:data` repositories | `Transaction` and `Sms` repositories are implemented, along with the dynamic `TransactionQueryBuilder` and mappers. The other twelve (`Merchant`, `Account`, `Investment`, `Subscription`, `Bill`, `Tag`, `Timeline`, `Insight`, `Rule`, `ParserLog`, `Statistics`, `Search`) have interfaces but no implementations. |
| `:data` pipeline | `SmsIngestionPipeline`, `RuleEngine`, the subscription/bill/investment sync, all WorkManager workers, and `WorkManagerSmsIngestScheduler` are unwritten. Without the scheduler the SMS receiver has nothing to bind to. |
| `:data` DI | `RepositoryModule` / `PipelineModule` not written. |
| Import / export | Contracts, CSV importer and the CSV/JSON exporters landed. XLSX, SMS-Backup XML, encrypted backup and the PDF summary are unwritten. |
| `:feature:*` | Six modules exist with Gradle setup and manifests, and **no screens**. Every screen in the spec still needs building. |
| `:app` | `Application`, `MainActivity`, `MainViewModel`, manifest, resources and launcher icon are done. The `LifeLedgerApp` composable, navigation graph, lock screen and onboarding/permission flow are not. |
| Tests | 22 test files (parsers, charts, crypto, preferences). Repository, migration, pipeline, UI and benchmark suites are unwritten. |
| Schema export | `schemas/` is not yet generated — it is produced by the first successful build. |

## Why it stopped here

The build was parallelised across worker agents; the account's session quota was exhausted
partway through and every worker was terminated mid-task. Everything above was written
before that point or afterwards by hand. Nothing here is blocked on a design question — the
contracts the remaining work programs against are all in place:

- repository interfaces: `data/…/repository/Repositories.kt`
- DAO surface: `core/database/…/dao/`
- engine output: `sms/…/engine/SmsParsingEngine.kt` (`EngineResult`)
- scheduler seam: `sms/…/ingest/SmsIngestScheduler.kt`

## Suggested order to finish

1. `:data` repository implementations (mechanical — DAOs and mappers both exist).
2. `SmsIngestionPipeline` + `WorkManagerSmsIngestScheduler` + workers. At this point the
   app ingests and stores real messages end to end.
3. `:app` navigation shell, lock screen and permission onboarding.
4. Feature screens, starting with Dashboard and Timeline.
5. Remaining importers/exporters, then the test suites.
