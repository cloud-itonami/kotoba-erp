(ns kotoba-erp.kotoba-doc
  "Host mechanism: the wire boundary between clj data and a Kotoba `:document`.

  A `:document` is the inert EDN-shaped tree the compiled guest speaks. On the
  JS host it is a tagged JS array: #js [\"map\" #js [#js [k v] ...]],
  #js [\"vector\" #js [...]], #js [\"string\" s], #js [\"i64\" BigInt],
  #js [\"bool\" b], #js [\"keyword\" \":k\"], #js [\"null\"].

  Two host obligations live here and nowhere else, because getting either
  wrong is rejected by the guest at the boundary rather than silently:

    * map entries must be in CANONICAL ORDER -- keyword keys sorted by their
      \":name\" string. An unsorted map is rejected with `noncanonical-doc-map`.
    * integers must be BigInt. A JS number is rejected with `invalid-i64`.

  This is a JS-host codec, so it is `.cljs` rather than `.cljc` on purpose:
  there is no BigInt on the JVM and no honest `:clj` branch to write."
  (:require [clojure.string :as str]))

(declare ->doc)

(defn- entry [k v]
  #js [#js ["keyword" (str k)] (->doc v)])

(defn ->doc
  "Encode clj data as a Kotoba `:document`."
  [x]
  (cond
    (nil? x)     #js ["null"]
    (boolean? x) #js ["bool" x]
    (keyword? x) #js ["keyword" (str x)]
    (string? x)  #js ["string" x]
    (integer? x) #js ["i64" (js/BigInt x)]
    (map? x)     #js ["map" (->> (seq x)
                                 (sort-by (fn [[k _]] (str k)))
                                 (mapv (fn [[k v]] (entry k v)))
                                 (apply array))]
    (sequential? x) #js ["vector" (apply array (mapv ->doc x))]
    :else (throw (ex-info "value has no :document encoding"
                          {:value x :type (type x)}))))

(defn doc->
  "Decode a Kotoba `:document` back into clj data."
  [d]
  (let [tag (aget d 0)]
    (case tag
      "null"    nil
      "bool"    (aget d 1)
      "i64"     (js/Number (aget d 1))
      "f64"     (aget d 1)
      "string"  (aget d 1)
      "keyword" (keyword (subs (aget d 1) 1))
      "symbol"  (symbol (aget d 1))
      ("vector" "list" "set") (mapv doc-> (array-seq (aget d 1)))
      "map"     (into {} (map (fn [e]
                                [(keyword (subs (aget (aget e 0) 1) 1))
                                 (doc-> (aget e 1))]))
                      (array-seq (aget d 1)))
      (throw (ex-info "unknown :document tag" {:tag tag})))))
