(ns kotoba-erp.crm.use-cases.close-opportunity
  "CRM Use Case — CloseOpportunity (graph nodes).
  Port of crm_module/src/use_cases/close_opportunity.py.

  State keys: :input-data :opportunity :errors :status"
  (:require [kotoba-erp.crm.entities :as e]
            [kotoba-erp.crm.repository :as repo]))

(defn parse-request [_state] {:status "PARSED"})

(defn fetch-opportunity [{:keys [input-data errors]}]
  (let [opp-id (get input-data :opportunity-id "")
        opp    (repo/get-opportunity (repo/default-store) opp-id)
        errors (vec (or errors []))]
    {:opportunity opp
     :errors (if opp errors (conj errors "Opportunity not found."))}))

(defn check-opp-exists [{:keys [errors]}]
  (if (pos? (count (or errors []))) "reject" "update_stage"))

(defn update-stage [{:keys [opportunity input-data]}]
  (let [stage (get input-data :stage-name "Closed Won")
        opp   (assoc opportunity :StageName stage)
        opp   (cond-> opp
                (= stage "Closed Won") (assoc :Probability 100.0)
                (= stage "Closed Lost") (assoc :Probability 0.0))]
    {:opportunity opp}))

(defn validate-opp [{:keys [opportunity errors]}]
  (let [errors (vec (or errors []))]
    {:errors (if (e/validate-won opportunity)
               errors
               (conj errors "Validation Failed: Won opportunity must have Amount > 0 and 100% Probability."))}))

(defn check-validation [{:keys [errors]}]
  (if (pos? (count (or errors []))) "reject" "save"))

(defn save-opp [{:keys [opportunity]}]
  (repo/save-opportunity (repo/default-store) opportunity)
  {:status "SUCCESS"})

(defn reject-opp [_state] {:status "REJECTED"})
