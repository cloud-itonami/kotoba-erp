(ns kotoba-erp.crm-test
  "Port of crm_module/tests/test_crm.py + entity business-rule coverage."
  (:require [clojure.test :refer [deftest is]]
            [kotoba-erp.crm.entities :as e]
            [kotoba-erp.crm.app :as app]))

(deftest test-close-opportunity-won
  (let [result (app/invoke {:input-data {:opportunity-id "006000000000001AAA"
                                         :stage-name "Closed Won"}})]
    (is (= "SUCCESS" (:status result)))
    (is (= "Closed Won" (:StageName (:opportunity result))))
    (is (= 100.0 (:Probability (:opportunity result))))))

(deftest test-validate-won-rule
  (is (true?  (e/validate-won (e/opportunity {:StageName "Closed Won" :Amount 1.0 :Probability 100.0}))))
  (is (false? (e/validate-won (e/opportunity {:StageName "Closed Won" :Amount 0.0 :Probability 100.0}))))
  (is (true?  (e/validate-won (e/opportunity {:StageName "Prospecting" :Amount 0.0 :Probability 0.0})))))
