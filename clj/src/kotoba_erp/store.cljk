(ns kotoba-erp.store
  "Injectable store seam — the substrate boundary for the ERP module ports.

  Mirrors the python KQE (`assert_quad` / `get_objects`) + KSE (`publish`) host
  APIs the WASM components call. A `store` is a plain map of fns so backends are
  swappable by injection (the actor-tree Store idiom): the in-process `mem-store`
  here is the unit-test/dev backend; a live deploy injects a store whose fns
  speak to the canonical kotoba Datom log (EAVT quads), per ADR-2605262130.
  Per the substrate boundary, RisingWave/Postgres are forbidden — never reintroduce.

  Note on CBOR: the python repo wraps each object value in CBOR bytes
  (`object_cbor`). At the in-process boundary we carry the *decoded* clj data
  structure directly under `:object`; the byte-level CBOR encode/decode is a
  concern of the WASM host edge (`run` entrypoint), out of scope for bb verify.")

(defrecord Quad [graph subject predicate object])

(defn quad [graph subject predicate object]
  (->Quad graph subject predicate object))

(defn mem-store
  "An in-process store. Accepts an optional opts map:
    :fixtures  (fn [graph subject predicate] -> seq-of-objects) seeding reads.
  Captures every asserted quad and published event in atoms for assertions
  (`@(:quads s)`, `@(:events s)`)."
  ([] (mem-store {}))
  ([{:keys [fixtures] :or {fixtures (fn [_ _ _] [])}}]
   (let [quads  (atom [])
         events (atom [])]
     {:quads       quads
      :events      events
      :assert-quad (fn [q] (swap! quads conj q) nil)
      :get-objects (fn [graph subject predicate] (vec (fixtures graph subject predicate)))
      :publish     (fn [topic payload] (swap! events conj {:topic topic :payload payload})
                     "mem-cid")})))

(defn assert-quad! [store q] ((:assert-quad store) q))
(defn get-objects [store graph subject predicate] ((:get-objects store) graph subject predicate))
(defn publish! [store topic payload] ((:publish store) topic payload))
