(ns kotoba-erp.mm.repository
  "MM Interface Adapter — Gateway/Repository.
  Port of mm_module/src/adapters/repository.py. Reads EKKO/EKPO, writes
  MKPF/MSEG, and publishes a GoodsReceiptPosted event for the FI module."
  (:require [kotoba-erp.store :as store]
            [kotoba-erp.util :as u]
            [kotoba-erp.mm.entities :as e]))

(def default-graph "mm_inventory")

(def mock-price
  "Standard price stub (a real system fetches it from MARA/MBEW)."
  10.50)

(defn- default-fixtures
  "Reproduces the python `_KqeMock`: one open PO header + one line item."
  [_graph _subject predicate]
  (cond
    (= predicate "erp:mm:ekko_header")
    [{:ebeln "PO-1000" :lifnr "V-001" :bedat (u/now-iso) :status "OPEN"}]
    (= predicate "erp:mm:ekpo_item")
    [{:ebeln "PO-1000" :ebelp "10" :matnr "MAT-01" :menge 100.0 :netpr 10.50}]
    :else []))

(defn default-store [] (store/mem-store {:fixtures default-fixtures}))

(defn get-purchase-order
  [store-m ebeln & {:keys [graph] :or {graph default-graph}}]
  (let [subject (str "ekko:" ebeln)
        headers (store/get-objects store-m graph subject "erp:mm:ekko_header")]
    (when (seq headers)
      (let [h     (first headers)
            items (mapv e/ekpo (store/get-objects store-m graph subject "erp:mm:ekpo_item"))]
        (e/ekko {:ebeln (:ebeln h) :lifnr (:lifnr h) :bedat (:bedat h)
                 :items items :status (:status h)})))))

(defn save-material-document
  "Persist MKPF/MSEG quads and publish a GoodsReceiptPosted event."
  [store-m {:keys [mblnr budat usnam status items] :as _mkpf}
   & {:keys [graph] :or {graph default-graph}}]
  (let [subject (str "mkpf:" mblnr)]
    (store/assert-quad! store-m
      (store/quad graph subject "erp:mm:mkpf_header"
                  {:mblnr mblnr :budat budat :usnam usnam :status status}))
    (let [total (reduce
                  (fn [acc item]
                    (store/assert-quad! store-m
                      (store/quad graph subject "erp:mm:mseg_item"
                                  (select-keys item [:mblnr :zeile :bwart :matnr :menge :ebeln :ebelp])))
                    (+ acc (* (:menge item) mock-price)))
                  0.0
                  items)]
      (store/publish! store-m "erp.mm.mkpf"
                      {:event-type "GoodsReceiptPosted"
                       :mblnr mblnr
                       :ebeln (if (seq items) (:ebeln (first items)) "")
                       :total-value total
                       :timestamp budat}))
    nil))
