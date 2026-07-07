(ns wagering.governor
  "Responsible Gambling Governor -- the independent compliance layer
  that earns the WagerOps-LLM the right to commit. The LLM has no
  notion of jurisdictional gaming-licensing law, whether a claimed
  payout actually equals the wager's own stake times odds, whether a
  patron carries an undisclosed self-exclusion/verification flag, or
  when an act stops being a draft and becomes a real-world wager
  acceptance or payout settlement, so this MUST be a separate system
  able to *reject* a proposal and fall back to HOLD -- the gambling/
  betting analog of `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated jurisdiction spec-basis, incomplete gaming-license
  evidence, a payout that doesn't equal stake times odds, an unresolved
  patron compliance flag, or a double acceptance/settlement). The
  confidence/actuation gate is SOFT: it asks a human to look (low
  confidence / actuation), and the human may approve -- but see
  `wagering.phase`: for `:stake :actuation/accept-wager`/`:actuation/
  settle-payout` (a real wager acceptance or payout settlement) NO
  phase ever allows auto-commit either. Two independent layers agree
  that actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source (`wagering.
                                       facts`), or invent one? Like
                                       `credit.governor`'s/`marketadmin.
                                       governor`'s/`testlab.governor`'s/
                                       `clinic.governor`'s/`registrar.
                                       governor`'s actuation ops,
                                       `:wager/accept`/`:payout/settle`
                                       act directly on a pre-seeded
                                       wager (see `wagering.store`'s
                                       own docstring) -- there is no
                                       'wager is missing' failure mode
                                       to guard against here.
    2. Evidence incomplete         -- for `:wager/accept`/`:payout/
                                       settle`, has the jurisdiction
                                       actually been assessed with a
                                       full gaming-license evidence
                                       checklist on file?
    3. Payout mismatch             -- for `:payout/settle`,
                                       INDEPENDENTLY recompute whether
                                       the wager's own `:claimed-
                                       payout` equals `stake-amount x
                                       odds` (`wagering.registry/
                                       payout-matches-claim?`) -- needs
                                       no proposal inspection or
                                       stored-verdict lookup at all,
                                       reusing this fleet's EXACT-MATCH
                                       independent-recompute family
                                       (`pension.registry`'s
                                       apportionment check/
                                       `reinsurance.registry`'s
                                       recovery check/`realty.
                                       registry`'s fee check/
                                       `brokerage.registry`'s order-
                                       value check) for a further
                                       domain.
    4. Patron flag unresolved      -- reported by THIS proposal itself
                                       (a `:patron/screen` that just
                                       found one), or already on file
                                       for the wager (`:patron/screen`/
                                       `:wager/accept`/`:payout/
                                       settle`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME discipline
                                       `casualty.governor/sanctions-
                                       violations`/`marketadmin.
                                       governor/surveillance-flag-
                                       unresolved-violations`/`testlab.
                                       governor/calibration-not-current-
                                       violations`/`clinic.governor/
                                       credential-not-current-
                                       violations`/`registrar.governor/
                                       integrity-flag-unresolved-
                                       violations` established -- an
                                       unresolved patron compliance
                                       flag (self-exclusion or failed
                                       age/ID verification) blocks BOTH
                                       real-world acts this actor
                                       performs, even if the screening
                                       op itself never (re)ran in this
                                       session.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:wager/accept`/
                                       `:payout/settle` (REAL gaming
                                       acts) -> escalate.

  Two more guards, double-acceptance/double-settlement prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-accepted-violations`/
  `already-settled-violations` refuse to accept/settle the SAME wager
  twice, off dedicated `:wager-accepted?`/`:payout-settled?` facts
  (never a `:status` value) -- the SAME 'check a dedicated boolean,
  not status' discipline `accounting.governor`'s/`marketadmin.
  governor`'s/`testlab.governor`'s/`clinic.governor`'s/`registrar.
  governor`'s guards establish, informed by `cloud-itonami-isic-6492`'s
  status-lifecycle bug (ADR-2607071320)."
  (:require [wagering.facts :as facts]
            [wagering.registry :as registry]
            [wagering.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Accepting a real wager and settling a real payout are the two real-
  world actuation events this actor performs -- a two-member set,
  matching `cloud-itonami-isic-6512`'s/`6622`'s/`6520`'s/`6530`'s/
  `6820`'s/`6920`'s/`6611`'s/`8530`'s dual-actuation shape."
  #{:actuation/accept-wager :actuation/settle-payout})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:wager/accept`/`:payout/settle`)
  proposal with no spec-basis citation is a HARD violation -- never
  invent a jurisdiction's gaming-licensing requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :wager/accept :payout/settle} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:wager/accept`/`:payout/settle`, the jurisdiction's required
  patron age/ID verification/self-exclusion-check/gaming-license
  evidence must actually be satisfied -- do not trust the advisor's
  self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:wager/accept :payout/settle} op)
    (let [w (store/wager st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction w) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(本人確認記録/利用制限者登録確認記録/施設運営免許証等)が充足していない状態での受付/精算提案"}]))))

(defn- payout-mismatch-violations
  "For `:payout/settle`, INDEPENDENTLY recompute whether the wager's own
  claimed payout equals stake-amount x odds via `wagering.registry/
  payout-matches-claim?` -- needs no proposal inspection or stored-
  verdict lookup at all, since its inputs are permanent ground-truth
  fields already on the wager. Reuses this fleet's EXACT-MATCH
  independent-recompute family for a further domain."
  [{:keys [op subject]} st]
  (when (= op :payout/settle)
    (let [w (store/wager st subject)]
      (when-not (registry/payout-matches-claim? w)
        [{:rule :payout-mismatch
          :detail (str subject " の申告払戻額(" (:claimed-payout w)
                      ")が独立再計算値(" (registry/compute-payout w) ")と一致しない")}]))))

(defn- patron-flag-unresolved-violations
  "An unresolved patron compliance flag -- reported by THIS proposal
  (e.g. a `:patron/screen` that itself just found one), or already on
  file in the store for the wager (`:patron/screen`/`:wager/accept`/
  `:payout/settle`) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY (not scoped to a specific op) so the screening op
  itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :open (get-in proposal [:value :verdict]))
        wager-id (when (contains? #{:patron/screen :wager/accept :payout/settle} op) subject)
        hit-on-file? (and wager-id (= :open (:verdict (store/patron-screening-of st wager-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :patron-flag-unresolved
        :detail "未解決の利用者コンプライアンスフラグのある賭金は進められない"}])))

(defn- already-accepted-violations
  "For `:wager/accept`, refuses to accept the SAME wager twice, off a
  dedicated `:wager-accepted?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :wager/accept)
    (when (store/wager-already-accepted? st subject)
      [{:rule :already-accepted
        :detail (str subject " は既に受付済み")}])))

(defn- already-settled-violations
  "For `:payout/settle`, refuses to settle the SAME wager's payout
  twice, off a dedicated `:payout-settled?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :payout/settle)
    (when (store/wager-already-settled? st subject)
      [{:rule :already-settled
        :detail (str subject " は既に精算済み")}])))

(defn check
  "Censors a WagerOps-LLM proposal against the governor rules. Returns
   {:ok? bool :violations [..] :confidence c :escalate? bool :high-stakes? bool
    :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (payout-mismatch-violations request st)
                           (patron-flag-unresolved-violations request proposal st)
                           (already-accepted-violations request st)
                           (already-settled-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
