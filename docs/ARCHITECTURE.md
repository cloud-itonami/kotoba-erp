# Kotoba ERP Architecture

## Overview
This document defines the architectural blueprint for the SAP-scale ERP system implemented using `Kotoba`, `Wasm`, and `PyWasm`. The system adheres strictly to **Clean Architecture** principles, ensuring that enterprise business rules and application use cases are decoupled from external agencies, UI, and databases.

## Technology Stack
- **Canonical Storage Substrate**: `kotoba-kqe` (Datalog engine with EAVT/AEVT/AVET/VAET index arrangements) as per ADR-2605262130.
- **Application Execution Environment**: `kotoba-runtime` WASM Component Model.
- **Business Logic Layer**: `PyWasm` (`componentize-py`), compiling Python-based `LangGraph` state machines and pure Python domain logic into WASM components.
- **Inter-Process Communication**: CBOR over WIT (`world.wit` `kotoba-node`).

## Clean Architecture Layers

### 1. Entities (Enterprise Business Rules)
Located in `src/domain/`.
Contains pure Python classes and Datalog schema definitions for core ERP concepts:
- **Financial Accounting (FI)**: General Ledger, Chart of Accounts, Journal Entries, Postings.
- **Controlling (CO)**: Cost Centers, Profit Centers.
- **Materials Management (MM)**: Material Master, Purchase Orders, Goods Receipt.
- **Sales and Distribution (SD)**: Customer Master, Sales Orders, Invoicing.

*Rule: Entities have zero dependencies on other layers.*

### 2. Use Cases (Application Business Rules)
Located in `src/use_cases/`.
Implemented as `LangGraph` workflows (`StateGraph`). 
Examples:
- `PostJournalEntryUseCase`: Validates debits/credits balance, updates accounts.
- `ApprovePurchaseOrderUseCase`: Routes PO through approval workflow based on value limits.

*Rule: Use cases depend only on Entities. They define interfaces (Ports) for data persistence which the outer layers implement.*

### 3. Interface Adapters
Located in `src/adapters/`.
Translates data between the Use Cases and the external Frameworks.
- **Input Controllers**: The `WitWorld.run` entrypoint mapping incoming CBOR `ctx_cbor` to Use Case input models.
- **Gateways/Repositories**: Adapters implementing the Use Case persistence ports, translating Python entity objects into `Kotoba` Datalog transactions (`EAVT` quad insertions).

### 4. Frameworks & Drivers
Located in `src/framework/` and provided by the host.
- The `KotobaCheckpointer` (provided by `kotoba_langgraph`).
- `kotoba-runtime` providing the WASM host environment.
- Any external system interfaces.

## Substrate Invariants (ADR-2605262130)
- **No Projection Layer**: All reads MUST hit `kotoba-kqe` arrangements directly over content-addressed blocks. No Postgres/SQLite side caches.
- **Server-Key Invariant**: Business logic holds no platform private keys.
- **PyWasm Compilation**: Every business module is compiled into a standalone `.wasm` component via `build-pywasm.sh`.

## Domain Driven Design (DDD) Bounded Contexts
The ERP is split into bounded contexts. Each context compiles to a separate PyWasm module:
1. `fi_module.wasm` (Financials)
2. `mm_module.wasm` (Materials Management)
3. `sd_module.wasm` (Sales & Distribution)
4. `hr_module.wasm` (Human Resources)
