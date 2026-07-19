(ns kotoba-erp.sd.repository
  "SD Interface Adapter — Gateway/Repository.
  Port of sd_module/src/adapters/repository.py. Reads VBAK/VBAP, writes
  VBRK/VBRP, and publishes a BillingDocumentPosted event for the FI module."
  (:require [kotoba-erp.store :as store]
            [kotoba-erp.util :as u]
            [kotoba-erp.sd.entities :as e]))

(def default-graph "sd_sales")

(defn- default-fixtures
  "Reproduces the python `_KqeMock`: one open sales order header + one item."
  [_graph _subject predicate]
  (cond
    (= predicate "erp:sd:vbak_header")
    [{:vbeln "SO-1000" :kunnr "CUST-01" :audat (u/now-iso) :status "OPEN"}]
    (= predicate "erp:sd:vbap_item")
    [{:vbeln "SO-1000" :posnr "10" :matnr "MAT-01" :kwmeng 10.0 :netpr 100.0}]
    :else []))

(defn default-store [] (store/mem-store {:fixtures default-fixtures}))

(defn get-sales-order
  [store-m vbeln & {:keys [graph] :or {graph default-graph}}]
  (let [subject (str "vbak:" vbeln)
        headers (store/get-objects store-m graph subject "erp:sd:vbak_header")]
    (when (seq headers)
      (let [h     (first headers)
            items (mapv e/vbap (store/get-objects store-m graph subject "erp:sd:vbap_item"))]
        (e/vbak {:vbeln (:vbeln h) :kunnr (:kunnr h) :audat (:audat h)
                 :items items :status (:status h)})))))

(defn save-billing-document
  "Persist VBRK/VBRP quads and publish a BillingDocumentPosted event."
  [store-m {:keys [vbeln fkart kunnr fkdat netwr status items] :as _vbrk}
   & {:keys [graph] :or {graph default-graph}}]
  (let [subject (str "vbrk:" vbeln)]
    (store/assert-quad! store-m
      (store/quad graph subject "erp:sd:vbrk_header"
                  {:vbeln vbeln :fkart fkart :kunnr kunnr :fkdat fkdat
                   :netwr netwr :status status}))
    (doseq [item items]
      (store/assert-quad! store-m
        (store/quad graph subject "erp:sd:vbrp_item"
                    (select-keys item [:vbeln :posnr :aubel :aupos :matnr :fkimg :netwr]))))
    (store/publish! store-m "erp.sd.billing"
                    {:event-type "BillingDocumentPosted"
                     :vbeln vbeln
                     :aubel (if (seq items) (:aubel (first items)) "")
                     :kunnr kunnr :netwr netwr :timestamp fkdat})
    nil))
