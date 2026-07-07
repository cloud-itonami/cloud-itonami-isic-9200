(ns wagering.facts
  "Per-jurisdiction gambling/betting-licensing regulatory catalog -- the
  G2-style spec-basis table the Responsible Gambling Governor checks
  every jurisdiction/assess proposal against ('did the advisor cite an
  OFFICIAL public source for this jurisdiction's gaming-licensing
  requirements, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official gaming
  regulator (see `:provenance`); they are a STARTING catalog, not a
  from-scratch survey of all ~194 jurisdictions. Extending coverage is
  additive: add one map to `catalog`, cite a real source, done --
  never invent a jurisdiction's requirements to make coverage look
  bigger.

  Like `clinic.facts`'s USA entry, the USA entry here cites a single
  representative state regulator (the Nevada Gaming Control Board, the
  oldest and most internationally-referenced US gaming regulator)
  rather than all 50 individual state gaming commissions -- an honest
  single representative citation, not a state-by-state survey, the
  same simplification every prior catalog makes when a jurisdiction's
  real regulatory structure is itself federated.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  patron-age/ID-verification/self-exclusion-registry-check/wager-
  acceptance-and-payout-calculation-documentation/gaming-license
  evidence set submitted in some form; `:legal-basis` / `:owner-
  authority` / `:provenance` are the G2 citation the governor requires
  before any :jurisdiction/assess proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "カジノ管理委員会 (Japan Casino Regulatory Commission, JCRC)"
          :legal-basis "特定複合観光施設区域整備法 (Act on Development of Specified Complex Tourist Facility Areas)"
          :national-spec "IR事業者免許・カジノ行為区画運営等業務の規制基準"
          :provenance "https://www.casino-management-committee.go.jp/"
          :required-evidence ["利用者本人確認記録 (patron age/ID verification record)"
                              "利用制限者登録確認記録 (self-exclusion-registry check record)"
                              "賭金受入・払戻計算記録 (wager-acceptance/payout-calculation documentation)"
                              "施設運営免許証 (gaming-license certificate)"]}
   "USA" {:name "United States"
          :owner-authority "Nevada Gaming Control Board (NGCB)"
          :legal-basis "Nevada Gaming Control Act (NRS Chapter 463)"
          :national-spec "NGCB Regulations (licensing, internal controls, responsible gaming)"
          :provenance "https://gaming.nv.gov/"
          :required-evidence ["Patron age/ID verification record"
                              "Self-exclusion-registry check record"
                              "Wager-acceptance/payout-calculation documentation"
                              "Gaming-license certificate"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "UK Gambling Commission (UKGC)"
          :legal-basis "Gambling Act 2005"
          :national-spec "UKGC Licence Conditions and Codes of Practice (LCCP)"
          :provenance "https://www.gamblingcommission.gov.uk/"
          :required-evidence ["Patron age/ID verification record"
                              "Self-exclusion-registry check record"
                              "Wager-acceptance/payout-calculation documentation"
                              "Gaming-license certificate"]}
   "DEU" {:name "Germany"
          :owner-authority "Gemeinsame Glücksspielbehörde der Länder (GGL)"
          :legal-basis "Glücksspielstaatsvertrag 2021 (GlüStV)"
          :national-spec "GGL Erlaubnis- und Aufsichtsanforderungen"
          :provenance "https://www.gluecksspiel-behoerde.de/"
          :required-evidence ["Alters-/Identitätsprüfungsnachweis (patron age/ID verification record)"
                              "Spielersperrdatei-Prüfnachweis (self-exclusion-registry check record)"
                              "Einsatz-/Auszahlungsberechnungsdokumentation (wager-acceptance/payout-calculation documentation)"
                              "Glücksspiellizenz (gaming-license certificate)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to accept a wager
  or settle a payout on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-9200 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `wagering.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
