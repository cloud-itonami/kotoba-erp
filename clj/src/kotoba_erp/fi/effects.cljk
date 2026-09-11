(ns kotoba-erp.fi.effects
  "Host mechanism: drain the guest's INERT effect vector into the injected store.

  The `.kotoba` slice never writes. It returns `:effects` -- a vector of quad
  descriptions -- and this namespace is the only thing that turns one into a
  write. That split is the point of the migration: the decision to persist, the
  graph name, the subject, the predicate and the object are all product
  semantics and live in the guest; `store/assert-quad!` is a mechanism and
  lives here.

  Nothing here throws. An effect this host does not implement is reported as
  data (`:errors`), because a partially-drained batch that also raised would
  leave the caller unable to say which writes happened."
  (:require [kotoba-erp.store :as store]))

(defn apply-effect!
  "Execute one inert effect against `store-m`. Returns `[:ok n]` or `[:error k]`."
  [store-m {:keys [kind graph subject predicate object] :as effect}]
  (if (= "assert-quad" kind)
    (do (store/assert-quad! store-m (store/quad graph subject predicate object))
        [:ok 1])
    [:error {:reason :unknown-effect-kind :kind kind :effect effect}]))

(defn drain!
  "Execute every effect in order. Returns {:applied n :errors [...]}."
  [store-m effects]
  (reduce (fn [acc e]
            (let [[tag v] (apply-effect! store-m e)]
              (if (= :ok tag)
                (update acc :applied + v)
                (update acc :errors conj v))))
          {:applied 0 :errors []}
          (or effects [])))
