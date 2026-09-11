(ns kotoba-erp.fi.entities
  "FI (Financial Accounting) enterprise business rules — Entities layer.
  Port of fi_module/src/domain/entities.py. Pure data + business rules, zero
  dependencies on other layers (Clean Architecture invariant).

  SAP standard models: SKA1 (G/L account master), BSEG (document segment),
  BKPF (document header)."
  (:require [kotoba-erp.util :as u]))

;; SAP SKA1 — G/L Account Master
(defrecord SKA1 [saknr   ;; G/L account number
                 txt20   ;; short text
                 xbilk]) ;; balance-sheet-account indicator (bool)

;; SAP BSEG — Accounting Document Segment (line item)
(defrecord BSEG [belnr   ;; document number
                 buzei   ;; line item number
                 hkont   ;; G/L account
                 shkzg   ;; debit/credit indicator: "S" debit, "H" credit
                 wrbtr   ;; amount in document currency
                 sgtxt]) ;; item text

;; SAP BKPF — Accounting Document Header
(defrecord BKPF [belnr   ;; document number
                 bukrs   ;; company code
                 bldat   ;; document date (ISO string)
                 budat   ;; posting date (ISO string)
                 items   ;; vector of BSEG
                 bstat]) ;; status: "V" parked/draft, "" posted, "R" rejected

(defn bseg [m] (map->BSEG m))
(defn bkpf [m] (map->BKPF (merge {:bstat "V"} m)))

(defn validate-balance
  "Enterprise rule: a journal entry must balance (Σ debits == Σ credits)."
  [{:keys [items]}]
  (let [debits  (->> items (filter #(= "S" (:shkzg %))) (map :wrbtr) (reduce + 0.0))
        credits (->> items (filter #(= "H" (:shkzg %))) (map :wrbtr) (reduce + 0.0))]
    (< (u/abs* (- debits credits)) 0.0001)))
