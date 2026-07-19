(ns kotoba-erp.crm.repository
  "CRM Interface Adapter — Gateway/Repository.
  Port of crm_module/src/adapters/repository.py. Reads/writes Opportunity quads
  and publishes a cross-cloud event (Salesforce -> SAP) on Closed Won."
  (:require [kotoba-erp.store :as store]
            [kotoba-erp.util :as u]
            [kotoba-erp.crm.entities :as e]))

(def default-graph "crm_salesforce")

(defn- default-fixtures
  "Reproduces the python `_KqeMock`: a single Negotiation/Review opportunity."
  [_graph _subject predicate]
  (if (= predicate "sfdc:opportunity")
    [{:Id "006000000000001AAA" :AccountId "001000000000001AAA"
      :Name "Big Deal 2026" :StageName "Negotiation/Review"
      :Amount 50000.0 :CloseDate (u/now-iso) :Probability 90.0}]
    []))

(defn default-store [] (store/mem-store {:fixtures default-fixtures}))

(defn get-opportunity
  [store-m opp-id & {:keys [graph] :or {graph default-graph}}]
  (let [subject (str "Opportunity:" opp-id)
        objs    (store/get-objects store-m graph subject "sfdc:opportunity")]
    (when (seq objs)
      (e/opportunity (first objs)))))

(defn save-opportunity
  "Persist the Opportunity and, when Closed Won, publish a cross-cloud event."
  [store-m {:keys [Id AccountId Name StageName Amount CloseDate Probability] :as _opp}
   & {:keys [graph] :or {graph default-graph}}]
  (let [subject (str "Opportunity:" Id)]
    (store/assert-quad! store-m
      (store/quad graph subject "sfdc:opportunity"
                  {:Id Id :AccountId AccountId :Name Name :StageName StageName
                   :Amount Amount :CloseDate CloseDate :Probability Probability}))
    (when (= StageName "Closed Won")
      (store/publish! store-m "crm.opportunity.won"
                      {:event-type "OpportunityClosedWon"
                       :opportunity-id Id :account-id AccountId
                       :amount Amount :timestamp (u/now-iso)}))
    nil))
