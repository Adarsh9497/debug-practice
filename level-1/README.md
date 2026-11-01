# 🎮 LEVEL 1: Amazon Fulfillment & Outbound Pallet Consolidation Engine

## 1. System Description

Amazon fulfillment centers consolidate outbound parcels into regional pallets before transferring shipments to sortation centers. `FulfillmentConsolidationService` prepares pallets and calculates the shipment billing manifest from one inbound batch of parcels.

## 2. Business Requirements

* Hazardous parcels must be excluded from standard pallet consolidation and billing.
* Parcels are grouped by the first three digits of the destination ZIP code.
* Within each region, pack `HIGH` priority before `MEDIUM`, then `STANDARD`. For equal priority, pack lighter parcels first.
* A pallet holds at most 25,000g and 8 parcels.
* Start a new pallet only when the next parcel would make the total weight exceed 25,000g or the current pallet already has 8 parcels. A total of exactly 25,000g is valid.
* Charge a $2.50 base handling fee per processed non-hazardous parcel.
* Charge $1.25 for each 500g increment above 2,000g, rounded up to a whole increment.
* When the processed parcels have a combined declared value of at least $500.00, apply a 10% discount to base handling fees only.

## 3. Environment and Architecture

* Java: 21
* Spring Boot: 3.2.4
* Build tool: Maven 3.9+
* Database: none
* Test library: JUnit 5.10.2
* Entry point: Maven Surefire tests; there is no application `main` method.

```text
level-1/
├── README.md
├── pom.xml
└── src/
    ├── main/java/com/amazon/fulfillment/
    │   └── FulfillmentConsolidationService.java
    └── test/java/com/amazon/fulfillment/
        └── FulfillmentConsolidationServiceTest.java
```

No external services or application configuration are required.

Run from the repository root:

```bash
cd level-1
mvn test
```

## 4. Incident Report

**Incident ID:** INC-882104-FC
**Severity:** Sev-2

During a high-volume shift, finance identified invoices where parcels weighing slightly over 2,000g did not receive the expected overweight surcharge. For example, a 2,450g parcel was billed at the under-2,000g rate.

Fulfillment operations also reported premature pallet splits. A two-parcel batch totaling exactly 25,000g was split across two pallets, increasing freight linehaul costs and leaving partially empty containers.

## 5. Test Execution Report

Executed with `mvn test` on 2026-11-01: **5 tests run, 3 passed, 2 failed**.

### Passing Scenarios

**Standard parcel under 2,000g**

* Input: One non-hazardous 1,500g parcel with $100.00 declared value.
* Expected: One pallet, $0.00 surcharge, and a $2.50 net invoice.
* Observed: Matches expected.

**Exact surcharge increment**

* Input: One non-hazardous 3,000g parcel with $200.00 declared value.
* Expected: A $2.50 surcharge and a $5.00 net invoice.
* Observed: Matches expected.

**Hazardous parcel exclusion**

* Input: One eligible 1,200g parcel and one hazardous 2,500g parcel.
* Expected: One processed parcel, one pallet, and a $2.50 net invoice.
* Observed: Matches expected.

### Failing Scenarios

**Scenario 1: Fractional overweight parcel**

* Input: One eligible 2,450g parcel with $100.00 declared value.
* Expected: A $1.25 surcharge and a $3.75 net invoice.
* Actual: A $0.00 surcharge and a $2.50 net invoice.
* Reproduction: Reconcile a parcel whose excess weight above 2,000g is greater than zero but less than 500g.

**Scenario 2: Exact pallet weight limit**

* Input: Two parcels in ZIP prefix `981`, weighing 15,000g and 10,000g.
* Expected: One pallet totaling 25,000g.
* Actual: Two pallets are created.
* Reproduction: Reconcile the two parcels with no intervening capacity constraint.

## 6. Debugging Constraints

* Maximum alteration: 2 changed production lines.
* Preserve all class names, method signatures, records, and public behavior.
* Do not rewrite the consolidation algorithm or change the project architecture.
* Do not add dependencies.
* Additional focused tests are allowed.
* Do not hardcode incident values or special-case individual scenarios.
* The final patch should be the smallest production-safe correction.

## 7. Your Task

Identify each defect and submit findings incrementally or together using this format:

```text
Bug #1
* File:
* Class and method:
* Exact faulty statement or line:
* Root cause:
* Triggering conditions:
* Proposed minimal fix:
* Regression tests:
```

Your investigation starts now. Find the root cause before changing the code.
