(ns kotoba-erp.fi.repository
  "FI Interface Adapter — Gateway/Repository.
  Port of fi_module/src/adapters/repository.py. Translates BKPF/BSEG entities
  to/from store quads. The store is injected (substrate boundary)."
  (:require [clojure.string :as str]
            [kotoba-erp.store :as store]
            [kotoba-erp.util :as u]
            [kotoba-erp.fi.entities :as e]))

(def default-graph "fi_journal")

(defn- default-fixtures
  "Reproduces the python `_KqeMock.get_objects` read fixture: a bare BKPF header
  for any DIRECT subject, no line items."
  [_graph subject predicate]
  (if (and (= predicate "erp:fi:bkpf_header")
           (str/includes? (str subject) "DIRECT"))
    [{:belnr "DIRECT-001" :bukrs "1000"
      :bldat (u/now-iso) :budat (u/now-iso) :bstat ""}]
    []))

(defn default-store [] (store/mem-store {:fixtures default-fixtures}))

(defn save-accounting-document
  "Translate BKPF/BSEG into quad assertions and write them to the store."
  [store-m {:keys [belnr bukrs bldat budat bstat items] :as _bkpf}
   & {:keys [graph] :or {graph default-graph}}]
  (let [subject (str "bkpf:" belnr)]
    (store/assert-quad! store-m
      (store/quad graph subject "erp:fi:bkpf_header"
                  {:belnr belnr :bukrs bukrs :bldat bldat :budat budat :bstat bstat}))
    (doseq [item items]
      (store/assert-quad! store-m
        (store/quad graph subject "erp:fi:bseg_item"
                    (select-keys item [:belnr :buzei :hkont :shkzg :wrbtr :sgtxt]))))
    nil))

(defn get-accounting-document
  "Fetch BKPF + BSEG from the store via the read API; nil if no header."
  [store-m belnr & {:keys [graph] :or {graph default-graph}}]
  (let [subject (str "bkpf:" belnr)
        headers (store/get-objects store-m graph subject "erp:fi:bkpf_header")]
    (when (seq headers)
      (let [h     (first headers)
            items (mapv e/bseg (store/get-objects store-m graph subject "erp:fi:bseg_item"))]
        (e/bkpf {:belnr (:belnr h) :bukrs (:bukrs h)
                 :bldat (:bldat h) :budat (:budat h)
                 :items items :bstat (:bstat h)})))))
