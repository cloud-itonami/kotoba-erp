(ns kotoba-erp.crm.app
  "CRM module application wiring. Port of crm_module/app.py."
  (:require [kotoba-erp.graph :as g]
            [kotoba-erp.crm.use-cases.close-opportunity :as uc]))

(def compiled
  (-> (g/state-graph)
      (g/add-node "parse" uc/parse-request)
      (g/add-node "fetch_opp" uc/fetch-opportunity)
      (g/add-node "update_stage" uc/update-stage)
      (g/add-node "validate" uc/validate-opp)
      (g/add-node "save" uc/save-opp)
      (g/add-node "reject" uc/reject-opp)
      (g/add-edge g/START "parse")
      (g/add-edge "parse" "fetch_opp")
      (g/add-conditional-edges "fetch_opp" uc/check-opp-exists
                               {"reject" "reject" "update_stage" "update_stage"})
      (g/add-edge "update_stage" "validate")
      (g/add-conditional-edges "validate" uc/check-validation
                               {"reject" "reject" "save" "save"})
      (g/add-edge "save" g/END)
      (g/add-edge "reject" g/END)
      (g/compile-graph)))

(defn invoke [initial-state]
  (g/invoke compiled (merge {:errors []} initial-state)))

(defn run [payload]
  (let [result (invoke {:input-data (or payload {})})
        out    {:status (get result :status "UNKNOWN")
                :errors (get result :errors [])}]
    (if-let [opp (:opportunity result)]
      (assoc out :opportunity-id (:Id opp) :stage (:StageName opp))
      out)))
