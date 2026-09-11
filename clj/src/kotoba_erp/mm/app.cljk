(ns kotoba-erp.mm.app
  "MM module application wiring. Port of mm_module/app.py.
  The python entrypoint delegates to `handle_invoke`; here `run` plays that role."
  (:require [kotoba-erp.graph :as g]
            [kotoba-erp.mm.use-cases.receive-goods :as uc]))

(def compiled
  (-> (g/state-graph)
      (g/add-node "parse" uc/parse-receipt)
      (g/add-node "fetch_po" uc/fetch-po)
      (g/add-node "validate" uc/validate-receipt)
      (g/add-node "post" uc/post-receipt)
      (g/add-node "reject" uc/reject-receipt)
      (g/add-edge g/START "parse")
      (g/add-edge "parse" "fetch_po")
      (g/add-conditional-edges "fetch_po" uc/check-po-exists
                               {"reject" "reject" "validate" "validate"})
      (g/add-conditional-edges "validate" uc/check-validation
                               {"reject" "reject" "post" "post"})
      (g/add-edge "post" g/END)
      (g/add-edge "reject" g/END)
      (g/compile-graph)))

(defn invoke [initial-state]
  (g/invoke compiled (merge {:errors []} initial-state)))

(defn run [payload]
  (let [result (invoke {:input-data (or payload {})})
        out    {:status (get result :status "UNKNOWN")
                :errors (get result :errors [])}]
    (if-let [mkpf (:mkpf result)]
      (assoc out :material-doc-id (:mblnr mkpf))
      out)))
