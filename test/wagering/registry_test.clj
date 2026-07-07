(ns wagering.registry-test
  (:require [clojure.test :refer [deftest is]]
            [wagering.registry :as r]))

;; ----------------------------- compute-payout / payout-matches-claim? -----------------------------

(deftest compute-payout-is-stake-times-odds
  (is (= 250.0 (r/compute-payout {:stake-amount 100 :odds 2.5})))
  (is (= 200.0 (r/compute-payout {:stake-amount 100 :odds 2.0}))))

(deftest payout-matches-claim-when-equal
  (is (r/payout-matches-claim? {:stake-amount 100 :odds 2.5 :claimed-payout 250.0}))
  (is (r/payout-matches-claim? {:stake-amount 100 :odds 2.5 :claimed-payout 250})))

(deftest payout-does-not-match-claim-when-different
  (is (not (r/payout-matches-claim? {:stake-amount 100 :odds 2.5 :claimed-payout 300.0})))
  (is (not (r/payout-matches-claim? {:stake-amount 100 :odds 2.5 :claimed-payout 249.99}))))

;; ----------------------------- register-wager-acceptance -----------------------------

(deftest wager-acceptance-is-a-draft-not-a-real-acceptance
  (let [result (r/register-wager-acceptance "wager-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest wager-acceptance-assigns-acceptance-number
  (let [result (r/register-wager-acceptance "wager-1" "JPN" 7)]
    (is (= (get result "acceptance_number") "JPN-WGR-000007"))
    (is (= (get-in result ["record" "wager_id"]) "wager-1"))
    (is (= (get-in result ["record" "kind"]) "wager-acceptance-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest wager-acceptance-validation-rules
  (is (thrown? Exception (r/register-wager-acceptance "" "JPN" 0)))
  (is (thrown? Exception (r/register-wager-acceptance "wager-1" "" 0)))
  (is (thrown? Exception (r/register-wager-acceptance "wager-1" "JPN" -1))))

(deftest acceptance-history-is-append-only
  (let [a1 (r/register-wager-acceptance "wager-1" "JPN" 0)
        hist (r/append [] a1)
        a2 (r/register-wager-acceptance "wager-2" "JPN" 1)
        hist2 (r/append hist a2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-WGR-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-WGR-000001" (get-in hist2 [1 "record_id"])))))

;; ----------------------------- register-payout-settlement -----------------------------

(deftest payout-settlement-is-a-draft-not-a-real-settlement
  (let [result (r/register-payout-settlement "wager-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest payout-settlement-assigns-settlement-number
  (let [result (r/register-payout-settlement "wager-1" "JPN" 7)]
    (is (= (get result "settlement_number") "JPN-PAY-000007"))
    (is (= (get-in result ["record" "wager_id"]) "wager-1"))
    (is (= (get-in result ["record" "kind"]) "payout-settlement-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest payout-settlement-validation-rules
  (is (thrown? Exception (r/register-payout-settlement "" "JPN" 0)))
  (is (thrown? Exception (r/register-payout-settlement "wager-1" "" 0)))
  (is (thrown? Exception (r/register-payout-settlement "wager-1" "JPN" -1))))

(deftest settlement-history-is-append-only
  (let [s1 (r/register-payout-settlement "wager-1" "JPN" 0)
        hist (r/append [] s1)
        s2 (r/register-payout-settlement "wager-2" "JPN" 1)
        hist2 (r/append hist s2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-PAY-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-PAY-000001" (get-in hist2 [1 "record_id"])))))
