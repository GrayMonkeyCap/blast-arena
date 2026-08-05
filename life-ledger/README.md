# Life Ledger

**A private, offline digital memory that reconstructs your financial and personal life from
the SMS already on your phone.**

Life Ledger is not a budgeting app and not an expense tracker. It is a *life tracker*. Every
salary credit, UPI payment, SIP instalment, flight booking, electricity bill and courier
delivery leaves a trail in your inbox. Life Ledger reads that trail — entirely on the
device — and turns it into a searchable timeline of what actually happened.

---

## The one non-negotiable

**Nothing leaves the phone. Ever.**

| | |
|---|---|
| Network | The app declares **no `INTERNET` permission**. Not "we don't call servers" — the OS will not let it. |
| Accounts | None. No sign-up, no login, no identity. |
| Analytics / telemetry / crash reporting | None. No Firebase, no ads SDK, no attribution SDK. |
| Cloud | None. No sync, no remote backup, no "optional" upload. |
| AI | Rule-based and local. The architecture accepts an on-device model later; there is no path to a hosted one. |
| Storage | SQLCipher-encrypted Room database, key held in the Android Keystore (hardware-backed where available). |
| Backups | Written by you, to a location you choose, encrypted with a passphrase only you know. |

The app requests exactly two permissions: `READ_SMS` and `RECEIVE_SMS`. It works fully in
airplane mode, forever.

---

## What it does

**Reads and understands your SMS.** A rule-based parsing engine with dedicated parsers for
HDFC, ICICI, SBI, Axis, Kotak, IDFC First, Yes, Canara, Indian Bank, Federal, AU Small
Finance, plus PhonePe, Google Pay, Paytm, Amazon Pay, credit/debit cards and wallets — and
a conservative generic fallback for everything else. Every message yields a structured
record: merchant, amount, direction, account, payment rail, category, reference, balance,
and the confidence behind each.

**Normalises merchants.** `AMZN*MKTP IN`, `AMAZON PAY INDIA` and `AMAZON SELLER SERVICES`
all become **Amazon**. `BBNOW`, `BB DAILY`, `BIGBASKET` become **BigBasket**. Payment
processors (Razorpay, BillDesk, PayU) are seen through, not recorded as the merchant. The
alias table grows every time you correct something.

**Detects duplicates.** One purchase often produces three messages — bank alert, card
network alert, merchant app alert. Life Ledger scores them on reference id, amount,
timing, merchant similarity and message similarity, and keeps one.

**Builds a life timeline.**

```
9:14 AM   Salary ₹85,000 received          HDFC ·  XX4521
1:12 PM   Paid Swiggy ₹420                 UPI
5:20 PM   Booked flight — BLR → DEL        IndiGo · PNR XXXXXX
7:18 PM   SIP ₹10,000                      Parag Parikh Flexi Cap
10:01 PM  Electricity bill ₹2,340 paid     BESCOM
```

**Tracks what recurs.** Subscriptions (with next billing, monthly and annual cost, and
cancel suggestions for the ones you stopped using), bills with estimated due dates and
local reminders, EMIs, SIPs and standing instructions.

**Answers questions.** *"What did I spend on coffee last month?"* · *"When was my last
insurance payment?"* · *"How much did I invest this year?"* — interpreted locally by a rule
engine over SQLite FTS, with the interpretation shown alongside the answer.

**Explains your patterns.** "You spent 24% more on food this month." "You have invested
every month for 18 months." "Your salary arrives on the last working day." All computed on
device, all with the underlying numbers one tap away.

---

## Architecture at a glance

Kotlin · Jetpack Compose · Material 3 · Room · Hilt · WorkManager · Coroutines + Flow ·
MVVM + repository pattern · Gradle version catalog · multi-module.

```
app  ──────────────────────────── navigation, DI wiring, single Activity

feature:home  transactions  money  analytics  search  settings

domain  ─────────────────────── use cases, insight engine, statistics, NLQ
data    ─────────────────────── repositories, mappers, import/export, pipeline
sms     ─────────────────────── parsers, merchant intelligence, dedupe, ingestion

core:model  common  database  datastore  security  designsystem  ui  testing
```

Dependencies point strictly downward. `:core:model` is a pure Kotlin module with no Android
dependency, so the entire domain vocabulary is unit-testable in milliseconds.

Full detail: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
Privacy design and threat model: [`docs/PRIVACY.md`](docs/PRIVACY.md).
Where this is going: [`docs/ROADMAP.md`](docs/ROADMAP.md).

---

## Building

Requires Android Studio (Ladybug or newer) and JDK 17.

```bash
./gradlew :app:assembleDebug        # build
./gradlew test                      # all unit tests, including the parser corpus
./gradlew :sms:test                 # parser accuracy suite only
./gradlew lint                      # Android lint across every module
```

There is no `google-services.json`, no API key and no `local.properties` secret to obtain —
by design.

---

## Adding a parser

The engine is built to be extended. A new bank is one file and one binding:

```kotlin
class MyBankParser @Inject constructor() : BaseBankParser() {
    override val info = ParserInfo(
        id = "mybank.v1",
        displayName = "My Bank",
        version = 1,
        senderCodes = setOf("MYBNK"),
        priority = 20,
    )
    override val bankCode = "MYBANK"

    override fun refine(draft: ParsedTransaction, sms: SmsRecord, context: ParserContext) =
        draft.copy(/* only the parts this bank writes differently */)
}
```

Bind it into the parser multibinding set, add a test class with a dozen real messages, and
it is live. Nothing else in the app changes.

---

## Status

Life Ledger is a complete, self-contained codebase built to be run and extended, not a
demo. The SMS engine is the first data source in a broader personal Life OS — email
exports, statement imports, on-device receipt OCR, calendar correlation and a unified
timeline are designed for but not yet implemented. See the roadmap.
