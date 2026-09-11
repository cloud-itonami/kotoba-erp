(ns kotoba-erp.fi-test
  "Port of fi_module/tests/test_repository.py + test_integration.py."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba-erp.store :as store]
            [kotoba-erp.fi.entities :as e]
            [kotoba-erp.fi.repository :as repo]
            [kotoba-erp.fi.app :as app]))

(deftest test-save-accounting-document
  (let [s     (store/mem-store)
        item1 (e/bseg {:belnr "DOC-001" :buzei "1" :hkont "1000" :shkzg "S" :wrbtr 100.0 :sgtxt "Cash"})
        item2 (e/bseg {:belnr "DOC-001" :buzei "2" :hkont "2000" :shkzg "H" :wrbtr 100.0 :sgtxt "Revenue"})
        bkpf  (e/bkpf {:belnr "DOC-001" :bukrs "1000" :bldat "2026-06-07" :budat "2026-06-07"
                       :items [item1 item2] :bstat ""})]
    (repo/save-accounting-document s bkpf :graph "test_graph")
    (let [quads @(:quads s)]
      (is (= 3 (count quads)))
      (is (= "erp:fi:bkpf_header" (:predicate (nth quads 0))))
      (is (= "DOC-001" (get-in (nth quads 0) [:object :belnr])))
      (is (= "" (get-in (nth quads 0) [:object :bstat])))
      (is (= "erp:fi:bseg_item" (:predicate (nth quads 1))))
      (is (= "1000" (get-in (nth quads 1) [:object :hkont]))))))

(deftest test-get-accounting-document
  (let [s    (repo/default-store)
        bkpf (repo/get-accounting-document s "DIRECT-001" :graph "test_graph")]
    (is (some? bkpf))
    (is (= "DIRECT-001" (:belnr bkpf)))
    (is (= "1000" (:bukrs bkpf)))
    (is (= 0 (count (:items bkpf))))))

(deftest test-direct-journal
  (let [result (app/invoke {:ctx-payload {:entry-id "DIRECT-001"
                                          :lines [{:account-id "1000" :amount 100.0 :is-debit true  :description "Cash"}
                                                  {:account-id "2000" :amount 100.0 :is-debit false :description "Revenue"}]}})]
    (is (= "POSTED" (:status result)))
    (is (= "DIRECT-001" (:belnr (:bkpf result))))
    (is (= "direct_journal" (:route result)))))

(deftest test-mm-event-processing
  (testing "a GoodsReceiptPosted event maps to a balanced inventory/GR-IR journal"
    (let [result (app/invoke {:ctx-payload {:event-type "GoodsReceiptPosted"
                                            :receipt-id "GR-001" :po-number "PO-1000"
                                            :total-value 105.0 :timestamp "2026-06-07T00:00:00"}})
          items  (:items (:bkpf result))
          debit  (first (filter #(= "S" (:shkzg %)) items))
          credit (first (filter #(= "H" (:shkzg %)) items))]
      (is (= "POSTED" (:status result)))
      (is (= "map_mm_receipt" (:route result)))
      (is (= "JE-GR-001" (:belnr (:bkpf result))))
      (is (= 2 (count items)))
      (is (= "1300" (:hkont debit)))
      (is (= 105.0 (:wrbtr debit)))
      (is (= "2110" (:hkont credit)))
      (is (= 105.0 (:wrbtr credit))))))
