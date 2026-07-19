(ns kotoba-erp.fi.use-cases.process-event
  "FI Use Case — EventRouter (graph nodes).
  Port of fi_module/src/use_cases/process_event.py. Routes an incoming ctx
  payload to either a direct journal entry or a mapped MM goods-receipt entry.

  State keys: :ctx-payload :mapped-journal-data :route")

(defn parse-incoming-payload
  "Decide whether the payload is a direct journal command or an MM event."
  [{:keys [ctx-payload]}]
  (if (= (:event-type ctx-payload) "GoodsReceiptPosted")
    {:route "map_mm_receipt"}
    {:route "direct_journal" :mapped-journal-data ctx-payload}))

(defn map-mm-receipt
  "Map a GoodsReceiptPosted event into a balanced journal-entry payload."
  [{:keys [ctx-payload]}]
  (let [receipt-id  (get ctx-payload :receipt-id "UNKNOWN")
        po-number   (get ctx-payload :po-number "UNKNOWN")
        total-value (double (get ctx-payload :total-value 0.0))
        desc        (str "Goods Receipt " receipt-id " for PO " po-number)]
    {:mapped-journal-data
     {:entry-id (str "JE-" receipt-id)
      :lines [{:account-id "1300" :amount total-value :is-debit true  :description desc}
              {:account-id "2110" :amount total-value :is-debit false :description desc}]}}))

(defn route-event [{:keys [route]}] route)
