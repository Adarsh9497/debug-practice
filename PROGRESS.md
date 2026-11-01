# Debugging Simulator Progress

## Current Status

- Current Level: 3
- Completed Levels: 2
- Current Challenge: Level 3 — CareSlot Appointment Booking API
- Current Difficulty Tier: Spring Boot and API Debugging
- Overall Average Score: 69/100

## Completed Levels

| Level | Challenge | Score | Status | Defect Categories |
| --- | --- | ---: | --- | --- |
| 1 | Amazon Fulfillment & Pallet Consolidation | 68/100 | Completed | Integer division, capacity boundary, prospective-state validation |
| 2 | Merchant Settlement Ledger | 69/100 | Completed with follow-up | Half-open time windows, idempotency, collection key selection |

## Areas to Improve

- Pair exact-boundary tests with above-boundary tests so a partial boundary fix cannot pass the suite.
- Verify that capacity checks evaluate the prospective state after adding the next item.
- State the root cause and triggering conditions before presenting the patch.
- Keep production patches minimal; unrelated import changes increase review noise.
- Add or identify a regression test for each corrected invariant, not only for the supplied failing examples.
- Check every normalization clause in a business requirement, including whitespace around currency codes.
- Reduce working patches to the stated size before treating the implementation as complete.

## Strengths

- Correctly identified and fixed the integer-truncation issue affecting fractional surcharge units.
- Accepted review feedback and corrected the capacity comparison to use the prospective pallet weight.
- Preserved the public API and avoided hardcoding the incident values.
- Confirmed the corrected implementation against both the supplied suite and an over-capacity scenario.
- Correctly diagnosed and fixed the Level 2 time-window and provider-redelivery incidents.

## Learning History

### 2026-11-01 — Level 1 Completed

- Final verification: 5 of 5 tests passed.
- Additional capacity probe: 20,000g plus 10,000g produced two pallets with no overweight pallet.
- Stronger concept: explain and test the complete invariant, rather than only the reported boundary example.
- Weakness to revisit: use both exact-limit and over-limit regression cases when changing capacity logic.

### Next Focus

Level 2 completed with a follow-up: finalize currency-code whitespace normalization in [PaymentReconciliationService.java](level-2/src/main/java/com/amazon/payments/PaymentReconciliationService.java).

### 2026-11-02 — Level 3 Started

- Current focus: Spring MVC request binding, Bean Validation activation, interval overlap semantics, and problem-detail HTTP status mapping.
- New domain: healthcare appointment scheduling.
- Baseline verification: 6 MockMvc tests run, 3 passed and 3 failed as intended.
