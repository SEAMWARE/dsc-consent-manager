# ADR 0017 — A contract may name a party by its self-description URI

[Documentation](../README.md) › [ADR index](README.md) › ADR 0017

- **Status:** Accepted.
- **Date:** 2026-11-03
- **Ticket:** none — raised by the FDSC consent integration (data-space-connector ticket-109).
- **Relates to:** `PartyAlias`, `ContractPartyResolver`, `PrivacyNoticeService`,
  `ContractDiscoveryService`, `ParticipantRepository.findBySelfDescriptionUri`, and the
  `self_description_uri` column of `V1__initial_schema.sql`.

## Context

A participant registers here under the identifier its token carries — a DID in every deployment so
far — and may declare a `selfDescriptionUri`, the URL its catalog entry is published at. Until now
only the identifier meant anything to contract handling: `dataProvider` and `dataConsumer` of a
fetched contract were compared to `participants.identifier`, and nothing else.

That is not the vocabulary the contracts are written in. A Contract Service projecting TM Forum
agreements (the FDSC consent-facade) names the parties by the catalog URL each published its
self-description at, because that is the identifier the catalog documents use; the contract-writing
component on the provider side writes the same URLs. The consent-filter plugin and the owner
resolver that gate the actual data access read the parties the same way, and ask the Contract
Service for the governing contract *by those URLs*.

So one agreement could satisfy this service or the enforcement chain, never both:

- with the parties written as DIDs, privacy-notice generation worked and the contract lookup the
  gate makes (`GET /verify/{providerSD}/{consumerSD}`) found no contract, so every read was denied;
- with the parties written as catalog URLs, the gate resolved and generation answered
  `404 No active participant is registered under 'https://…/participants/…'` — or `403`, when the
  caller's own DID did not equal the URL the contract called it by.

Demanding DIDs in the contract would have been a decision for every other component in the chain,
taken by the component with the least claim to make it: a contract is written by the catalog and
read by several services, and this one is not its author.

## Decision

A contract may name a participant by **either** of the two names its registration carries: the
identifier it is registered and authenticated under, or the self-description URI it published. Both
name one row, and which name a given contract uses is not this service's business.

- `PartyAlias` is the pair of names one registered participant goes by. `names(stated)` is the one
  definition of "this contract means that participant"; a blank or absent name never matches, so a
  participant that published no self-description is named by its identifier alone.
- `ContractPartyResolver` judges parties against a `PartyAlias` rather than a bare identifier, on
  both shapes — bilateral `dataProvider`/`dataConsumer`, ecosystem membership, recorded roles and
  service offerings. It stays pure: it is handed the names and never consults the directory.
  `resolve` still returns the values the contract states verbatim.
- `PrivacyNoticeService` resolves a stated party to a participant by identifier first, then by
  self-description URI, and checks the caller against the stated pair under both of its own names
  before any refusal quotes a party — the reading order that keeps a stranger from learning an
  identifier off a contract stays exactly as it was.
- Notices, consents and receipts keep recording the **identifier**. The URI is a second way in, not
  a second way of being recorded, so nothing downstream changes.

### A URI two participants claim names neither

`self_description_uri` carries no unique constraint and is declared by the participant itself at
registration. Two registrations can therefore claim one URL. Resolution returns the single active
row claiming it and **nothing** when two do, which surfaces as the ordinary
`404 No active participant is registered under '…'`.

Picking either row would let one participant decide whose contract a notice is generated from by
declaring a URL it does not own — self-asserted data choosing the counterparty of someone else's
agreement. Failing closed costs a deployment that duplicated a URL a 404 that names the URL, which
is the right place to notice it. Making the column unique was rejected for now: it would turn an
existing duplicate into a failing migration and a registration conflict, for a case no deployment
has yet hit.

### Where the pair is checked for being one participant

Two names of one participant read as two distinct parties as long as the comparison is on strings,
so the distinctness check is repeated on the resolved rows, where a participant stated once by
identifier and once by URI is caught.

## Consequences

- A contract naming its parties by catalog URL generates the same notice as one naming them by DID,
  for the same two participants, recorded under their identifiers. The FDSC chain — the facade's
  contract lookup and this service's generation — reads one agreement.
- A participant that published no self-description is unaffected: its identifier is its only name.
- Resolution costs one extra query, and only when the stated party is not an identifier.
- The directory remains the only place that maps a name to a row; the party resolver stays pure and
  testable without one.
