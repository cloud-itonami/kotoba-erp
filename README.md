# kotoba-erp

A clean-architecture ERP core — **FI** (financial accounting), **MM** (materials
management), **SD** (sales & distribution) and **CRM** — written as portable
`.cljc` and driven by an in-repo `StateGraph` runner. Each module exposes one
`run` entrypoint that takes a decoded payload map and returns a status map.

This repository was extracted from `etzhayyim/root/60-apps/kotoba-erp`
(`migration.edn` pins the source revision). The `.cljc` port under `clj/` is the
canonical code; the Python originals were deleted in 2026-06.

**Start here:** [`docs/operator-quickstart.md`](docs/operator-quickstart.md) —
run the suite and drive all four modules in about two minutes.
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) describes the original PyWasm
design and is retained as the architectural intent, not as a description of the
code that runs today (see *Reading ARCHITECTURE.md* below).

## What this repo is, and what it is not

| | |
|---|---|
| **Is** | The ERP *domain and use-case layers* — entities, business rules, and the graphs that sequence them. Pure functions plus one injected store seam. |
| **Is not** | A datastore. Persistence is the injected `store` map; the canonical backing is the kotoba Datom log (EAVT quads, ADR-2605262130). |
| **Is not** | A service. There is no HTTP surface, no deploy target, and no scheduled job in this repo. |
| **Is not** | A workflow engine. `kotoba-erp.graph` is a 67-line in-repo shim, not `langgraph-clj` — the port needs `add-node` / `add-edge` / `add-conditional-edges` / `invoke` and nothing else. |

## Layout

```
README.edn                machine-readable extraction record
migration.edn             source revision + path this repo was extracted from
docs/ARCHITECTURE.md      original PyWasm architecture (intent; see caveat below)
docs/operator-quickstart.md
clj/                      the canonical implementation (20 .cljc, ~34 KB)
  src/kotoba_erp/
    graph.cljc              StateGraph shim — START/END, nodes, conditional edges
    store.cljc              injected store seam (assert-quad / get-objects / publish)
    util.cljc               portable helpers
    fi/  crm/  mm/  sd/     entities | repository | use_cases/ | app
  test/kotoba_erp/        5 suites — 15 tests / 54 assertions
  run_tests.clj           the test runner
{fi,mm,sd,crm}_module/app.wasm    compiled PyWasm artifacts (see below)
```

Every module follows the same four-layer shape:

- `entities.cljc` — records and business rules. No dependencies on other layers.
- `use_cases/*.cljc` — graph nodes, each `state -> partial-state`, merged by the runner.
- `repository.cljc` — translates entities to and from store quads.
- `app.cljc` — wires nodes into one compiled graph and exposes `run`.

## Running it

```bash
cd clj && bb run_tests.clj      # 15 tests, 54 assertions
```

Worked examples for all four modules, with their actual output, are in
[`docs/operator-quickstart.md`](docs/operator-quickstart.md).

## Known gaps

These are measured, not suspected. Each was observed while writing the
quickstart on 2026-08-14.

1. **The dev read-fixtures for MM, SD and CRM ignore the subject they are
   asked for.** `default-fixtures` in those three repositories takes
   `[_graph _subject predicate]` and answers on `predicate` alone, so
   `get-purchase-order` returns `PO-1000` for *any* `ebeln`. The practical
   consequence is that the `reject` branch for a missing PO / sales order /
   opportunity **cannot be reached in-process** — billing a nonexistent sales
   order returns `POSTED`. FI's fixture is the exception: it does read the
   subject. A live store makes these branches reachable; the in-memory one does
   not, so do not read a green in-process run as evidence that the not-found
   path works.
2. **No test covers a `reject` branch.** The 15 tests exercise the happy paths
   and the balance rule; the strings `reject` and `not found` do not appear in
   `clj/test/` at all. Combined with (1), the "not found" rejections in MM, SD
   and CRM are both untested and unreachable.
3. **The suite runs only under `bb`.** `nbb` — the sanctioned script host for
   this workspace — cannot load it: `graph_test.cljc:21` names
   `cljs.core/ExceptionInfo` in a reader conditional, which sci does not
   resolve, and the run dies before the first assertion. `bb` is a retired
   script host here, so the one working path is the deprecated one.
4. **~76 MB of build artifacts are committed.** The four `*/app.wasm`
   components are 18.4–19.3 MB each. They are outputs of the deleted Python
   sources, so nothing in this repo can regenerate them, and they are carried in
   git history rather than in DataLad/git-annex as this workspace's large-binary
   policy requires. Removing them is a deploy cutover, not a cleanup.

## Reading ARCHITECTURE.md

`docs/ARCHITECTURE.md` predates the Clojure port and describes the PyWasm
design: Python `src/domain/`, `src/use_cases/`, `src/adapters/` directories
compiled to WASM components over a WIT/CBOR boundary. **Those Python paths no
longer exist.** The layer *shape* it prescribes is exactly what `clj/src/` still
implements, module for module, which is why it is kept — read it for the
intended boundaries and the substrate invariants, and read `clj/` for what runs.

The substrate invariants it states do still bind: reads go to the Datalog
arrangements over content-addressed blocks with no projection layer, business
logic holds no platform private keys, and Postgres/RisingWave side caches are
forbidden. The `store` seam is where that boundary is enforced — it is the only
way any module reaches persistence.
