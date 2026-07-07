(ns wagering.governor-contract-test
  "The governor contract as executable tests -- the gambling/betting
  analog of `cloud-itonami-isic-6512`'s `casualty.governor-contract-
  test`. The single invariant under test:

    WagerOps-LLM never accepts a wager or settles a payout the
    Responsible Gambling Governor would reject, `:wager/accept`/
    `:payout/settle` NEVER auto-commit at any phase, `:wager/intake`
    (no direct capital risk) MAY auto-commit when clean, and every
    decision (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [wagering.store :as store]
            [wagering.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :gaming-supervisor :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  "Walks `subject` through assess -> approve, leaving an assessment on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :jurisdiction/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :wager/intake :subject "wager-1"
                   :patch {:id "wager-1" :patron "Sakura Tanaka"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Tanaka" (:patron (store/wager db "wager-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :jurisdiction/assess :subject "wager-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "wager-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :jurisdiction/assess :subject "wager-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "wager-1")) "no assessment written"))))

(deftest wager-accept-without-assessment-is-held
  (testing "wager/accept before any jurisdiction assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :wager/accept :subject "wager-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest payout-mismatch-is-held
  (testing "a claimed payout that doesn't equal stake x odds -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t5pre" "wager-3")
          res (exec-op actor "t5" {:op :payout/settle :subject "wager-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:payout-mismatch} (-> (store/ledger db) last :basis)))
      (is (empty? (store/settlement-history db))))))

(deftest patron-flag-is-held-and-unoverridable
  (testing "an unresolved patron compliance flag on a wager -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :patron/screen :subject "wager-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:patron-flag-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/patron-screening-of db "wager-4")) "no clearance written"))))

(deftest wager-accept-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, non-flagged wager still ALWAYS interrupts for human approval -- actuation/accept-wager is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t7pre" "wager-1")
          r1 (exec-op actor "t7" {:op :wager/accept :subject "wager-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, acceptance record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:wager-accepted? (store/wager db "wager-1"))))
          (is (= 1 (count (store/acceptance-history db))) "one draft acceptance record"))))))

(deftest payout-settle-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, matching-payout wager still ALWAYS interrupts for human approval -- actuation/settle-payout is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t8pre" "wager-1")
          r1 (exec-op actor "t8" {:op :payout/settle :subject "wager-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, settlement record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:payout-settled? (store/wager db "wager-1"))))
          (is (= 1 (count (store/settlement-history db))) "one draft settlement record"))))))

(deftest wager-accept-double-acceptance-is-held
  (testing "accepting the same wager twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t9pre" "wager-1")
          _ (exec-op actor "t9a" {:op :wager/accept :subject "wager-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :wager/accept :subject "wager-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-accepted} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/acceptance-history db))) "still only the one earlier acceptance"))))

(deftest payout-settle-double-settlement-is-held
  (testing "settling the same wager's payout twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t10pre" "wager-1")
          _ (exec-op actor "t10a" {:op :payout/settle :subject "wager-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :payout/settle :subject "wager-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-settled} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/settlement-history db))) "still only the one earlier settlement"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :wager/intake :subject "wager-1"
                          :patch {:id "wager-1" :patron "Sakura Tanaka"}} operator)
      (exec-op actor "b" {:op :jurisdiction/assess :subject "wager-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
