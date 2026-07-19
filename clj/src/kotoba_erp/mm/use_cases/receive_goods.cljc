(ns kotoba-erp.mm.use-cases.receive-goods
  "MM Use Case — ReceiveGoods (graph nodes).
  Port of mm_module/src/use_cases/receive_goods.py.

  State keys: :input-data :mkpf :ekko :errors :status"
  (:require [kotoba-erp.util :as u]
            [kotoba-erp.mm.entities :as e]
            [kotoba-erp.mm.repository :as repo]))

(defn parse-receipt [{:keys [input-data]}]
  (let [mblnr (get input-data :mblnr "GR-TEMP")
        ebeln (get input-data :ebeln "")
        items (vec
                (map-indexed
                  (fn [idx l]
                    (e/mseg {:mblnr mblnr
                             :zeile (str (inc idx))
                             :bwart "101"
                             :matnr (:matnr l)
                             :menge (double (:menge l))
                             :ebeln ebeln
                             :ebelp (get l :ebelp "10")}))
                  (get input-data :items [])))]
    {:mkpf (e/mkpf {:mblnr mblnr :budat (u/now-iso)
                    :usnam (get input-data :usnam "SYSTEM")
                    :items items :status "DRAFT"})}))

(defn fetch-po [{:keys [mkpf errors]}]
  (let [ebeln  (if (seq (:items mkpf)) (:ebeln (first (:items mkpf))) "")
        ekko   (repo/get-purchase-order (repo/default-store) ebeln)
        errors (vec (or errors []))]
    {:ekko ekko
     :errors (if ekko errors (conj errors "Purchase Order (EKKO) not found."))}))

(defn check-po-exists [{:keys [errors]}]
  (if (pos? (count (or errors []))) "reject" "validate"))

(defn validate-receipt [{:keys [mkpf ekko errors]}]
  (let [errors (vec (or errors []))]
    {:errors (if (e/validate-receipt mkpf ekko)
               errors
               (conj errors "Material Document invalid against PO (EKKO) (e.g. quantity exceeded)."))}))

(defn check-validation [{:keys [errors]}]
  (if (pos? (count (or errors []))) "reject" "post"))

(defn post-receipt [{:keys [mkpf]}]
  (let [posted (assoc mkpf :status "POSTED")]
    (repo/save-material-document (repo/default-store) posted)
    {:mkpf posted :status "POSTED"}))

(defn reject-receipt [{:keys [mkpf]}]
  {:mkpf (assoc mkpf :status "REJECTED") :status "REJECTED"})
