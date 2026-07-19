(ns kotoba-erp.mm-test
  "Port of mm_module/tests/test_mm.py + receipt-validation business-rule coverage."
  (:require [clojure.test :refer [deftest is]]
            [kotoba-erp.store :as store]
            [kotoba-erp.mm.entities :as e]
            [kotoba-erp.mm.repository :as repo]
            [kotoba-erp.mm.app :as app]))

(deftest test-save-receipt
  (let [s    (store/mem-store)
        mseg (e/mseg {:mblnr "GR-001" :zeile "1" :bwart "101" :matnr "MAT-01"
                      :menge 10.0 :ebeln "PO-1000" :ebelp "10"})
        mkpf (e/mkpf {:mblnr "GR-001" :budat "2026-06-07" :usnam "TEST_USER"
                      :items [mseg] :status "POSTED"})]
    (repo/save-material-document s mkpf :graph "test_mm")
    (let [quads @(:quads s)]
      (is (= 2 (count quads)))
      (is (= "erp:mm:mkpf_header" (:predicate (nth quads 0))))
      (is (= "GR-001" (get-in (nth quads 0) [:object :mblnr])))
      (is (= "TEST_USER" (get-in (nth quads 0) [:object :usnam]))))))

(deftest test-get-purchase-order
  (let [ekko (repo/get-purchase-order (repo/default-store) "PO-1000" :graph "test_mm")]
    (is (some? ekko))
    (is (= "PO-1000" (:ebeln ekko)))
    (is (= 1 (count (:items ekko))))
    (is (= "MAT-01" (:matnr (first (:items ekko)))))))

(deftest test-validate-receipt-rule
  (let [po (e/ekko {:ebeln "PO-1" :lifnr "V" :bedat "x"
                    :items [(e/ekpo {:ebeln "PO-1" :ebelp "10" :matnr "MAT-01" :menge 100.0 :netpr 1.0})]})
        ok (e/mkpf {:mblnr "GR" :budat "x" :usnam "U"
                    :items [(e/mseg {:mblnr "GR" :zeile "1" :bwart "101" :matnr "MAT-01" :menge 10.0 :ebeln "PO-1" :ebelp "10"})]})
        over (e/mkpf {:mblnr "GR" :budat "x" :usnam "U"
                      :items [(e/mseg {:mblnr "GR" :zeile "1" :bwart "101" :matnr "MAT-01" :menge 200.0 :ebeln "PO-1" :ebelp "10"})]})]
    (is (true? (e/validate-receipt ok po)))
    (is (false? (e/validate-receipt over po)))))

(deftest test-receive-goods-flow
  (let [result (app/invoke {:input-data {:mblnr "GR-001" :ebeln "PO-1000"
                                         :items [{:matnr "MAT-01" :menge 10.0 :ebelp "10"}]}})]
    (is (= "POSTED" (:status result)))
    (is (= "GR-001" (:material-doc-id (app/run {:mblnr "GR-001" :ebeln "PO-1000"
                                                :items [{:matnr "MAT-01" :menge 10.0 :ebelp "10"}]}))))))
