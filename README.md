# cloud-itonami-isic-9200

Open Business Blueprint for **ISIC Rev.5 9200**: Gambling and betting
activities. This repository publishes a gambling/betting actor -- wager
intake, jurisdiction gaming-license assessment, patron compliance
screening, wager acceptance and payout settlement -- as an OSS
business that any qualified, licensed gaming operator can fork, deploy,
run, improve and sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530)) --
the first leisure/recreation-services vertical (ISIC division 92) in
this fleet. Here it is **WagerOps-LLM ⊣ Responsible Gambling
Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a wager
> summary, normalizing intake, and checking whether a claimed payout
> actually equals stake times odds -- but it has **no notion of which
> jurisdiction's gaming-licensing requirements are official, no
> license to accept a real wager or settle a real payout, and no way
> to know on its own whether a patron carries an undisclosed self-
> exclusion or failed age/ID-verification flag**. Letting it accept a
> wager or settle a payout directly invites fabricated jurisdiction
> citations, a payout that doesn't match the actual stake-odds
> calculation, and a self-excluded patron being quietly waved through
> -- and liability for whoever runs it. This project seals the
> WagerOps-LLM into a single node and wraps it with an independent
> **Responsible Gambling Governor**, a human **approval workflow**,
> and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers wager intake through jurisdiction gaming-license
assessment, patron compliance screening, wager acceptance and payout
settlement. It does **not**, by itself, hold a license to operate a
gaming venue in any jurisdiction, and it does not claim to. It also
does **not** model a full multi-leg/parlay/progressive-jackpot payout
engine -- no accumulator bets, no progressive-jackpot contribution
tracking, no in-play/live-odds adjustment (see `wagering.registry/
compute-payout`'s own docstring for the honest simplification this
makes: a single flat stake-times-odds payout, not a full wagering-
product catalog). Whoever deploys and operates a live instance (a
licensed gaming operator) supplies the jurisdiction-specific license,
the real responsible-gambling expertise and the real gaming-
management-system integrations, and bears that jurisdiction's
liability -- the software supplies the governed, spec-cited, audited
execution scaffold so that operator does not have to build the
compliance layer from scratch for every new market.

### Actuation

