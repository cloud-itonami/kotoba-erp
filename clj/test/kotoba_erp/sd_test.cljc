(ns kotoba-erp.sd-test
  "Port of sd_module/tests/test_sd.py + test_repository.py."
  (:require [clojure.test :refer [deftest is]]
            [kotoba-erp.store :as store]
            [kotoba-erp.sd.entities :as e]
            [kotoba-erp.sd.repository :as repo]
            [kotoba-erp.sd.app :as app]))

(deftest test-save-billing-document
  (let [s    (store/mem-store)
        vbrp (e/vbrp {:vbeln "INV-001" :posnr "10" :aubel "SO-1000" :aupos "10"
                      :matnr "MAT-01" :fkimg 10.0 :netwr 100.0})
        vbrk (e/vbrk {:vbeln "INV-001" :fkart "F2" :kunnr "CUST-01" :fkdat "2026-06-07"
                      :netwr 100.0 :items [vbrp] :status "POSTED"})]
    (repo/save-billing-document s vbrk :graph "test_sd")
    (let [quads @(:quads s)]
      (is (= 2 (count quads)))
      (is (= "erp:sd:vbrk_header" (:predicate (nth quads 0))))
      (is (= "INV-001" (get-in (nth quads 0) [:object :vbeln])))
      (is (= "F2" (get-in (nth quads 0) [:object :fkart]))))))

(deftest test-get-sales-order
  (let [vbak (repo/get-sales-order (repo/default-store) "SO-1000" :graph "test_sd")]
    (is (some? vbak))
    (is (= "SO-1000" (:vbeln vbak)))
    (is (= 1 (count (:items vbak))))
    (is (= "MAT-01" (:matnr (first (:items vbak)))))))

(deftest test-generate-billing
  (let [result (app/invoke {:input-data {:billing-id "INV-001" :order-id "SO-1000"}})]
    (is (= "POSTED" (:status result)))
    (is (= "INV-001" (:vbeln (:vbrk result))))
    (is (= 1000.0 (:netwr (:vbrk result))))
    (is (= 1 (count (:items (:vbrk result)))))))
