#!/usr/bin/env bb
;; Test runner for the kotoba-erp clj port (repo rule: run_tests.clj, NOT .sh).
;; Usage:  bb run_tests.clj   (or)  bb test
(require '[clojure.test :as t])

(def test-namespaces
  '[kotoba-erp.graph-test
    kotoba-erp.fi-test
    kotoba-erp.crm-test
    kotoba-erp.mm-test
    kotoba-erp.sd-test])

(doseq [ns-sym test-namespaces]
  (require ns-sym))

(let [{:keys [fail error] :as summary} (apply t/run-tests test-namespaces)]
  (println "\nkotoba-erp clj port —" (pr-str (select-keys summary [:test :pass :fail :error])))
  (System/exit (if (zero? (+ (or fail 0) (or error 0))) 0 1)))
