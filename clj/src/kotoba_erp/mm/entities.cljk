(ns kotoba-erp.mm.entities
  "MM (Materials Management) enterprise business rules — Entities layer.
  Port of mm_module/src/domain/entities.py. SAP standard models:
  MARA (material master), EKPO/EKKO (purchasing doc item/header),
  MSEG/MKPF (material doc segment/header).")

(defrecord MARA [matnr maktx meins])
(defrecord EKPO [ebeln ebelp matnr menge netpr])
(defrecord EKKO [ebeln lifnr bedat items status])
(defrecord MSEG [mblnr zeile bwart matnr menge ebeln ebelp])
(defrecord MKPF [mblnr budat usnam items status])

(defn ekpo [m] (map->EKPO m))
(defn ekko [m] (map->EKKO (merge {:status "OPEN"} m)))
(defn mseg [m] (map->MSEG m))
(defn mkpf [m] (map->MKPF (merge {:status "DRAFT"} m)))

(defn validate-receipt
  "Enterprise rule: a goods receipt must match a valid PO and not exceed the
  ordered quantity (mapped by material number, mirroring the python)."
  [{:keys [items] :as _mkpf} {po-items :items :as _ekko}]
  (let [po-materials (into {} (map (juxt :matnr :menge) po-items))]
    (every?
      (fn [{:keys [matnr menge]}]
        (and (contains? po-materials matnr)
             (> menge 0)
             (<= menge (get po-materials matnr))))
      items)))
