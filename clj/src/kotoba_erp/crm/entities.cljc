(ns kotoba-erp.crm.entities
  "CRM enterprise business rules — Entities layer.
  Port of crm_module/src/domain/entities.py. Salesforce standard objects
  (Account / Contact / Opportunity). SFDC field names are preserved as keys.")

(defrecord Account [Id Name Industry Type])
(defrecord Contact [Id AccountId FirstName LastName Email])
(defrecord Opportunity [Id AccountId Name StageName Amount CloseDate Probability])

(defn account [m] (map->Account m))
(defn contact [m] (map->Contact m))
(defn opportunity [m] (map->Opportunity m))

(defn is-closed? [{:keys [StageName]}]
  (contains? #{"Closed Won" "Closed Lost"} StageName))

(defn validate-won
  "Business rule: a Closed Won opportunity must have Amount > 0 and 100% probability."
  [{:keys [StageName Amount Probability]}]
  (if (= StageName "Closed Won")
    (and (> Amount 0) (== Probability 100.0))
    true))
