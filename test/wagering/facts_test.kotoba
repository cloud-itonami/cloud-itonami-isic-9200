(ns wagering.facts-test
  (:require [clojure.test :refer [deftest is]]
            [wagering.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest sgp-has-a-spec-basis
  (is (some? (facts/spec-basis "SGP")))
  (is (string? (:provenance (facts/spec-basis "SGP"))))
  (is (= 4 (count (facts/evidence-checklist "SGP")))))

(deftest mlt-has-a-spec-basis
  (is (some? (facts/spec-basis "MLT")))
  (is (string? (:provenance (facts/spec-basis "MLT"))))
  (is (= 4 (count (facts/evidence-checklist "MLT"))))
  (is (re-find #"Cap\. 583" (:legal-basis (facts/spec-basis "MLT")))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))
