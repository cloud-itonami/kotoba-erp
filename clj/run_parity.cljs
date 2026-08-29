#!/usr/bin/env nbb
;; Parity gate for the `.kotoba` FI journal-posting slice.
;;
;;   exit 0  every scenario agreed          (and at least one scenario ran)
;;   exit 1  the guest and the oracle disagreed
;;   exit 2  the question could not be asked (no compiler, compile failed,
;;           zero scenarios) -- deliberately NOT 0, so "skipped" and "passed"
;;           are not the same value.
;;
;; Usage:  nbb --classpath src:test run_parity.cljs
(ns run-parity
  (:require [clojure.string :as str]
            [kotoba-erp.store :as store]
            [kotoba-erp.fi.app :as oracle]
            [kotoba-erp.fi.effects :as fx]
            [kotoba-erp.fi.post-journal-kotoba :as guest]))

(def now "2026-06-07T00:00:00Z")

;; --- scenarios ------------------------------------------------------------
;; One spec drives both sides. Amounts are given in MINOR units; the oracle
;; takes the major-unit double it has always taken, the guest takes the i64.

(def scenarios
  [{:name "direct/balanced"
    :entry-id "DIRECT-001"
    :lines [{:account-id "1000" :minor 10000 :debit? true  :description "Cash"}
            {:account-id "2000" :minor 10000 :debit? false :description "Revenue"}]}
   {:name "direct/unbalanced-rejects"
    :entry-id "DIRECT-002"
    :lines [{:account-id "1000" :minor 10000 :debit? true  :description "Cash"}
            {:account-id "2000" :minor  9000 :debit? false :description "Revenue"}]}
   {:name "direct/three-lines"
    :entry-id "DIRECT-003"
    :lines [{:account-id "1000" :minor 6000 :debit? true  :description "Cash A"}
            {:account-id "1010" :minor 4000 :debit? true  :description "Cash B"}
            {:account-id "2000" :minor 10000 :debit? false :description "Revenue"}]}
   {:name "direct/defaults"        ;; no :entry-id, no :description, no :is-debit
    :lines [{:account-id "1000" :minor 2500}
            {:account-id "2000" :minor 2500 :debit? false}]}
   {:name "direct/empty-lines"
    :entry-id "DIRECT-004"
    :lines []}
   {:name "mm/goods-receipt"
    :event {:event-type "GoodsReceiptPosted" :receipt-id "GR-001"
            :po-number "PO-1000" :minor 10500}}
   {:name "mm/goods-receipt-defaults"
    :event {:event-type "GoodsReceiptPosted" :minor 700}}
   {:name "mm/goods-receipt-zero"
    :event {:event-type "GoodsReceiptPosted" :receipt-id "GR-002"
            :po-number "PO-2000" :minor 0}}])

(defn oracle-payload [{:keys [entry-id lines event]}]
  (if event
    (cond-> {:event-type (:event-type event)
             :total-value (/ (:minor event) 100.0)}
      (:receipt-id event) (assoc :receipt-id (:receipt-id event))
      (:po-number event)  (assoc :po-number (:po-number event)))
    (cond-> {:lines (mapv (fn [l]
                            (cond-> {:account-id (:account-id l)
                                     :amount (/ (:minor l) 100.0)}
                              (contains? l :debit?)      (assoc :is-debit (:debit? l))
                              (contains? l :description) (assoc :description (:description l))))
                          lines)}
      entry-id (assoc :entry-id entry-id))))

(defn guest-event [{:keys [entry-id lines event]}]
  (assoc
   (if event
     (cond-> {:event-type (:event-type event)
              :total-value-minor (:minor event)}
       (:receipt-id event) (assoc :receipt-id (:receipt-id event))
       (:po-number event)  (assoc :po-number (:po-number event)))
     (cond-> {:lines (mapv (fn [l]
                             (cond-> {:account-id (:account-id l)
                                      :amount-minor (:minor l)}
                               (contains? l :debit?)      (assoc :is-debit (:debit? l))
                               (contains? l :description) (assoc :description (:description l))))
                           lines)}
       entry-id (assoc :entry-id entry-id)))
   :now now))

