# ADR-0001: cloud-itonami-isic-9200 -- WagerOps-LLM as a contained intelligence node

- Status: Accepted (2026-07-07)
- Related: `cloud-itonami-isic-6511`/`6512`/`6621`/`6622`/`6629`/`6520`/
  `6530`/`6820`/`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`
  ADR-0001s (the pattern this ADR ports); ADR-2607071250/ADR-2607071320/
  ADR-2607071351/ADR-2607071618/ADR-2607071640/ADR-2607071654/
  ADR-2607071717 (`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`, the
  seven verticals built outside ADR-2607032000's original insurance/
  real-estate batch -- this is the eighth)
- Context: Continuing the standing "pick a new ISIC blueprint vertical"
  direction past `8530`, this ADR deepens `cloud-itonami-isic-9200`
  (gambling and betting activities) from `:blueprint` to
  `:implemented`, the sixteenth actor in this fleet -- the FIRST
  leisure/recreation-services vertical (ISIC division 92), continuing
  the deliberate diversification beyond finance/insurance,
  professional/technical services, healthcare and education.

## Problem

A gaming operator's wager-settlement workflow bundles several distinct
concerns under one governed workflow:

1. **Jurisdiction gaming-licensing correctness** -- is the required
   evidence for accepting a wager or settling a payout based on an
   official gaming regulator (JCRC/NGCB/UKGC/GGL), or invented?