**Accepting a real wager and settling a real payout are never
autonomous, at any phase, by construction.** Two independent layers
enforce this (`wagering.governor`'s `:actuation/accept-wager`/
`:actuation/settle-payout` high-stakes gate and `wagering.phase`'s
phase table, which never puts `:wager/accept`/`:payout/settle` in any
phase's `:auto` set) -- see `wagering.phase`'s docstring and
`test/wagering/phase_test.kotoba`'s `wager-accept-never-auto-at-any-
phase`/`payout-settle-never-auto-at-any-phase`. The actor may draft,
check and recommend; a human gaming supervisor is always the one who
actually accepts a wager or settles a payout. Like `6512`/`6622`/
`6520`/`6530`/`6820`/`6920`/`6611`/`8530`, this actor has TWO actuation
events.

## The core contract

```
wager intake + jurisdiction facts (wagering.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ WagerOps-    │ ─────────────▶ │ Responsible Gambling        │  (independent system)
   │ LLM (sealed) │  + citations    │ Governor: spec-basis ·      │
   └──────────────┘                 │ evidence-incomplete ·        │
                             commit ◀────┼──────────▶ hold │ payout-mismatch
                                 │             │           │ (exact-match
                           record + ledger  escalate ─▶ human   independent recompute) ·
                                             (ALWAYS for         patron-flag-unresolved ·
                                              :wager/accept /     already-accepted/-settled
                                              :payout/settle)
```

**The WagerOps-LLM never accepts a wager or settles a payout the
Responsible Gambling Governor would reject, and never does so without
a human sign-off.** Hard violations (fabricated jurisdiction
requirements; unsupported gaming-license evidence; a claimed payout
that doesn't match stake times odds; an unresolved patron compliance
flag; a double acceptance or settlement) force **hold** and *cannot*
be approved past; a clean wager/payout proposal still always routes to
a human.

## Run

```bash
kbb -M:dev:run     # walk two clean lifecycles (wager acceptance, payout settlement) + four HARD-hold cases through the actor
kbb -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
kbb -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a cash-handling robot
manages physical currency/chip custody where used, under the actor,
gated by the independent **Responsible Gambling Governor**. The
governor never dispatches hardware itself; `:high`/`:safety-critical`
actions require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Responsible Gambling Governor, wager-acceptance + payout-settlement draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`9200`). Like `6920`/`7120`/`8620`/`8530`, this vertical's operational
records are practice-specific rather than a shared cross-operator data
contract, so `wagering.*` runs on the generic identity/forms/dmn/bpmn/
audit-ledger stack only -- no bespoke domain capability lib to
reference at all.

## Layout

| File | Role |
|---|---|
| `src/wagering/store.kotoba` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate wager-acceptance/payout-settlement history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded wager, and the double-acceptance/double-settlement guards check dedicated `:wager-accepted?`/`:payout-settled?` booleans rather than a `:status` value |
| `src/wagering/registry.kotoba` | Wager-acceptance + payout-settlement draft records, plus `compute-payout`/`payout-matches-claim?` -- an EXACT-MATCH independent recompute (claimed payout must equal stake x odds), reusing this fleet's established recompute family for a further domain |
| `src/wagering/facts.kotoba` | Per-jurisdiction gaming-licensing catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/wagering/wageropsllm.kotoba` | **WagerOps-LLM Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/assessment/patron-screening/wager-acceptance/payout-settlement proposals |
| `src/wagering/governor.kotoba` | **Responsible Gambling Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · payout-mismatch, pure ground-truth exact-match recompute · patron-flag-unresolved, unconditional evaluation) + already-accepted/already-settled guards + 1 soft (confidence/actuation gate) |
| `src/wagering/phase.kotoba` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (wager/payout actuation always human; wager intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/wagering/operation.kotoba` | **OperationActor** -- langgraph-clj StateGraph |
| `src/wagering/sim.kotoba` | demo driver |
| `test/wagering/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers wager intake through jurisdiction gaming-license
assessment, patron compliance screening, wager acceptance and payout
settlement -- the core governed lifecycle this blueprint's own
`docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Wager intake + per-jurisdiction gaming-licensing checklisting, HARD-gated on an official spec-basis citation (`:wager/intake`/`:jurisdiction/assess`) | A full multi-leg/parlay/progressive-jackpot payout engine (accumulator bets, progressive-jackpot contribution tracking, in-play/live-odds adjustment -- see `compute-payout`'s docstring) |
| Patron compliance screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:patron/screen`) | Real gaming-management-system integration, tax/regulatory reporting |
| Wager acceptance, HARD-gated on a double-acceptance guard (`:wager/accept`) | Ongoing responsible-gambling monitoring / play-pattern analysis itself |
| Payout settlement, HARD-gated on the claimed payout matching stake times odds and a double-settlement guard (`:payout/settle`) | |
| Immutable audit ledger for every intake/assessment/screening/acceptance/settlement decision | |

Extending coverage is additive: add the next gate (e.g. a wager-limit-
ceiling check) as its own governed op with its own HARD checks and
tests, following the SAME "an independent governor re-verifies against
the actor's own records before any real-world act" pattern this repo's
flagship op already establishes.

## Jurisdiction coverage (honest)

`wagering.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `wagering.facts/catalog` --
currently 5 seeded (JPN, USA, GBR, DEU, SGP) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `wagering.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to make
coverage look bigger.

## Maturity

`:implemented` -- `WagerOps-LLM` + `Responsible Gambling Governor` run
as real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the fifteen
prior actors' architecture. See `docs/adr/0001-architecture.md` for
the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
