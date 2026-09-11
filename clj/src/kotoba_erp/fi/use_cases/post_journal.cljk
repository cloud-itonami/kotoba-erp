(ns kotoba-erp.fi.use-cases.post-journal
  "FI Use Case — PostJournalEntry (graph nodes).
  Port of fi_module/src/use_cases/post_journal.py. Each node is a fn
  `state -> partial-state` merged by kotoba-erp.graph/invoke.

  State keys: :entry-data :bkpf :validation-errors :status
  Host seams injected by the caller: :now (clock) :store (persistence)."
  (:require [kotoba-erp.util :as u]
            [kotoba-erp.fi.entities :as e]
            [kotoba-erp.fi.repository :as repo]))

(defn parse-entry
  "Parse the incoming entry map into BKPF/BSEG entities.

  The clock is a HOST mechanism, so it is read from state (`:now`) when the
  caller injects one and only falls back to `u/now-iso` when it does not. That
  injection is what lets this oracle be compared byte-for-byte against the
  `.kotoba` slice, which cannot read a clock at all."
  [{:keys [entry-data now]}]
  (let [belnr (get entry-data :entry-id "TEMP")
        stamp (or now (u/now-iso))
        items (vec
                (map-indexed
                  (fn [idx l]
                    (e/bseg {:belnr belnr
                             :buzei (str (inc idx))
                             :hkont (:account-id l)
                             :shkzg (if (get l :is-debit true) "S" "H")
                             :wrbtr (double (:amount l))
                             :sgtxt (get l :description "")}))
                  (get entry-data :lines [])))]
    {:bkpf (e/bkpf {:belnr belnr :bukrs "1000"
                    :bldat stamp :budat stamp
                    :items items :bstat "V"})}))

(defn validate-entry
  "Validate that the accounting document balances."
  [{:keys [bkpf validation-errors]}]
  (let [errors (vec (or validation-errors []))]
    {:validation-errors
     (if (e/validate-balance bkpf)
       errors
       (conj errors "Accounting Document (BKPF) does not balance."))}))

(defn check-validation [{:keys [validation-errors]}]
  (if (pos? (count (or validation-errors []))) "reject" "post"))

(defn post-entry
  "Mark POSTED and persist via the repository adapter.

  The store is a HOST mechanism, so it is taken from state (`:store`) when the
  caller injects one. Without injection the writes go to a throwaway
  `default-store`, which is what every caller before the injection seam did."
  [{:keys [bkpf store]}]
  (let [posted (assoc bkpf :bstat "")]
    (repo/save-accounting-document (or store (repo/default-store)) posted)
    {:bkpf posted :status "POSTED"}))

(defn reject-entry [{:keys [bkpf]}]
  {:bkpf (assoc bkpf :bstat "R") :status "REJECTED"})
