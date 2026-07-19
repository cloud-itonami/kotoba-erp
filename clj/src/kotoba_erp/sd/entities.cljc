(ns kotoba-erp.sd.entities
  "SD (Sales & Distribution) enterprise business rules — Entities layer.
  Port of sd_module/src/domain/entities.py. SAP standard models:
  VBAK/VBAP (sales doc header/item), VBRK/VBRP (billing doc header/item).")

(defrecord VBAK [vbeln kunnr audat items status])
(defrecord VBAP [vbeln posnr matnr kwmeng netpr])
(defrecord VBRK [vbeln fkart kunnr fkdat netwr items status])
(defrecord VBRP [vbeln posnr aubel aupos matnr fkimg netwr])

(defn vbak [m] (map->VBAK (merge {:status "OPEN"} m)))
(defn vbap [m] (map->VBAP m))
(defn vbrk [m] (map->VBRK (merge {:status "DRAFT"} m)))
(defn vbrp [m] (map->VBRP m))

(defn validate-totals
  "Enterprise rule: header net value must equal the sum of line net values."
  [{:keys [netwr items]}]
  (let [calculated (reduce + 0.0 (map :netwr items))]
    (< (let [d (- netwr calculated)] (if (neg? d) (- d) d)) 0.0001)))
