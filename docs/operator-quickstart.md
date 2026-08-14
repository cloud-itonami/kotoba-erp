# Operator quickstart

Drive all four ERP modules from a cold checkout. Every command below was run
end to end on 2026-08-14 and the output shown is what it actually printed — if a
step does not reproduce, that is a defect, not drift in the instructions.

**Time:** about two minutes. **Writes nothing** outside the checkout: the
default store is in-memory and is discarded when the process exits.

## 0. Prerequisites

One tool: `babashka`.

```bash
bb --version      # verified against v1.12.218
```

> `nbb` does not work here, and it is worth knowing why before you try it. The
> suite dies at load with `Unable to resolve symbol: cljs.core/ExceptionInfo`,
> because `clj/test/kotoba_erp/graph_test.cljc:21` names that symbol in a
> `#?(:cljs ...)` branch and sci does not resolve it. `bb` is the only host that
> runs this repo today.

All commands run from the `clj/` directory:

```bash
cd clj
```

## 1. Run the suite

```bash
bb run_tests.clj
```

```
Ran 15 tests containing 54 assertions.
0 failures, 0 errors.

kotoba-erp clj port — {:test 15, :pass 54, :fail 0, :error 0}
```

Exit code is 0. If this is not green, stop here — every step below assumes it.

## 2. Post a journal entry (FI)

Each module's `run` takes an already-decoded payload map and returns a status
map. Nothing here speaks CBOR; encoding is the WASM host's edge.

```bash
bb --classpath src -e '
(require (quote [kotoba-erp.fi.app :as fi]))
(prn (fi/run {:entry-id "JE-1001"
              :lines [{:account-id "1000" :amount 250.0 :is-debit true  :description "Cash"}
                      {:account-id "4000" :amount 250.0 :is-debit false :description "Revenue"}]}))'
```

```clojure
{:status "POSTED", :errors [], :entry-id "JE-1001"}
```

Now break the balance — debit 250, credit 100:

```bash
bb --classpath src -e '
(require (quote [kotoba-erp.fi.app :as fi]))
(prn (fi/run {:entry-id "JE-1002"
              :lines [{:account-id "1000" :amount 250.0 :is-debit true}
                      {:account-id "4000" :amount 100.0 :is-debit false}]}))'
```

```clojure
{:status "REJECTED", :errors ["Accounting Document (BKPF) does not balance."], :entry-id "JE-1002"}
```

That is the FI graph taking its conditional edge to `reject` instead of `post`.
Double-entry balance is the one business rule enforced before persistence.

## 3. Let an MM event drive FI (cross-module routing)

FI's entrypoint is also an event router. Hand it a `GoodsReceiptPosted` event
instead of a journal payload and it maps the receipt into a balanced pair of
lines — inventory 1300 debit, GR/IR clearing 2110 credit — before running the
same posting flow:

```bash
bb --classpath src -e '
(require (quote [kotoba-erp.fi.app :as fi]))
(prn (fi/run {:event-type "GoodsReceiptPosted"
              :receipt-id "GR-77" :po-number "PO-9" :total-value 1500.0}))'
```

```clojure
{:status "POSTED", :errors [], :entry-id "JE-GR-77"}
```

The document id is derived from the receipt (`JE-` + `GR-77`), which is how you
tell a routed posting from a direct one.

## 4. Goods receipt (MM), billing (SD), opportunity close (CRM)

```bash
bb --classpath src -e '
(require (quote [kotoba-erp.mm.app :as mm]) (quote [kotoba-erp.sd.app :as sd])
         (quote [kotoba-erp.crm.app :as crm]))
(println "MM ok:   " (pr-str (mm/run {:mblnr "GR-5001" :ebeln "PO-1000" :usnam "OPERATOR"
                                      :items [{:matnr "MAT-01" :menge 10.0 :ebelp "10"}]})))
(println "MM over: " (pr-str (mm/run {:mblnr "GR-5002" :ebeln "PO-1000"
                                      :items [{:matnr "MAT-01" :menge 999.0 :ebelp "10"}]})))
(println "SD ok:   " (pr-str (sd/run {:billing-id "INV-2001" :order-id "SO-1000"})))
(println "CRM won: " (pr-str (crm/run {:opportunity-id "006000000000001AAA" :stage-name "Closed Won"})))'
```

```
MM ok:    {:status "POSTED", :errors [], :material-doc-id "GR-5001"}
MM over:  {:status "REJECTED", :errors ["Material Document invalid against PO (EKKO) (e.g. quantity exceeded)."], :material-doc-id "GR-5002"}
SD ok:    {:status "POSTED", :errors [], :billing-id "INV-2001"}
CRM won:  {:status "SUCCESS", :errors [], :opportunity-id "006000000000001AAA", :stage "Closed Won"}
```

The ids are not arbitrary. The in-memory store ships one open purchase order
`PO-1000` (line `10`, material `MAT-01`, quantity 100) and one open sales order
`SO-1000` (line `10`, quantity 10 at 100.0). `MM over` receives 999 against a PO
for 100, so the quantity rule rejects it.

CRM returns `SUCCESS` rather than `POSTED` — the four modules do not share a
status vocabulary. Closing the same opportunity as `"Closed Lost"` also returns
`SUCCESS`: the `Amount > 0 and 100% probability` rule guards `Closed Won` only,
and a lost deal is not required to satisfy it.

## 5. What this run does *not* prove

Read this before you write an integration test against the in-memory store.

**The MM, SD and CRM read-fixtures ignore the subject they are asked for.**
`default-fixtures` in those three repositories is `[_graph _subject predicate]`
and dispatches on the predicate alone, so a lookup for any id gets the one
seeded record back. Bill a sales order that does not exist:

```bash
bb --classpath src -e '
(require (quote [kotoba-erp.sd.app :as sd]))
(prn (sd/run {:billing-id "INV-2002" :order-id "SO-NOPE"}))'
```

```clojure
{:status "POSTED", :errors [], :billing-id "INV-2002"}
```

It bills it. The same holds for a goods receipt against a nonexistent PO. So:

- **the `not found` reject branches in MM, SD and CRM are unreachable in
  process**, and no test in `clj/test/` exercises any reject branch at all;
- **FI is the exception** — its fixture does read the subject (it answers only
  for subjects containing `DIRECT`), so FI's lookup miss is real;
- a green run here says the happy paths and the *rule-based* rejections
  (unbalanced document, over-received quantity) work. It says nothing about
  behaviour on a missing record.

Against a live store — one whose `get-objects` actually queries the kotoba Datom
log — those branches become reachable. That is the seam to inject at.

## 6. Where to go next

- **Swap the store.** `kotoba-erp.store/mem-store` returns a plain map of three
  fns — `:assert-quad`, `:get-objects`, `:publish` — plus two capture atoms.
  Any map carrying those three fns works; `repository.cljc` in each module is
  the only code that touches it.
- **Inspect what was written.** `mem-store` captures every quad and event, so
  `@(:quads s)` and `@(:events s)` show exactly what a posting persisted and
  published. `clj/test/kotoba_erp/fi_test.cljc` uses this directly.
- **Read a graph.** Each `app.cljc` is the whole control flow of its module in
  about 20 lines of `add-node` / `add-edge` / `add-conditional-edges`.
- **Boundaries and caveats** are in [`../README.md`](../README.md); the
  architectural intent is in [`ARCHITECTURE.md`](ARCHITECTURE.md).
