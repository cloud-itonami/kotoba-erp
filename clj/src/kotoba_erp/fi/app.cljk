(ns kotoba-erp.fi.app
  "FI module application wiring — Interface Adapters (input controller).
  Port of fi_module/app.py. Combines the EventRouter + PostJournal use cases
  into one master StateGraph and exposes the WASM `run` entrypoint."
  (:require [kotoba-erp.graph :as g]
            [kotoba-erp.fi.use-cases.post-journal :as pj]
            [kotoba-erp.fi.use-cases.process-event :as ev]))

(defn init-post-journal
  "Pass the router's mapped data into entry-data for the PostJournal flow."
  [{:keys [mapped-journal-data]}]
  {:entry-data mapped-journal-data})

(def compiled
  (-> (g/state-graph)
      (g/add-node "parse_incoming_payload" ev/parse-incoming-payload)
      (g/add-node "map_mm_receipt" ev/map-mm-receipt)
      (g/add-node "init_post_journal" init-post-journal)
      (g/add-node "parse_entry" pj/parse-entry)
      (g/add-node "validate_entry" pj/validate-entry)
      (g/add-node "post" pj/post-entry)
      (g/add-node "reject" pj/reject-entry)
      (g/add-edge g/START "parse_incoming_payload")
      (g/add-conditional-edges "parse_incoming_payload" ev/route-event
                               {"map_mm_receipt" "map_mm_receipt"
                                "direct_journal" "init_post_journal"})
      (g/add-edge "map_mm_receipt" "init_post_journal")
      (g/add-edge "init_post_journal" "parse_entry")
      (g/add-edge "parse_entry" "validate_entry")
      (g/add-conditional-edges "validate_entry" pj/check-validation
                               {"reject" "reject" "post" "post"})
      (g/add-edge "post" g/END)
      (g/add-edge "reject" g/END)
      (g/compile-graph)))

(defn invoke
  "Run the compiled FI graph on an initial state."
  [initial-state]
  (g/invoke compiled (merge {:validation-errors []} initial-state)))

(defn run
  "WASM entrypoint analogue. Takes an already-decoded ctx payload map (keyword
  keys) and returns the output map. CBOR decode/encode is the WASM host's edge
  (the python `WitWorld.run` does `cbor2.loads`/`dumps` around this core)."
  [ctx-payload]
  (let [result (invoke {:ctx-payload (or ctx-payload {})})
        out    {:status (get result :status "UNKNOWN")
                :errors (get result :validation-errors [])}]
    (if-let [bkpf (:bkpf result)]
      (assoc out :entry-id (:belnr bkpf))
      out)))
