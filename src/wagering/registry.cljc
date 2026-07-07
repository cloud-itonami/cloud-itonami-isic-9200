(ns wagering.registry
  "Pure-function wager-acceptance + payout-settlement record
  construction -- an append-only gaming book-of-record draft.

  Like every sibling actor's registry, there is no single international
  check-digit standard for a wager-acceptance or payout-settlement
  reference number -- every operator/jurisdiction assigns its own
  reference format. This namespace does NOT invent one; it builds a
  jurisdiction-scoped sequence number and validates the record's
  required fields, the same honest, non-fabricating discipline
  `wagering.facts` uses.

  `payout-matches-claim?` is a REAL, foundational gaming-integrity
  concept (a settled payout must equal stake x odds, computed
  independently from the wager's own permanent fields) -- see its own
  docstring for the honest simplification it makes vs. a full multi-
  leg/parlay/progressive-jackpot payout engine. This reuses the EXACT-
  MATCH independent-recompute family `pension.registry`'s apportionment
  check/`reinsurance.registry`'s recovery check/`realty.registry`'s fee
  check/`brokerage.registry`'s order-value check establish, for a
  further domain (gaming payout calculation) -- the same discipline,
  not a new arithmetic shape.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real gaming-management system. It builds the RECORD an
  operator would keep, not the act of accepting a wager or settling a
  payout itself (that is `wagering.operation`'s `:wager/accept`/
  `:payout/settle`, always human-gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  licensed operator's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn compute-payout
  "The ground-truth payout owed for `wager`'s own `:stake-amount` and
  `:odds` -- see ns docstring for the honest simplification this makes
  vs. a full multi-leg/parlay/progressive-jackpot payout engine (this
  R0 models only a single flat stake x odds payout)."
  [{:keys [stake-amount odds]}]
  (* (double stake-amount) (double odds)))

(defn payout-matches-claim?
  "Does `wager`'s own `:claimed-payout` equal the independently
  recomputed `compute-payout`? A pure ground-truth check against the
  wager's own permanent fields -- reuses this fleet's EXACT-MATCH
  independent-recompute family for a further domain."
  [{:keys [claimed-payout] :as wager}]
  (== (double claimed-payout) (compute-payout wager)))

(defn register-wager-acceptance
  "Validate + construct the WAGER-ACCEPTANCE registration DRAFT -- the
  operator's own legal act of accepting a real wager. Pure function --
  does not touch any real gaming-management system; it builds the
  RECORD an operator would keep. `wagering.governor` independently re-
  verifies the patron's own compliance flag, and blocks a double-
  acceptance of the same wager, before this is ever allowed to commit."
  [wager-id jurisdiction sequence]
  (when-not (and wager-id (not= wager-id ""))
    (throw (ex-info "wager-acceptance: wager_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "wager-acceptance: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "wager-acceptance: sequence must be >= 0" {})))
  (let [acceptance-number (str (str/upper-case jurisdiction) "-WGR-" (zero-pad sequence 6))
        record {"record_id" acceptance-number
                "kind" "wager-acceptance-draft"
                "wager_id" wager-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "acceptance_number" acceptance-number
     "certificate" (unsigned-certificate "WagerAcceptance" acceptance-number acceptance-number)}))

(defn register-payout-settlement
  "Validate + construct the PAYOUT-SETTLEMENT registration DRAFT -- the
  operator's own legal act of settling a real payout. Pure function --
  does not touch any real gaming-management system; it builds the
  RECORD an operator would keep. `wagering.governor` independently re-
  verifies the wager's own claimed payout against `compute-payout`,
  and blocks a double-settlement of the same wager, before this is
  ever allowed to commit."
  [wager-id jurisdiction sequence]
  (when-not (and wager-id (not= wager-id ""))
    (throw (ex-info "payout-settlement: wager_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "payout-settlement: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "payout-settlement: sequence must be >= 0" {})))
  (let [settlement-number (str (str/upper-case jurisdiction) "-PAY-" (zero-pad sequence 6))
        record {"record_id" settlement-number
                "kind" "payout-settlement-draft"
                "wager_id" wager-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "settlement_number" settlement-number
     "certificate" (unsigned-certificate "PayoutSettlement" settlement-number settlement-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
