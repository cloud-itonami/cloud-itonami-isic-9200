(ns wagering.store
  "SSoT for the gambling/betting actor, behind a `Store` protocol so
  the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/wagering/store_contract_test.clj), which is the whole point:
  the actor, the Responsible Gambling Governor and the audit ledger
  never know which SSoT they run on.

  Like `marketadmin.store`'s dual admission/halt-lift history, and
  `registrar.store`'s dual grade/degree history, this actor has TWO
  actuation events (wager acceptance, payout settlement) acting on the
  SAME entity (a wager), each with its OWN history collection,
  sequence counter and dedicated double-actuation-guard boolean
  (`:wager-accepted?`/`:payout-settled?`, never a `:status` value) --
  the same discipline `accounting.governor`'s/`marketadmin.governor`'s/
  `testlab.governor`'s/`clinic.governor`'s/`registrar.governor`'s
  guards establish.

  The ledger stays append-only on every backend: 'which wager was
  screened for a patron compliance flag, which wager was accepted,
  which payout was settled, on what jurisdictional basis, approved by
  whom' is always a query over an immutable log -- the audit trail a
  patron trusting an operator needs, and the evidence an operator
  needs if a wager or a payout is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [wagering.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (wager [s id])
  (all-wagers [s])
  (patron-screening-of [s wager-id] "committed patron compliance screening verdict for a wager, or nil")
  (assessment-of [s wager-id] "committed jurisdiction gaming-license assessment, or nil")
  (ledger [s])
  (acceptance-history [s] "the append-only wager-acceptance history (wagering.registry drafts)")
  (settlement-history [s] "the append-only payout-settlement history (wagering.registry drafts)")
  (next-acceptance-sequence [s jurisdiction] "next wager-acceptance-number sequence for a jurisdiction")
  (next-settlement-sequence [s jurisdiction] "next payout-settlement-number sequence for a jurisdiction")
  (wager-already-accepted? [s wager-id] "has this wager already been accepted?")
  (wager-already-settled? [s wager-id] "has this wager's payout already been settled?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-wagers [s wagers] "replace/seed the wager directory (map id->wager)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained wager set covering both actuation
  lifecycles (wager acceptance, payout settlement) so the actor + tests
  run offline."
  []
  {:wagers
   {"wager-1" {:id "wager-1" :patron "Sakura Tanaka" :bet-type :moneyline
                :stake-amount 100 :odds 2.5 :claimed-payout 250.0
                :patron-flagged? false :wager-accepted? false :payout-settled? false
                :jurisdiction "JPN" :status :intake}
    "wager-2" {:id "wager-2" :patron "Atlantis Doe" :bet-type :moneyline
                :stake-amount 100 :odds 2.0 :claimed-payout 200.0
                :patron-flagged? false :wager-accepted? false :payout-settled? false
                :jurisdiction "ATL" :status :intake}
    "wager-3" {:id "wager-3" :patron "鈴木一郎" :bet-type :moneyline
                :stake-amount 100 :odds 2.5 :claimed-payout 300.0
                :patron-flagged? false :wager-accepted? false :payout-settled? false
                :jurisdiction "JPN" :status :intake}
    "wager-4" {:id "wager-4" :patron "田中花子" :bet-type :moneyline
                :stake-amount 100 :odds 2.0 :claimed-payout 200.0
                :patron-flagged? true :wager-accepted? false :payout-settled? false
                :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- accept-wager!
  "Backend-agnostic `:wager/mark-accepted` -- looks up the wager via
  the protocol and drafts the wager-acceptance record, and returns
  {:result .. :wager-patch ..} for the caller to persist."
  [s wager-id]
  (let [w (wager s wager-id)
        seq-n (next-acceptance-sequence s (:jurisdiction w))
        result (registry/register-wager-acceptance wager-id (:jurisdiction w) seq-n)]
    {:result result
     :wager-patch {:wager-accepted? true
                   :acceptance-number (get result "acceptance_number")}}))

(defn- settle-payout!
  "Backend-agnostic `:wager/mark-settled` -- looks up the wager via the
  protocol and drafts the payout-settlement record, and returns
  {:result .. :wager-patch ..} for the caller to persist."
  [s wager-id]
  (let [w (wager s wager-id)
        seq-n (next-settlement-sequence s (:jurisdiction w))
        result (registry/register-payout-settlement wager-id (:jurisdiction w) seq-n)]
    {:result result
     :wager-patch {:payout-settled? true
                   :settlement-number (get result "settlement_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (wager [_ id] (get-in @a [:wagers id]))
  (all-wagers [_] (sort-by :id (vals (:wagers @a))))
  (patron-screening-of [_ id] (get-in @a [:patron-screenings id]))
  (assessment-of [_ wager-id] (get-in @a [:assessments wager-id]))
  (ledger [_] (:ledger @a))
  (acceptance-history [_] (:acceptances @a))
  (settlement-history [_] (:settlements @a))
  (next-acceptance-sequence [_ jurisdiction] (get-in @a [:acceptance-sequences jurisdiction] 0))
  (next-settlement-sequence [_ jurisdiction] (get-in @a [:settlement-sequences jurisdiction] 0))
  (wager-already-accepted? [_ wager-id] (boolean (get-in @a [:wagers wager-id :wager-accepted?])))
  (wager-already-settled? [_ wager-id] (boolean (get-in @a [:wagers wager-id :payout-settled?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :wager/upsert
      (swap! a update-in [:wagers (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :patron-screening/set
      (swap! a assoc-in [:patron-screenings (first path)] payload)

      :wager/mark-accepted
      (let [wager-id (first path)
            {:keys [result wager-patch]} (accept-wager! s wager-id)
            jurisdiction (:jurisdiction (wager s wager-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:acceptance-sequences jurisdiction] (fnil inc 0))
                       (update-in [:wagers wager-id] merge wager-patch)
                       (update :acceptances registry/append result))))
        result)

      :wager/mark-settled
      (let [wager-id (first path)
            {:keys [result wager-patch]} (settle-payout! s wager-id)
            jurisdiction (:jurisdiction (wager s wager-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:settlement-sequences jurisdiction] (fnil inc 0))
                       (update-in [:wagers wager-id] merge wager-patch)
                       (update :settlements registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-wagers [s wagers] (when (seq wagers) (swap! a assoc :wagers wagers)) s))

(defn seed-db
  "A MemStore seeded with the demo wager set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {} :patron-screenings {} :ledger [] :acceptance-sequences {}
                           :acceptances [] :settlement-sequences {} :settlements []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment/patron-screening payloads, ledger
  facts, acceptance/settlement records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:wager/id                     {:db/unique :db.unique/identity}
   :assessment/wager-id           {:db/unique :db.unique/identity}
   :patron-screening/wager-id      {:db/unique :db.unique/identity}
   :ledger/seq                      {:db/unique :db.unique/identity}
   :acceptance/seq                   {:db/unique :db.unique/identity}
   :settlement/seq                    {:db/unique :db.unique/identity}
   :acceptance-sequence/jurisdiction    {:db/unique :db.unique/identity}
   :settlement-sequence/jurisdiction     {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- wager->tx [{:keys [id patron bet-type stake-amount odds claimed-payout
                          patron-flagged? wager-accepted? payout-settled?
                          jurisdiction status acceptance-number settlement-number]}]
  (cond-> {:wager/id id}
    patron                        (assoc :wager/patron patron)
    bet-type                        (assoc :wager/bet-type bet-type)
    stake-amount                      (assoc :wager/stake-amount stake-amount)
    odds                                (assoc :wager/odds odds)
    claimed-payout                       (assoc :wager/claimed-payout claimed-payout)
    (some? patron-flagged?)                (assoc :wager/patron-flagged? patron-flagged?)
    (some? wager-accepted?)                  (assoc :wager/wager-accepted? wager-accepted?)
    (some? payout-settled?)                    (assoc :wager/payout-settled? payout-settled?)
    jurisdiction                                (assoc :wager/jurisdiction jurisdiction)
    status                                        (assoc :wager/status status)
    acceptance-number                              (assoc :wager/acceptance-number acceptance-number)
    settlement-number                               (assoc :wager/settlement-number settlement-number)))

(def ^:private wager-pull
  [:wager/id :wager/patron :wager/bet-type :wager/stake-amount :wager/odds :wager/claimed-payout
   :wager/patron-flagged? :wager/wager-accepted? :wager/payout-settled? :wager/jurisdiction
   :wager/status :wager/acceptance-number :wager/settlement-number])

(defn- pull->wager [m]
  (when (:wager/id m)
    {:id (:wager/id m) :patron (:wager/patron m) :bet-type (:wager/bet-type m)
     :stake-amount (:wager/stake-amount m) :odds (:wager/odds m) :claimed-payout (:wager/claimed-payout m)
     :patron-flagged? (boolean (:wager/patron-flagged? m))
     :wager-accepted? (boolean (:wager/wager-accepted? m))
     :payout-settled? (boolean (:wager/payout-settled? m))
     :jurisdiction (:wager/jurisdiction m) :status (:wager/status m)
     :acceptance-number (:wager/acceptance-number m) :settlement-number (:wager/settlement-number m)}))

(defrecord DatomicStore [conn]
  Store
  (wager [_ id]
    (pull->wager (d/pull (d/db conn) wager-pull [:wager/id id])))
  (all-wagers [_]
    (->> (d/q '[:find [?id ...] :where [?e :wager/id ?id]] (d/db conn))
         (map #(pull->wager (d/pull (d/db conn) wager-pull [:wager/id %])))
         (sort-by :id)))
  (patron-screening-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?wid
                :where [?k :patron-screening/wager-id ?wid] [?k :patron-screening/payload ?p]]
              (d/db conn) id)))
  (assessment-of [_ wager-id]
    (dec* (d/q '[:find ?p . :in $ ?wid
                :where [?a :assessment/wager-id ?wid] [?a :assessment/payload ?p]]
              (d/db conn) wager-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (acceptance-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :acceptance/seq ?s] [?e :acceptance/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (settlement-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :settlement/seq ?s] [?e :settlement/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-acceptance-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :acceptance-sequence/jurisdiction ?j] [?e :acceptance-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-settlement-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :settlement-sequence/jurisdiction ?j] [?e :settlement-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (wager-already-accepted? [s wager-id]
    (boolean (:wager-accepted? (wager s wager-id))))
  (wager-already-settled? [s wager-id]
    (boolean (:payout-settled? (wager s wager-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :wager/upsert
      (d/transact! conn [(wager->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/wager-id (first path) :assessment/payload (enc payload)}])

      :patron-screening/set
      (d/transact! conn [{:patron-screening/wager-id (first path) :patron-screening/payload (enc payload)}])

      :wager/mark-accepted
      (let [wager-id (first path)
            {:keys [result wager-patch]} (accept-wager! s wager-id)
            jurisdiction (:jurisdiction (wager s wager-id))
            next-n (inc (next-acceptance-sequence s jurisdiction))]
        (d/transact! conn
                     [(wager->tx (assoc wager-patch :id wager-id))
                      {:acceptance-sequence/jurisdiction jurisdiction :acceptance-sequence/next next-n}
                      {:acceptance/seq (count (acceptance-history s)) :acceptance/record (enc (get result "record"))}])
        result)

      :wager/mark-settled
      (let [wager-id (first path)
            {:keys [result wager-patch]} (settle-payout! s wager-id)
            jurisdiction (:jurisdiction (wager s wager-id))
            next-n (inc (next-settlement-sequence s jurisdiction))]
        (d/transact! conn
                     [(wager->tx (assoc wager-patch :id wager-id))
                      {:settlement-sequence/jurisdiction jurisdiction :settlement-sequence/next next-n}
                      {:settlement/seq (count (settlement-history s)) :settlement/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-wagers [s wagers]
    (when (seq wagers) (d/transact! conn (mapv wager->tx (vals wagers)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:wagers ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [wagers]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-wagers s wagers))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo wager set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
