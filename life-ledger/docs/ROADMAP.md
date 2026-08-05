# Roadmap — from SMS engine to Life OS

SMS is the first data source, not the product. The architecture was built so that each of
the following is an additive module rather than a refactor.

## The extension point

`TimelineEvent` carries a `SourceKind` and a `sourceId`. Every future source writes into
that one stream, and every timeline, search and insight surface consumes it without
knowing where an event came from. `SourceKind` already declares the reserved values —
`EMAIL`, `STATEMENT`, `CALENDAR`, `RECEIPT_OCR`, `HEALTH`, `LOCATION` — so adding a source
means adding a parser and a writer, not changing the schema of everything downstream.

## Planned sources

**Email parsing (offline).** Order confirmations, tickets, invoices and statements carry
far more detail than SMS. Ingest from a user-exported mailbox file (`.mbox`, IMAP export),
parsed by the same parser-registry pattern. No IMAP client, no credentials, no network.

**Bank statement import (PDF/CSV).** Statements are authoritative where SMS is lossy: they
close gaps, correct amounts and supply opening balances. On-device PDF text extraction, a
per-bank statement parser family, and reconciliation against existing transactions using
the same duplicate scorer.

**On-device receipt OCR.** Camera or gallery → ML Kit text recognition (which runs locally)
→ line items attached to the matching transaction. Turns "₹2,340 at DMart" into a shopping
list.

**Calendar correlation.** Reading the local calendar to explain the timeline: a spike in
travel spend sits next to the trip that caused it; a hospital payment sits next to the
appointment.

**Health, location, browser and screen-time imports.** All optional, all local, all from
user-exported files. Location history in particular stays opt-in and is only ever used to
annotate events that already exist.

**Voice notes and a document vault.** Attaching context to events — a photo of a warranty,
a note about why a payment was made — encrypted with the same key hierarchy.

## Planned intelligence

**On-device language model.** `LocalLanguageModel` is already the declared plug point.
The constraint it must respect is architectural: an interpreter emits a
`TransactionQuery`, never a figure. Weights are side-loaded by the user; there is no
download path, because adding one would require the `INTERNET` permission the whole
product is built on not having.

**Personal knowledge graph.** Merchants, accounts, people, places and events already exist
as entities. Linking them turns the timeline into something queryable in a way lists are
not: "everything connected to the Goa trip".

**Better forecasting.** Bill amount prediction, salary date prediction, and cash-flow
projection from detected recurrences — all deterministic, all explainable.

## Deliberate non-goals

- Cloud sync, multi-device, or web access.
- A hosted AI feature of any kind.
- Sharing, social or comparison features.
- Bank API / account-aggregator integration, which would mean network access and consent
  flows that hand data to a third party.
- Ads, subscriptions, or any business model requiring a server.
