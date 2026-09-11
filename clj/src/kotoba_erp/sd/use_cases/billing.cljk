(ns kotoba-erp.sd.use-cases.billing
  "SD Use Case — GenerateBilling (graph nodes).
  Port of sd_module/src/use_cases/billing.py.

  State keys: :input-data :vbrk :vbak :errors :status"
  (:require [kotoba-erp.util :as u]
            [kotoba-erp.sd.entities :as e]
            [kotoba-erp.sd.repository :as repo]))

(defn parse-billing-request [{:keys [input-data]}]
  {:vbrk (e/vbrk {:vbeln (get input-data :billing-id "INV-TEMP")
                  :fkart "F2" :kunnr "" :fkdat (u/now-iso)
                  :netwr 0.0 :items []})})

(defn fetch-sales-order [{:keys [input-data errors]}]
  (let [vbak   (repo/get-sales-order (repo/default-store) (get input-data :order-id ""))
        errors (vec (or errors []))]
    {:vbak vbak
     :errors (if vbak errors (conj errors "Sales Order (VBAK) not found."))}))

(defn check-so-exists [{:keys [errors]}]
  (if (pos? (count (or errors []))) "reject" "generate_lines"))

(defn generate-lines [{:keys [vbrk vbak]}]
  (let [lines (vec
                (map-indexed
                  (fn [idx vbap]
                    (e/vbrp {:vbeln (:vbeln vbrk)
                             :posnr (str (* (inc idx) 10))
                             :aubel (:vbeln vbak)
                             :aupos (:posnr vbap)
                             :matnr (:matnr vbap)
                             :fkimg (:kwmeng vbap)
                             :netwr (* (:kwmeng vbap) (:netpr vbap))}))
                  (:items vbak)))
        total (reduce + 0.0 (map :netwr lines))]
    {:vbrk (assoc vbrk :kunnr (:kunnr vbak) :items lines :netwr total)}))

(defn validate-billing [{:keys [vbrk errors]}]
  (let [errors (vec (or errors []))]
    {:errors (if (e/validate-totals vbrk)
               errors
               (conj errors "Billing Document (VBRK) totals do not match line items."))}))

(defn check-validation [{:keys [errors]}]
  (if (pos? (count (or errors []))) "reject" "post"))

(defn post-billing [{:keys [vbrk]}]
  (let [posted (assoc vbrk :status "POSTED")]
    (repo/save-billing-document (repo/default-store) posted)
    {:vbrk posted :status "POSTED"}))

(defn reject-billing [{:keys [vbrk]}]
  {:vbrk (assoc vbrk :status "REJECTED") :status "REJECTED"})
