(ns wagering.phase
  "Phase 0->3 staged rollout -- the gambling/betting analog of
  `cloud-itonami-isic-6512`'s `casualty.phase`.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- wager intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-assess  -- adds jurisdiction assessment +
                                 patron screening writes, still
                                 approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:wager/intake` (no capital risk yet)
                                 may auto-commit. `:wager/accept`/
                                 `:payout/settle` NEVER auto-commit, at
                                 any phase.

  `:wager/accept`/`:payout/settle` are deliberately ABSENT from every
  phase's `:auto` set, including phase 3 -- a permanent structural
  fact, not a rollout milestone still to come. Accepting a real wager
  and settling a real payout are the two real-world legal acts this
  actor performs; both are always a human operator's call. `wagering.
  governor`'s `:actuation/accept-wager`/`:actuation/settle-payout`
  high-stakes gate enforces the same invariant independently -- two
  layers, not one, agree on this. `:patron/screen` is likewise never
  auto-eligible, at any phase -- the same posture every sibling's KYC/
  conflict/independence/surveillance/calibration/credential/integrity
  screening op has. Like `credit.phase`/`accounting.phase`/
  `marketadmin.phase`/`testlab.phase`/`clinic.phase`/`registrar.phase`,
  phase 3's `:auto` set here has only ONE member (`:wager/intake`) --
  this domain has no separate no-capital-risk 'file' lifecycle distinct
  from the wager itself.")

(def read-ops  #{})
(def write-ops #{:wager/intake :jurisdiction/assess :patron/screen
                 :wager/accept :payout/settle})

;; NOTE the invariant: `:wager/accept`/`:payout/settle` are members of
;; `write-ops` (governor-gated like any write) but are NEVER members of
;; any phase's `:auto` set below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"       :writes #{}                                                              :auto #{}}
   1 {:label "assisted-intake" :writes #{:wager/intake}                                                  :auto #{}}
   2 {:label "assisted-assess" :writes #{:wager/intake :jurisdiction/assess :patron/screen}                :auto #{}}
   3 {:label "supervised-auto" :writes write-ops
      :auto #{:wager/intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:wager/accept`/`:payout/settle` are never auto-eligible at any
    phase, so they always escalate once the governor clears them (or
    hold if the governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Responsible Gambling Governor verdict to a base disposition
  before the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
