(ns wagering.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger seq 8): this repo previously had NO demo page and
  no generator at all. This namespace drives the REAL actor stack
  (`wagering.operation` -> `wagering.governor` -> `wagering.store`)
  through a scenario adapted from this repo's own `wagering.sim` demo
  driver (`clojure -M:dev:run`, confirmed by actually running it before
  this file was written -- unlike `cloud-itonami-isic-851`'s
  `schoolops.sim`, this repo's own sim driver uses ids that DO match
  `wagering.store/demo-data`'s seeded wagers exactly, and every
  disposition it produces (commit / escalate+approve / HARD hold, and
  the exact `:rule` on each hold) matches `wagering.governor`'s own
  documented checks precisely, so it was safe to reuse rather than
  author from scratch), trimmed to a representative subset (one clean
  phase-3 auto-commit, the full wager-acceptance/payout-settlement
  actuation lifecycle for one wager -- both of which ALWAYS escalate,
  never auto, at any phase -- and three distinct HARD-hold reasons that
  never reach a human) and rendered deterministically -- no invented
  numbers, no timestamps in the page content, byte-identical across
  reruns against the same seed (verified by diffing two consecutive
  runs before shipping).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [wagering.store :as store]
            [wagering.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- harness (unchanged across every repo
;; in this cluster -- do not rewrite, only copy) -----------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :gaming-supervisor :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach, using ONLY real wager ids from
  `wagering.store/demo-data`:

  wager-1 (JPN, clean, stake 100 x odds 2.5 = claimed-payout 250.0,
  patron NOT flagged) walks the full clean lifecycle: a `:wager/intake`
  directory-normalization patch is a phase-3, no-capital-risk
  auto-commit (governor clean, `:wager/intake` is the ONLY op in phase
  3's `:auto` set); `:jurisdiction/assess` (JPN has a real spec-basis in
  `wagering.facts`) and `:patron/screen` (clean) each ALWAYS escalate
  (neither op is ever auto-eligible, at any phase) and are approved by
  a human gaming supervisor; `:wager/accept` and `:payout/settle` --
  the two REAL-WORLD actuation events this actor performs (real money
  at risk / real money changing hands) -- ALSO ALWAYS escalate (the
  governor's own `high-stakes` gate AND the phase table agree,
  independently, that actuation is never auto, at any phase) and are
  each approved, producing one draft wager-acceptance record
  (`JPN-WGR-000000`) and one draft payout-settlement record
  (`JPN-PAY-000000`).

  Then three DISTINCT HARD-hold reasons, none of which ever reach a
  human (a human approver cannot override a HARD violation):
    - wager-2 (jurisdiction ATL, not in `wagering.facts/catalog`):
      `:jurisdiction/assess` HARD-holds on `:no-spec-basis` -- the
      advisor may not invent a jurisdiction's gaming-licensing
      requirements.
    - wager-3 (JPN, stake 100 x odds 2.5 = 250.0, but claimed-payout is
      300.0): assessed first (clean escalate+approve, so evidence is on
      file and this HARD hold below is isolated to the payout check
      alone), then `:payout/settle` HARD-holds on `:payout-mismatch` --
      the governor independently recomputes stake x odds and refuses to
      trust the claimed figure.
    - wager-4 (JPN, `:patron-flagged? true` in the seed data):
      `:patron/screen` HARD-holds on `:patron-flag-unresolved` -- an
      unresolved self-exclusion/verification flag blocks progress,
      un-overridably, even though the screening op itself is the one
      that (re)discovers it.

  Returns the resulting store -- every field `render` below reads is
  real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]

    ;; wager-1: clean directory-normalization patch -- phase-3 auto-commit,
    ;; no capital risk yet.
    (exec! actor "w1-intake" {:op :wager/intake :subject "wager-1"
                               :patch {:id "wager-1" :patron "Sakura Tanaka"}})

    ;; wager-1: jurisdiction gaming-license assessment (JPN has a real
    ;; spec-basis) -- ALWAYS escalates, approved by a human.
    (exec! actor "w1-assess" {:op :jurisdiction/assess :subject "wager-1"})
    (approve! actor "w1-assess")

    ;; wager-1: patron compliance screening, clean -- ALWAYS escalates,
    ;; approved by a human.
    (exec! actor "w1-screen" {:op :patron/screen :subject "wager-1"})
    (approve! actor "w1-screen")

    ;; wager-1: REAL wager acceptance (actuation/accept-wager, real money
    ;; at risk) -- ALWAYS escalates regardless of phase or confidence,
    ;; approved by a human gaming supervisor.
    (exec! actor "w1-accept" {:op :wager/accept :subject "wager-1"})
    (approve! actor "w1-accept")

    ;; wager-1: REAL payout settlement (actuation/settle-payout, real
    ;; money changes hands) -- ALWAYS escalates, approved by a human.
    (exec! actor "w1-settle" {:op :payout/settle :subject "wager-1"})
    (approve! actor "w1-settle")

    ;; wager-2 (ATL): no official spec-basis in wagering.facts -> HARD
    ;; hold on :no-spec-basis, never reaches a human.
    (exec! actor "w2-assess" {:op :jurisdiction/assess :subject "wager-2"})

    ;; wager-3: assess JPN first (clean escalate+approve) so evidence is
    ;; on file and the payout-mismatch hold below is isolated.
    (exec! actor "w3-assess" {:op :jurisdiction/assess :subject "wager-3"})
    (approve! actor "w3-assess")

    ;; wager-3: claimed-payout 300.0 != stake 100 x odds 2.5 = 250.0 ->
    ;; HARD hold on :payout-mismatch, never reaches a human.
    (exec! actor "w3-settle" {:op :payout/settle :subject "wager-3"})

    ;; wager-4: seeded with an unresolved patron compliance flag -> HARD
    ;; hold on :patron-flag-unresolved, never reaches a human.
    (exec! actor "w4-screen" {:op :patron/screen :subject "wager-4"})

    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= (:subject %) subject-id) ledger)))

