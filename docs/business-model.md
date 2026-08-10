# Business Model: Gambling and betting activities

## Classification

- Repository: `cloud-itonami-isic-9200`
- ISIC Rev.5: `9200`
- Activity: gambling and betting activities -- operating games of chance, betting and lottery services under license
- Social impact: cultural/recreational access, data sovereignty, transparent audit

## Customer

- independent licensed gambling operators
- cooperative lottery/betting pools
- community gaming-hall operators

## Offer

- patron intake and age/ID verification
- wager-acceptance proposal
- payout/settlement proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per venue
- support: monthly retainer with SLA
- migration: import from an incumbent gaming-management system
- per-transaction processing fee

| Package | Customer | Price shape |
|---|---|---|
| Self-host starter | operator's own compliance/IT lead | setup fee + optional support retainer |
| Managed Starter | one licensed venue / 公営競技 施行者 (single site, ~2,000 patron verifications per month) | ¥30,000/月 flat |

**Market-anchored (2026-08-10)**: benchmarked against 6 real competitor
families. **3 publish full numbers and 1 publishes an entry point**, all of
them identity/AML vendors rather than responsible-gambling workflow tools:
**Sumsub** at `$1.35 per verification` with a `$149 min. monthly commitment`
(Compliance tier `$1.85` / `$299`) on its own pricing page
(<https://sumsub.com/pricing/>); **iDenfy** "Starting at $135/month",
`$1.35 / verif` (<https://www.idenfy.com/pricing/>); **Didit** with
"500 free verifications every month, forever" and pay-as-you-go
`$0.33 per check` for its Full KYC bundle, `$0.20 per check` AML screening,
no monthly minimum (<https://didit.me/pricing>); and **ComplyAdvantage**
Starter "From $99 per month" for a banded number of monitored entities, with
per-band amounts withheld and Enterprise on contact-sales
(<https://complyadvantage.com/pricing/>). **The gambling-specific compliance
vendors disclose nothing** — **Alessa**'s casino AML product is
quote-only (<https://alessa.com/industry-software/casino-aml/>), and **no
Japanese eKYC or 公営競技 compliance vendor publishes a figure at all**
(TRUSTDOCK states only that the price is 初期費用 + 固定費用 + 従量課金 and
routes to an enquiry form, <https://biz.trustdock.io/pricing>). Converting at
~¥150/$, the disclosed *fixed* floor at one venue runs ¥14,850/月
(ComplyAdvantage `$99`) to ¥44,850/月 (Sumsub Compliance `$299` minimum).

**¥30,000/月 sits in the upper middle of that ¥14,850–¥44,850 band.** Above
ComplyAdvantage's entry point because that is a screening-data subscription
with no human-approval gate, no independent Governor and no immutable ledger;
below Sumsub's Compliance minimum because this actor does **not** supply the
verification capability itself — no document capture, no biometrics, no
sanctions database — it adjudicates whether a wager may be accepted or a
payout settled given those inputs, so it is an addition to a KYC vendor
rather than a replacement for one.

**The tier is flat, and deliberately carries no per-verification or per-wager
component — including none of the `per-transaction processing fee` shape
listed above.** Two reasons, and the second is the binding one. First, this
actor's cost does not scale with wager volume: the Governor's work is per
decision-class, not per stake. Second, and decisively, **billing a
responsible-gambling and audit layer by wager volume would tie the revenue of
the component that is supposed to stop play to the amount of play it fails to
stop.** That is a structural conflict, not a pricing preference, so the shape
is ruled out rather than merely disfavoured. Note also that the assumed size
is patron verifications per month, not handle — nothing in this tier's
pricing responds to how much is wagered.

**Subscribe (2026-08-10)**: a live Stripe Payment Link for the Managed
Starter tier (¥30,000/月 flat) is available now —
[**subscribe to Managed Starter**](https://buy.stripe.com/9B6bJ2dqTcJG3va5PQeEo07).
This is a no-code Stripe-hosted checkout; nothing in this repo's actor code
changed. Fulfilment is manual today — after subscribing, contact gftdcojp to
arrange managed-tenant setup. **No operator has claimed or subscribed to this
tier yet — this is a live, working checkout with zero paid tenants, not a
claim of existing revenue.**

## Trust Controls

- no payout is settled and no wager is accepted from an unverified or self-excluded patron without human sign-off
- a fabricated jurisdiction gaming-license citation, incomplete
  licensing evidence, a claimed payout that doesn't match the actual
  stake-times-odds calculation, or an unresolved patron compliance flag
  -- each forces a hold, not an override
- a wager cannot be accepted or its payout settled twice: a double-
  acceptance/double-settlement attempt is held off this actor's own
  wager facts alone, with no upstream comparison needed
- every intake, assessment, screening, acceptance and settlement path
  is auditable
- responsible-gambling self-exclusion lists are honored, never overridden by the advisor
- emergency manual override paths remain outside LLM control
