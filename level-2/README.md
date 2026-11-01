# 🎮 LEVEL 2: Northstar Merchant Settlement Ledger

## 1. System Description

Northstar Bank reconciles merchant card activity from an upstream payment processor before depositing funds. `PaymentReconciliationService` creates a ledger for a UTC settlement window and summarizes payments and refunds by merchant and currency.

## 2. Business Requirements

* A settlement window includes `startInclusive` and excludes `endExclusive`.
* `providerTransactionId` is the processor's idempotency key. Include each provider transaction at most once.
* Redelivered provider events in the incident data have identical business fields and differ only in `ingestionBatch`.
* Trim and uppercase merchant account IDs and currency codes before aggregation.
* Keep merchant and currency totals independent. Payments increase net settlement; refunds reduce it.
* Amounts are positive minor currency units.
* Sort settlement rows by merchant account, then currency.
* `processedEventCount` is the number of unique business transactions in the report.

## 3. Environment and Architecture

* Java: 21
* Spring Boot: not used in this level
* Build tool: Maven 3.9+
* Database: none
* Test library: JUnit 5.10.2
* Entry point: Maven Surefire tests; there is no application `main` method.

```text
level-2/
├── README.md
├── pom.xml
└── src/
    ├── main/java/com/amazon/payments/
    │   └── PaymentReconciliationService.java
    └── test/java/com/amazon/payments/
        └── PaymentReconciliationServiceTest.java
```

No external services or application configuration are required.

Run from the repository root:

```bash
cd level-2
mvn test
```

## 4. Incident Report

**Incident ID:** PAY-RECON-204
**Severity:** Sev-3

The October 1 merchant settlement control failed during the close process. A transfer booked at `11:00:00Z` appeared in both the `10:00–11:00Z` ledger and the following ledger, inflating the daily deposit total.

Separately, Merchant South reported that a $125.00 transfer appeared as $250.00 after the processor retried delivery into a later ingestion batch. The downstream ledger contained two entries with the same provider transaction ID.

The control team requires a minimal correction without changing the public API or settlement semantics.

## 5. Test Execution Report

Executed with `mvn test` on 2026-11-01: **4 tests run, 2 passed, 2 failed**.

### Passing Scenarios

**Payments and refunds**

* Input: Merchant West has a 10,000 USD payment and a 3,500 USD refund in the settlement window.
* Expected: `paymentsMinor = 10,000`, `refundsMinor = 3,500`, `netMinor = 6,500`, `eventCount = 2`.
* Observed: Matches expected.

**Currency separation**

* Input: Merchant East has a 5,000 USD payment and a 1,000 EUR refund.
* Expected: Two settlement rows and total net `4,000`.
* Observed: Matches expected.

### Failing Scenarios

**Scenario 1: Settlement end boundary**

* Input: A 7,000 USD payment at `10:59:59Z` and an 8,000 USD payment at `11:00:00Z`; the window is `[10:00Z, 11:00Z)`.
* Expected: `processedEventCount = 1`, `totalNetMinor = 7,000`.
* Actual: `processedEventCount = 2`, `totalNetMinor = 15,000`.
* Reproduction: Reconcile data containing a transaction whose `settledAt` equals `endExclusive`.

**Scenario 2: Processor redelivery**

* Input: The same 12,500 USD provider transaction appears in `batch-primary` and `batch-redelivery`.
* Expected: `processedEventCount = 1`, `totalNetMinor = 12,500`.
* Actual: `processedEventCount = 2`, `totalNetMinor = 25,000`.
* Reproduction: Reconcile two otherwise-equal events with the same `providerTransactionId` but different `ingestionBatch` values.

## 6. Debugging Constraints

* Maximum alteration: 4 changed production lines.
* Preserve all class names, method signatures, records, and public behavior.
* Do not rewrite the reconciliation algorithm or change the project architecture.
* Do not add dependencies or database schema.
* Additional focused tests are allowed.
* Do not hardcode incident values or special-case either scenario.
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
