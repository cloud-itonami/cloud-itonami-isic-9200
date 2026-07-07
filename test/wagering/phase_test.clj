(ns wagering.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:wager/accept`/`:payout/settle` must NEVER be a member
  of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [wagering.phase :as phase]))

(deftest wager-accept-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real wager acceptance"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :wager/accept))
          (str "phase " n " must not auto-commit :wager/accept")))))

(deftest payout-settle-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-settles a real payout"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :payout/settle))
          (str "phase " n " must not auto-commit :payout/settle")))))

(deftest patron-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling KYC/conflict/independence/surveillance/calibration/credential/integrity screen"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :patron/screen))
          (str "phase " n " must not auto-commit :patron/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":wager/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:wager/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :wager/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :wager/accept} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :payout/settle} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :wager/intake} :commit)))))
