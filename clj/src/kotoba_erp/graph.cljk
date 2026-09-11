(ns kotoba-erp.graph
  "Minimal, pure StateGraph runner — a faithful clj port of the `kotoba_langgraph`
  Python shim (`StateGraph`/`START`/`END`/`compile`/`invoke`) the ERP modules wire.

  Semantics (matching LangGraph / the python shim):
  - a *node* is a fn `state -> partial-state`; its return map is MERGED into state.
  - a plain edge `from -> to` is followed unconditionally after `from` runs.
  - a *conditional* edge `from` runs a router fn `state -> branch-key`, then maps
    `branch-key -> target node` via a routing map.
  - traversal starts at the node wired from `START` and stops at `END`.

  This stays pure (no atoms, no I/O) so it loads and runs identically on JVM/bb,
  cljs, and WASM — the `.cljc` portability rule.")

(def START ::start)
(def END ::end)

(defn state-graph
  "Construct an empty builder. The `_schema` arg mirrors the python
  `StateGraph(StateType)` call shape; clj is dynamically typed so it is ignored."
  ([] (state-graph nil))
  ([_schema] {:nodes {} :edges {} :cond {} :entry nil}))

(defn add-node [g node-name f]
  (assoc-in g [:nodes node-name] f))

(defn add-edge [g from to]
  (if (= from START)
    (assoc g :entry to)
    (assoc-in g [:edges from] to)))

(defn add-conditional-edges [g from router routing]
  (assoc-in g [:cond from] {:router router :routing routing}))

(defn compile-graph
  "Validate and freeze the builder into an invokable graph (mirrors `.compile()`)."
  [g]
  (when-not (:entry g)
    (throw (ex-info "StateGraph has no START edge" {:graph g})))
  g)

(defn- next-node [g node state]
  (if-let [{:keys [router routing]} (get-in g [:cond node])]
    (let [branch (router state)]
      (or (get routing branch)
          (throw (ex-info "conditional router returned an unmapped branch"
                          {:node node :branch branch :routing routing}))))
    (get-in g [:edges node] END)))

(defn invoke
  "Run the compiled graph from START to END, threading + merging state.
  Guards against cycles (the ERP graphs are acyclic) via a step ceiling."
  ([g state] (invoke g state 10000))
  ([g state max-steps]
   (loop [node (:entry g)
          state state
          steps 0]
     (when (> steps max-steps)
       (throw (ex-info "StateGraph exceeded max steps (cycle?)"
                       {:node node :steps steps})))
     (if (= node END)
       state
       (let [f (or (get-in g [:nodes node])
                   (throw (ex-info "unknown node" {:node node})))
             delta (f state)
             state' (merge state delta)]
         (recur (next-node g node state') state' (inc steps)))))))
