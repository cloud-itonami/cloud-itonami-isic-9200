(ns wagering.wageropsllm
  "WagerOps-LLM client -- the *contained intelligence node* for the
  gambling/betting actor.

  It normalizes wager intake, drafts a per-jurisdiction gaming-license
  evidence checklist, screens wagers for a patron compliance flag
  (self-exclusion or failed age/ID verification), drafts the wager-
  acceptance action, and drafts the payout-settlement action.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record or a real wager acceptance/payout settlement. Every
  output is censored downstream by `wagering.governor` before anything
  touches the SSoT, and `:wager/accept`/`:payout/settle` proposals
  NEVER auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/accept-wager | :actuation/settle-payout | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [wagering.facts :as facts]
            [wagering.registry :as registry]
            [wagering.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the patron, stake/odds or jurisdiction. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "賭金記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :wager/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-jurisdiction
  "Per-jurisdiction gaming-license evidence checklist draft. `:no-
  spec?` injects the failure mode we must defend against: proposing a
  checklist for a jurisdiction with NO official spec-basis in
  `wagering.facts` -- the Responsible Gambling Governor must reject
  this (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [w (store/wager db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction w))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "wagering.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-patron
  "Patron compliance screening draft. `:patron-flagged?` on the wager
  record injects the failure mode: the Responsible Gambling Governor
  must HOLD, un-overridably, on any open flag."
  [db {:keys [subject]}]
  (let [w (store/wager db subject)]
    (cond
      (nil? w)
      {:summary "対象wagerが見つかりません" :rationale "no wager record"
       :cites [] :effect :patron-screening/set :value {:wager-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (:patron-flagged? w)
      {:summary    (str (:patron w) ": 未解決のコンプライアンスフラグを検出")
       :rationale  "スクリーニングが未解決のフラグ(利用制限者登録/本人確認未了)を検出。人手確認とホールドが必須。"
       :cites      [:patron-check]
       :effect     :patron-screening/set
       :value      {:wager-id subject :verdict :open}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:patron w) ": コンプライアンスフラグなし")
       :rationale  "利用者スクリーニング非該当。"
       :cites      [:patron-check]
       :effect     :patron-screening/set
       :value      {:wager-id subject :verdict :clear}
       :stake      nil
       :confidence 0.9})))

(defn- propose-wager-acceptance
  "Draft the actual WAGER-ACCEPTANCE action -- accepting a real wager.
  ALWAYS `:stake :actuation/accept-wager` -- this is a REAL-WORLD act
  (real money is put at risk), never a draft the actor may auto-run.
  See README `Actuation`: no phase ever adds this op to a phase's
  `:auto` set (`wagering.phase`); the governor also always escalates
  on `:actuation/accept-wager`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [w (store/wager db subject)]
    {:summary    (str subject " 向け賭金受付提案"
                      (when w (str " (patron=" (:patron w) ")")))
     :rationale  (if w
                   (str "stake-amount=" (:stake-amount w) " odds=" (:odds w))
                   "wagerが見つかりません")
     :cites      (if w [subject] [])
     :effect     :wager/mark-accepted
     :value      {:wager-id subject}
     :stake      :actuation/accept-wager
     :confidence (if (and w (not (:patron-flagged? w))) 0.9 0.3)}))

(defn- propose-payout-settlement
  "Draft the actual PAYOUT-SETTLEMENT action -- settling a real payout.
  ALWAYS `:stake :actuation/settle-payout` -- this is a REAL-WORLD act
  (real money changes hands), never a draft the actor may auto-run.
  See README `Actuation`: no phase ever adds this op to a phase's
  `:auto` set (`wagering.phase`); the governor also always escalates
  on `:actuation/settle-payout`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [w (store/wager db subject)
        matches? (and w (registry/payout-matches-claim? w))]
    {:summary    (str subject " 向け払戻精算提案"
                      (when w (str " (patron=" (:patron w) ")")))
     :rationale  (if w
                   (str "claimed-payout=" (:claimed-payout w)
                        " independent-recompute=" (registry/compute-payout w))
                   "wagerが見つかりません")
     :cites      (if w [subject] [])
     :effect     :wager/mark-settled
     :value      {:wager-id subject}
     :stake      :actuation/settle-payout
     :confidence (if matches? 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :wager/intake            (normalize-intake db request)
    :jurisdiction/assess       (assess-jurisdiction db request)
    :patron/screen                (screen-patron db request)
    :wager/accept                    (propose-wager-acceptance db request)
    :payout/settle                      (propose-payout-settlement db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたはゲーミング事業者の賭金受付・払戻精算エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:wager/upsert|:assessment/set|:patron-screening/set|"
       ":wager/mark-accepted|:wager/mark-settled) "
       ":stake(:actuation/accept-wager か :actuation/settle-payout か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :jurisdiction/assess  {:wager (store/wager st subject)}
    :patron/screen        {:wager (store/wager st subject)}
    :wager/accept         {:wager (store/wager st subject)}
    :payout/settle        {:wager (store/wager st subject)}
    {:wager (store/wager st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Responsible Gambling
  Governor escalates/holds -- an LLM hiccup can never auto-accept a
  wager or auto-settle a payout."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :wageropsllm-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