2. **Payout correctness** -- does a claimed payout actually equal
   stake times odds? Reuses this fleet's EXACT-MATCH independent-
   recompute family (`pension.registry`'s apportionment check/
   `reinsurance.registry`'s recovery check/`realty.registry`'s fee
   check/`brokerage.registry`'s order-value check) for a further
   domain -- a deliberate, straightforward reuse rather than a new
   shape, since the underlying real-world concept (payout = stake x
   odds) is genuinely the same arithmetic family.
3. **Patron compliance** -- does a wager carry an undisclosed patron
   compliance flag (self-exclusion or failed age/ID verification)?
   The gambling-specific reuse of the unconditional-evaluation
   screening discipline this fleet's `casualty.governor/sanctions-
   violations` originally established -- a SIXTH distinct grounding
   (after sanctions, market surveillance, instrument calibration,
   clinician credential, academic integrity).
4. **Real actuation, twice** -- accepting a real wager and settling a
   real payout are both irreversible acts involving real money.

An LLM has no authority or grounding for any of these. The design
problem is therefore not "run a gaming operation with an LLM" but
"seal the LLM inside a trust boundary and layer evidence-sufficiency,
payout-correctness verification, patron-compliance screening, audit
and human-approval on top of it, while structurally fixing both real
actuation events as human-only."

## Decision

### 1. WagerOps-LLM is sealed into the bottom node; it never accepts/settles directly

`wagering.wageropsllm` returns exactly five kinds of proposal: intake
normalization, jurisdiction gaming-license checklist, patron
compliance screening, wager-acceptance draft, and payout-settlement
draft. No proposal writes the SSoT or commits a real wager
acceptance/payout settlement directly.

### 2. OperationActor = langgraph-clj StateGraph, 1 run = 1 gambling/betting operation

`wagering.operation/build` is the SAME StateGraph shape as every
sibling actor's operation namespace, copied verbatim.

### 3. `payout-matches-claim?` reuses the EXACT-MATCH recompute family for a further domain

`payout-mismatch-violations` reuses this fleet's established EXACT-
MATCH independent-recompute shape (no proposal inspection or stored-
verdict lookup needed at all, since its inputs -- `:stake-amount`/
`:odds`/`:claimed-payout` -- are permanent facts already on the wager)
for the gambling domain -- a straightforward, deliberate reuse rather
than a new arithmetic shape, since payout = stake x odds is genuinely
the same "recompute and compare for equality" family every prior
instance establishes.

### 4. Patron compliance screening reuses the unconditional-evaluation discipline for a sixth distinct grounding

`patron-flag-unresolved-violations` reuses `casualty.governor/
sanctions-violations`'s fix (evaluated unconditionally, not scoped to
a specific op, so the screening op itself can HARD-hold on its own
finding) for BOTH `:wager/accept` and `:payout/settle` -- the SAME
shape `marketadmin.governor/surveillance-flag-unresolved-violations`/
`testlab.governor/calibration-not-current-violations`/`clinic.
governor/credential-not-current-violations`/`registrar.governor/
integrity-flag-unresolved-violations` establish for their own domains
(party-screening, market-surveillance, instrument-calibration,
clinician-credential, academic-integrity, and now patron self-
exclusion/verification -- the sixth distinct application of this
exact discipline).

### 5. Dual actuation events, on the SAME entity

`wagering.governor`'s `high-stakes` set has two members (`:actuation/
accept-wager` and `:actuation/settle-payout`), matching `6512`'s/
`6622`'s/`6520`'s/`6530`'s/`6820`'s/`6920`'s/`6611`'s/`8530`'s dual-
actuation shape -- this domain genuinely has two distinct real-world
gaming acts, both operating on the same wager entity (mirroring
`marketadmin.store`'s dual admission/halt-lift design and `registrar.
store`'s dual grade/degree design, rather than `accounting.store`'s
dual engagement-type split).

### 6. Double-acceptance/double-settlement guards check dedicated boolean facts, not `:status` -- deliberately sidestepping `6492`'s lifecycle trap

`already-accepted-violations`/`already-settled-violations` check
`:wager-accepted?`/`:payout-settled?`, dedicated booleans set once and
never cleared, rather than a `:status` value that could legitimately
advance past a checked state (the exact trap `cloud-itonami-isic-
6492`'s ADR-0001 documents in detail, explicitly avoided BY DESIGN in
`6920`'s, `6611`'s, `7120`'s, `8620`'s and `8530`'s equivalent guards).
This actor's `:status` never needs to encode "has this actuation
already happened" at all, so there is no analogous status-lifecycle
risk to fall into here -- a deliberate architectural choice informed
directly by the lesson from five prior builds, applied here for a
sixth consecutive time.

### 7. No fabricated international wager/payout-number standard

Same discipline as every sibling's registry: there is no single
international check-digit standard for a wager-acceptance or payout-
settlement reference number. `wagering.registry` therefore does not
invent one; it validates required fields and assigns a jurisdiction-
scoped sequence number only.

### 8. No bespoke capability lib

Like `6920`/`7120`/`8620`/`8530`, and unlike most other actors in this
fleet (each referencing its own `kotoba-lang/*` capability lib), this
vertical's operational records are practice-specific rather than a
shared cross-operator data contract -- `wagering.*` runs on the
generic identity/forms/dmn/bpmn/audit-ledger stack only, per the
blueprint's own explicit statement.

### 9. No bug this time

Like `7120`/`8620`/`8530` (and unlike `6492`'s status-lifecycle bug or
`6920`'s NullPointerException), this build's test suite, lint, and
demo-ledger verification all passed clean on the first run -- the
dedicated-boolean guard design (Decision 6) and the exact-match
recompute reuse (Decision 3) were both DELIBERATELY informed by prior
builds' lessons before writing any code. The demo (`clojure -M:dev:run`)
was still independently verified against the printed audit ledger --
basis tags `:no-spec-basis` · `:payout-mismatch` · `:patron-flag-
unresolved` · `:already-accepted` · `:already-settled` all appear
exactly where the sim script intends, and the acceptance/settlement
histories each contain exactly one drafted record after their
respective double-actuation attempts are held -- the same discipline
that caught every real bug in this fleet so far, applied here and
finding nothing to fix.

## Consequences

- (+) Gambling/betting gets the same governed, auditable-actor
  treatment as the fifteen prior actors, extending the pattern to a
  genuinely different domain (leisure/recreation services, ISIC
  division 92) for the first time.
- (+) The actuation invariant (governor + phase, two layers) is
  regression-tested by `test/wagering/phase_test.clj`'s `wager-accept-
  never-auto-at-any-phase`/`payout-settle-never-auto-at-any-phase`.
- (+) `MemStore` ‖ `DatomicStore` parity is proven by `test/wagering/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses.
- (+) `payout-matches-claim?`/`payout-mismatch-violations` extends this
  fleet's exact-match-recompute family to a fifth domain instance,
  regression-tested by `test/wagering/governor_contract_test.clj`'s
  `payout-mismatch-is-held`.
- (+) The dedicated-boolean double-actuation-guard lesson (from
  `6492`'s bug) has now been applied correctly BY DESIGN across a
  SIXTH consecutive build (`6920`, `6611`, `7120`, `8620`, `8530`,
  `9200`), each explicitly citing the prior lesson rather than re-
  deriving it by shape-analogy.
- (+) Both the demo and the full test suite passed clean on the first
  run -- no bug this time, unlike `6492`/`6920`.
- (-) This R0 seeds only 4 jurisdictions (JPN, USA, GBR, DEU) with an
  official spec-basis, out of ~194 worldwide; `wagering.facts/coverage`
  reports this honestly rather than claiming broader coverage.
- (-) `compute-payout` models only a single flat stake-times-odds
  payout, not a full multi-leg/parlay/progressive-jackpot payout
  engine (accumulator bets, progressive-jackpot contribution tracking,
  in-play/live-odds adjustment are out of scope -- see that fn's own
  docstring); real gaming-management-system integration and ongoing
  responsible-gambling monitoring are all out of scope for this OSS
  actor -- each operator's responsibility (see README's coverage
  table).
- 37 tests / 176 assertions, lint clean.

## Alternatives considered

| Option | Verdict | Reason |
|---|---|---|
| Add this as an addendum to ADR-2607071250/ADR-2607071320/ADR-2607071351/ADR-2607071618/ADR-2607071640/ADR-2607071654/ADR-2607071717 | ❌ | All seven of those ADRs' titles and scopes are explicitly `cloud-itonami-isic-6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`; mixing a different ISIC division (92, vs. those seven's 64/66/69/71/86/85) into any would blur scope boundaries |
| Keep `cloud-itonami-isic-9200` at `:blueprint` only | ❌ | The standing direction continues past `8530`; gambling/betting is a natural, well-precedented next domain, continuing the deliberate diversification into leisure/recreation services, a division this fleet had not yet touched |
| Invent a new arithmetic check shape for payout correctness rather than reusing the exact-match family | ❌ | Payout = stake x odds is genuinely the same "independently recompute and compare for equality" concept this fleet's apportionment/recovery/fee/order-value checks already establish -- inventing a superficially different shape for the same underlying concept would be artificial novelty, not a real contribution |
| Model a full multi-leg/parlay/progressive-jackpot payout engine for conformance-test rigor | ❌ | Genuinely more complex real-world wagering-product logic that this R0 does not claim to model correctly -- honestly scoped to a single flat stake-times-odds payout instead, same as every sibling's "starting catalog, not exhaustive" posture |
| Reference a capability lib (e.g. a hypothetical `kotoba-lang/gaming`) for consistency with most prior actors | ❌ | The blueprint itself explicitly states this vertical's records are practice-specific, not a shared cross-operator contract -- inventing a capability lib reference where the blueprint says none exists would misrepresent the domain, the same reasoning `6920`'s/`7120`'s/`8620`'s/`8530`'s ADRs already established |