;; --- normalisation --------------------------------------------------------
;; The two sides disagree about the REPRESENTATION of money on purpose (the
;; guest has no f64 arithmetic and money belongs in minor units anyway), so
;; the comparison converts the oracle's double into the same minor unit. It
;; does NOT relax anything else: subject, predicate, graph, field set and every
;; string must match exactly.

(defn- minor [d] (js/Math.round (* 100 d)))

(defn norm-quad
  "Plain-map view of a stored quad. `Quad` is a record and a record never
  equals a map, so both sides go through this before comparison."
  [q]
  (let [m (into {} q)]
    {:graph (:graph m) :subject (:subject m) :predicate (:predicate m)
     :object (into {} (:object m))}))

(defn norm-oracle-quad
  "As `norm-quad`, plus the ONE representation change this comparison allows:
  the oracle's major-unit double amount becomes the guest's i64 minor unit.
  Applied to the oracle side only -- applying it to both would multiply the
  already-minor guest amount by 100, and the mistake is loud rather than
  silent because only a zero amount survives it."
  [q]
  (let [m (norm-quad q)]
    (cond-> m
      (contains? (:object m) :wrbtr) (update-in [:object :wrbtr] minor))))

(defn run-oracle [spec]
  (let [s (store/mem-store)
        r (oracle/invoke {:ctx-payload (oracle-payload spec) :now now :store s})]
    {:route  (:route r)
     :status (:status r)
     :entry-id (:belnr (:bkpf r))
     :bstat  (:bstat (:bkpf r))
     :errors (vec (:validation-errors r))
     :quads  (mapv norm-oracle-quad @(:quads s))}))

(defn run-guest [step spec]
  (let [st (step {} (guest-event spec))
        out (:last st)
        s   (store/mem-store)
        d   (fx/drain! s (:effects st))]
    {:route (:route out)
     :status (:status out)
     :entry-id (:entry-id out)
     :bstat (get-in out [:bkpf :bstat])
     :errors (vec (:errors out))
     :quads (mapv norm-quad @(:quads s))
     :drain d}))

(defn compare-one [step spec]
  (let [o (run-oracle spec)
        g (run-guest step spec)
        g' (dissoc g :drain)]
    (cond
      (seq (:errors (:drain g)))
      {:name (:name spec) :ok false :why :effect-drain-errors :detail (:drain g)}

      (= o g') {:name (:name spec) :ok true :status (:status o) :quads (count (:quads o))}

      :else
      {:name (:name spec) :ok false :why :mismatch :oracle o :guest g'})))

(defn -main []
  (let [c (guest/compile-artifact)]
    (if-not (:ok c)
      (do (println "REFUSED\tcould not compile the .kotoba slice")
          (println "reason\t" (name (:reason c)))
          (println "detail\t" (:detail c))
          (println "SCENARIOS\t0")
          (.exit js/process 2))
      (-> (guest/load-guest (:path c))
          (.then
           (fn [{:keys [step required-capabilities]}]
             (println "compiler\t" (:bin c))
             (println "artifact\t" (:path c))
             (println "requiredCapabilities\t" (pr-str required-capabilities))
             (when (seq required-capabilities)
               (println "WARNING\tthe slice is supposed to need no capability"))
             (let [results (mapv #(compare-one step %) scenarios)
                   bad (remove :ok results)]
               (doseq [r results]
                 (if (:ok r)
                   (println (str "ok  \t" (:name r) "\t" (:status r)
                                 "\tquads=" (:quads r)))
                   (do (println (str "FAIL\t" (:name r) "\t" (name (:why r))))
                       (println "  oracle:" (pr-str (:oracle r)))
                       (println "  guest :" (pr-str (:guest r)))
                       (println "  detail:" (pr-str (:detail r))))))
               (println (str "SCENARIOS\t" (count results)))
               (println (str "MISMATCHES\t" (count bad)))
               (cond
                 (zero? (count results)) (.exit js/process 2)
                 (seq bad) (.exit js/process 1)
                 :else (.exit js/process 0)))))
          (.catch (fn [e]
                    (println "REFUSED\tcould not load the compiled artifact")
                    (println "detail\t" (str e))
                    (println "SCENARIOS\t0")
                    (.exit js/process 2)))))))

(-main)
