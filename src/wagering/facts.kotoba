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
                              "Glücksspiellizenz (gaming-license certificate)"]}
   ;; Like the USA entry, a single representative regime rather than a
   ;; state-by-state (here: statute-by-statute) survey -- Singapore's
   ;; gambling-licensing framework is itself split across the Casino
   ;; Control Act 2006 (casino licensing + the National Council on
   ;; Problem Gambling's family-exclusion/self-exclusion regime, Part
   ;; 10) and the separate Gambling Control Act 2022 (non-casino
   ;; gambling services). This entry cites the Casino Control Act 2006
   ;; because it is the one Act that carries BOTH halves of this
   ;; catalog's narrow angle (gaming licensing, Part 3 ss.40-54; AND
   ;; the patron self-exclusion registry, Part 10 s.165A/s.168) in a
   ;; single statute -- verified 2026-07-23 directly against Singapore
   ;; Statutes Online (sso.agc.gov.sg) via Internet Archive Wayback
   ;; Machine snapshots (live sso.agc.gov.sg returned HTTP 403 to
   ;; automated fetch; per this fleet's hard safety rule, that block
   ;; was not bypassed -- the archived snapshots were used instead) and
   ;; against gra.gov.sg directly (reachable live). The regulator was
   ;; renamed from the Casino Regulatory Authority (CRA) to the
   ;; Gambling Regulatory Authority of Singapore (GRA) with effect from
   ;; 1 August 2022 under s.3 of the Gambling Regulatory Authority of
   ;; Singapore Act 2022 (No. 14 of 2022) -- confirmed verbatim from the
   ;; Casino Control Act 2006's own "Authority" definition (s.2(1), as
   ;; at the 17 Jul 2024 SSO revision): "'Authority' means the Gambling
   ;; Regulatory Authority of Singapore, which is the Casino Regulatory
   ;; Authority of Singapore continued and renamed as the Gambling
   ;; Regulatory Authority of Singapore under section 3 of the Gambling
   ;; Regulatory Authority of Singapore Act 2022".
   "SGP" {:name "Singapore"
          :owner-authority "Gambling Regulatory Authority of Singapore (GRA)"
          :legal-basis "Casino Control Act 2006"
          :national-spec "Casino Control Act 2006 Part 3 (Licensing of Casinos, ss.40-54) and Part 10 (National Council on Problem Gambling: family exclusion orders, self-exclusion under s.165A, list of excluded persons under s.168)"
          :provenance "https://www.gra.gov.sg/"
          :required-evidence ["Patron age/ID verification record"
                              "Self-exclusion-registry check record"
                              "Wager-acceptance/payout-calculation documentation"
                              "Gaming-license certificate"]}
   ;; Malta -- verified 2026-07-23 directly against the Malta Gaming
   ;; Authority's own site (mga.org.mt, reachable live, HTTP 200, no
   ;; bot-detection challenge encountered). The Authority's own
   ;; enforcement-register page states verbatim: "All Enforcement
   ;; Actions taken by the Malta Gaming Authority in accordance with
   ;; the Gaming Act (Cap. 583) and its relevant directives are listed
   ;; here-under" -- confirming the Act name/chapter number directly
   ;; from the regulator's own text. The MGA's own regulatory-framework
   ;; page (mga.org.mt/our-work/regulatory-framework/, also reachable
   ;; live) lists the Gaming Act's subsidiary legislation by name and
   ;; S.L. (Subsidiary Legislation) number, confirmed directly:
   ;; S.L. 583.05 "Gaming Authorisations Regulations" (licensing) and
   ;; S.L. 583.08 "Gaming Player Protection Regulations" (the player-
   ;; protection/self-exclusion regime). The MGA's own self-barring
   ;; page (mga.org.mt/player-hub/self-barring/) independently confirms
   ;; self-exclusion is a real, operative MGA scheme, own text (exact):
   ;; "A self-exclusion is a tool which prevents players from gambling
   ;; by blocking access to the gaming website which the player has
   ;; excluded with."
   ;; HONEST GAP: legislation.mt (Malta's official legislation portal,
   ;; where the Act's own full text is hosted) is a JavaScript-rendered
   ;; single-page application that returns the same client-side shell
   ;; to a direct HTTP fetch regardless of URL path or Accept header --
   ;; not a bot-detection challenge (no CAPTCHA, no challenge-page
   ;; markers, no blocking status code; every request returned a plain
   ;; HTTP 200), just a page that requires JS execution to render, which
   ;; this session did not attempt to work around via browser automation.
   ;; The page's own structured (JSON-LD) metadata was readable without
   ;; JS and gives "legislationDate"/"dateCreated": "2018-05-24" for the
   ;; Gaming Act -- cited below as the Authority's own metadata, not as
   ;; independently-read enactment-clause text. A Wayback Machine lookup
   ;; for this same URL returned an identical client-rendered shell (the
   ;; snapshot itself preserved the same non-server-rendered page), so
   ;; the fallback did not yield additional text either -- disclosed
   ;; honestly rather than treated as equivalent to having read the
   ;; Act's substantive articles.
   "MLT" {:name "Malta"
          :owner-authority "Malta Gaming Authority (MGA)"
          :legal-basis "Gaming Act (Cap. 583 of the Laws of Malta) -- MGA's own text: \"the Malta Gaming Authority in accordance with the Gaming Act (Cap. 583)\"; per legislation.mt's own structured metadata, dateCreated/legislationDate 2018-05-24 (Act's own full commencement clause not independently read this session, see namespace docstring)"
          :national-spec "S.L. 583.05 Gaming Authorisations Regulations (licensing) and S.L. 583.08 Gaming Player Protection Regulations (player protection / self-exclusion), both named directly on MGA's own regulatory-framework page"
          :provenance "https://www.mga.org.mt/our-work/regulatory-framework/ ; https://www.mga.org.mt/player-hub/self-barring/"
          :required-evidence ["Patron age/ID verification record"
                              "Self-exclusion-registry check record"
                              "Wager-acceptance/payout-calculation documentation"
                              "Gaming-license certificate"]}})

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