(defn- status-cell [ledger subject-id]
  (let [f (last-fact-for ledger subject-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- wager-row [ledger {:keys [id patron bet-type stake-amount odds claimed-payout
                                  jurisdiction patron-flagged? wager-accepted? payout-settled?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s / %s</td><td>%s</td></tr>"
          (esc id) (esc patron) (esc (name (or bet-type :n-a))) (esc jurisdiction)
          (esc stake-amount) (esc odds) (esc claimed-payout)
          (if patron-flagged? "<span class=\"critical\">flagged</span>" "<span class=\"ok\">clear</span>")
          (if wager-accepted? "accepted" "not accepted") (if payout-settled? "settled" "not settled")
          (status-cell ledger id)))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map #(if (keyword? %) (name %) %)) (str/join ", "))
                    (some-> disposition name) ""))))

(defn- record-row [prefix {:strs [record_id wager_id jurisdiction kind immutable]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc prefix) (esc record_id) (esc wager_id) (esc jurisdiction)
          (if immutable "<span class=\"ok\">immutable draft</span>" (esc kind))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract
  ;; (`wagering.governor`/`wagering.phase`) -- documentation of fixed
  ;; behavior, not runtime telemetry, so it is legitimately hand-
  ;; described rather than derived from a live run.
  ["        <tr><td><code>:wager/intake</code></td><td><span class=\"ok\">phase-3 auto-commit when clean, no capital risk yet -- the ONLY auto-eligible op in this domain</span></td></tr>"
   "        <tr><td><code>:jurisdiction/assess</code></td><td><span class=\"warn\">ALWAYS human approval &middot; spec-basis independently checked against <code>wagering.facts</code>, never fabricated</span></td></tr>"
   "        <tr><td><code>:patron/screen</code></td><td><span class=\"warn\">ALWAYS human approval when clean &middot; an unresolved compliance flag is a HARD, un-overridable hold instead</span></td></tr>"
   "        <tr><td><code>:wager/accept</code></td><td><span class=\"warn\">ALWAYS human approval &middot; real money at risk (actuation/accept-wager) &middot; evidence-completeness + double-acceptance guard enforced, never auto at any phase</span></td></tr>"
   "        <tr><td><code>:payout/settle</code></td><td><span class=\"warn\">ALWAYS human approval &middot; real money changes hands (actuation/settle-payout) &middot; payout independently recomputed (stake &times; odds) + double-settlement guard enforced, never auto at any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        wagers (store/all-wagers db)
        wager-rows (str/join "\n" (map (partial wager-row ledger) wagers))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        acceptance-rows (str/join "\n" (map (partial record-row "acceptance") (store/acceptance-history db)))
        settlement-rows (str/join "\n" (map (partial record-row "settlement") (store/settlement-history db)))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-9200 &middot; gambling and betting activities</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Gambling and betting activities (ISIC 9200) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · wager acceptance/payout settlement always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Wagers</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>wagering.store</code> via <code>wagering.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Wager</th><th>Patron</th><th>Bet type</th><th>Jurisdiction</th><th>Stake</th><th>Odds</th><th>Claimed payout</th><th>Patron flag</th><th>Acceptance / Settlement</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     wager-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Draft wager-acceptance / payout-settlement records</h2>\n"
     "    <p class=\"muted\">Unsigned drafts only — the licensed operator's own act of signing is outside this actor's authority (see README <code>Actuation</code>).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Kind</th><th>Record id</th><th>Wager</th><th>Jurisdiction</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n"
     acceptance-rows (when (seq acceptance-rows) "\n")
     settlement-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Responsible Gambling Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden by a human approver. Jurisdiction spec-basis, gaming-license evidence completeness, payout arithmetic and patron compliance flags are independently recomputed, never trusted from the advisor's proposal; a real wager acceptance or payout settlement is always a human gaming supervisor's call, at every rollout phase.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/acceptance-history db)) "acceptance drafts,"
             (count (store/settlement-history db)) "settlement drafts )")))
