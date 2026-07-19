(ns kotoba-erp.util
  "Small portable helpers shared by the ERP module ports.")

(defn abs* "Portable absolute value (avoids JVM-only `Math/abs`)." [x]
  (if (neg? x) (- x) x))

(defn now-iso
  "Current instant as an ISO-8601 string (python `datetime.now().isoformat()`).
  Reader-conditioned so the `.cljc` stays JVM/bb + cljs + WASM portable."
  []
  #?(:clj  (.toString (java.time.Instant/now))
     :cljs (.toISOString (js/Date.))
     :default "1970-01-01T00:00:00Z"))
