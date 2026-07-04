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

## Trust Controls

- no payout is settled and no wager is accepted from an unverified or self-excluded patron without human sign-off
- a fabricated age/ID verification forces a hold, not an override
- every wager/payout path is auditable
- responsible-gambling self-exclusion lists are honored, never overridden by the advisor
- emergency manual override paths remain outside LLM control
