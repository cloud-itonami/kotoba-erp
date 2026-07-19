(ns kotoba-erp.sd.app
  "SD module application wiring. Port of sd_module/app.py."
  (:require [kotoba-erp.graph :as g]
            [kotoba-erp.sd.use-cases.billing :as uc]))

(def compiled
  (-> (g/state-graph)
      (g/add-node "parse" uc/parse-billing-request)
      (g/add-node "fetch_so" uc/fetch-sales-order)
      (g/add-node "generate_lines" uc/generate-lines)
      (g/add-node "validate" uc/validate-billing)
      (g/add-node "post" uc/post-billing)
      (g/add-node "reject" uc/reject-billing)
      (g/add-edge g/START "parse")
      (g/add-edge "parse" "fetch_so")
      (g/add-conditional-edges "fetch_so" uc/check-so-exists
                               {"reject" "reject" "generate_lines" "generate_lines"})
      (g/add-edge "generate_lines" "validate")
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
    (if-let [vbrk (:vbrk result)]
      (assoc out :billing-id (:vbeln vbrk))
      out)))
