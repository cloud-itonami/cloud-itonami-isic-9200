(ns wagering.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [wagering.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Tanaka" (:patron (store/wager s "wager-1"))))
      (is (= "JPN" (:jurisdiction (store/wager s "wager-1"))))
      (is (= 100 (:stake-amount (store/wager s "wager-1"))))
      (is (= 2.5 (:odds (store/wager s "wager-1"))))
      (is (= 250.0 (:claimed-payout (store/wager s "wager-1"))))
      (is (false? (:patron-flagged? (store/wager s "wager-1"))))
      (is (true? (:patron-flagged? (store/wager s "wager-4"))))
      (is (false? (:wager-accepted? (store/wager s "wager-1"))))
      (is (false? (:payout-settled? (store/wager s "wager-1"))))
      (is (= ["wager-1" "wager-2" "wager-3" "wager-4"]
             (mapv :id (store/all-wagers s))))
      (is (nil? (store/patron-screening-of s "wager-1")))
      (is (nil? (store/assessment-of s "wager-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/acceptance-history s)))
      (is (= [] (store/settlement-history s)))
      (is (zero? (store/next-acceptance-sequence s "JPN")))
      (is (zero? (store/next-settlement-sequence s "JPN")))
      (is (false? (store/wager-already-accepted? s "wager-1")))
      (is (false? (store/wager-already-settled? s "wager-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :wager/upsert
                                 :value {:id "wager-1" :patron "Sakura Tanaka"}})
        (is (= "Sakura Tanaka" (:patron (store/wager s "wager-1"))))
        (is (= 100 (:stake-amount (store/wager s "wager-1"))) "stake-amount preserved"))
      (testing "assessment / patron-screening payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["wager-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "wager-1")))
        (store/commit-record! s {:effect :patron-screening/set :path ["wager-1"]
                                 :payload {:wager-id "wager-1" :verdict :clear}})
        (is (= {:wager-id "wager-1" :verdict :clear} (store/patron-screening-of s "wager-1"))))
      (testing "wager acceptance drafts an acceptance record and advances the acceptance sequence"
        (store/commit-record! s {:effect :wager/mark-accepted :path ["wager-1"]})
        (is (= "JPN-WGR-000000" (get (first (store/acceptance-history s)) "record_id")))
        (is (= "wager-acceptance-draft" (get (first (store/acceptance-history s)) "kind")))
        (is (true? (:wager-accepted? (store/wager s "wager-1"))))
        (is (= 1 (count (store/acceptance-history s))))
        (is (= 1 (store/next-acceptance-sequence s "JPN")))
        (is (true? (store/wager-already-accepted? s "wager-1")))
        (is (false? (store/wager-already-accepted? s "wager-2"))))
      (testing "payout settlement drafts a settlement record and advances the settlement sequence"
        (store/commit-record! s {:effect :wager/mark-settled :path ["wager-1"]})
        (is (= "JPN-PAY-000000" (get (first (store/settlement-history s)) "record_id")))
        (is (= "payout-settlement-draft" (get (first (store/settlement-history s)) "kind")))
        (is (true? (:payout-settled? (store/wager s "wager-1"))))
        (is (= 1 (count (store/settlement-history s))))
        (is (= 1 (store/next-settlement-sequence s "JPN")))
        (is (true? (store/wager-already-settled? s "wager-1")))
        (is (false? (store/wager-already-settled? s "wager-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/wager s "nope")))
    (is (= [] (store/all-wagers s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/acceptance-history s)))
    (is (= [] (store/settlement-history s)))
    (is (zero? (store/next-acceptance-sequence s "JPN")))
    (is (zero? (store/next-settlement-sequence s "JPN")))
    (store/with-wagers s {"x" {:id "x" :patron "p" :bet-type :moneyline
                               :stake-amount 100 :odds 2.0 :claimed-payout 200.0
                               :patron-flagged? false :wager-accepted? false :payout-settled? false
                               :jurisdiction "JPN" :status :intake}})
    (is (= "p" (:patron (store/wager s "x"))))))
