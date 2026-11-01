# 🎮 LEVEL 1: Amazon Fulfillment & Outbound Pallet Consolidation Engine

## 1. System Description
At Amazon Fulfillment Centers (FC), outbound customer shipments are batched and consolidated into standardized pallet containers before transfer to regional sortation centers and last-mile delivery dispatch.

The `FulfillmentConsolidationService` performs automated batch consolidation and billing manifest calculation under the following core business requirements and invariants:
* **Hazmat Segregation**: Any order parcel marked as hazardous material (`isHazardous == true`) requires specialized Hazmat transport protocols and must be excluded from standard pallet consolidation.
* **Regional Destination Clustering**: Parcels are grouped by destination region based on the first 3 digits of the recipient postal ZIP code (e.g., `"98101"` belongs to regional prefix `"981"`).
* **Consolidation Priority & Packing Order**:
  * Within each regional cluster, parcels must be processed in order of priority: `HIGH` (rank 1) $\rightarrow$ `MEDIUM` (rank 2) $\rightarrow$ `STANDARD` (rank 3).
  * For parcels with identical priority, they must be packed in ascending order of parcel weight (`weightGrams`) so lighter packages are placed safely on upper tiers.
* **Pallet Allocation Invariants**:
  * Each pallet can hold up to a maximum weight capacity of **25,000g** (25kg) and up to **8 parcels**.
  * A pallet is closed and sealed, and a new pallet is opened, **only when adding the next parcel would exceed 25,000g** or when the pallet has already reached 8 parcels. Parcels that bring the total weight to exactly 25,000g are valid and must remain on the current pallet.
* **Billing & Surcharge Invoicing Invariants**:
  * **Base Handling Fee**: `$2.50` flat per processed non-hazardous parcel.
  * **Overweight Surcharge**: Parcels weighing **over 2,000g** incur a surcharge of `$1.25` for every 500g increment (or fraction thereof) above 2,000g. (e.g., 2,001g–2,500g = 1 unit = $1.25; 2,501g–3,000g = 2 units = $2.50).
  * **Commercial Volume Discount**: If the total declared value across all processed parcels in the shipment batch is $\ge$ **$500.00**, a **10% discount** is deducted from the total base handling fee (surcharges are not discounted).

---

## 2. Environment and Architecture
* **Java Version**: 21 (LTS)
* **Spring Boot Version**: 3.2.4
* **Build Tool**: Maven 3.9+
* **Project Structure**:
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
* **Execution & Verification**:
  Run tests directly using Maven:
  ```bash
  cd level-1
  mvn test
  ```

---

## 3. Incident Report
**Incident ID**: INC-882104-FC  
**Severity**: Sev-2 (Production Revenue & Operations Anomaly)  
**Reported Symptoms**:
1. Finance reported that during recent high-volume shifts, billing invoices for several parcels weighing slightly over 2,000g (e.g., 2,450g) failed to assess the required overweight surcharge, resulting in uncollected fees and an invoice discrepancy.
2. Fulfillment floor automation reported premature dispatch triggers: pallets loaded with exactly 25,000g total weight triggered premature pallet split events, resulting in partially empty pallets and higher freight linehaul costs.

---

## 4. Complete Codebase

### File: `level-1/src/main/java/com/amazon/fulfillment/FulfillmentConsolidationService.java`
```java
package com.amazon.fulfillment;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FulfillmentConsolidationService {

    public static final int MAX_PALLET_WEIGHT_GRAMS = 25_000; // 25 kg
    public static final int MAX_ITEMS_PER_PALLET = 8;
    public static final BigDecimal BASE_HANDLING_FEE = new BigDecimal("2.50");
    public static final BigDecimal SURCHARGE_PER_UNIT = new BigDecimal("1.25");
    public static final BigDecimal VOLUME_DISCOUNT_THRESHOLD = new BigDecimal("500.00");
    public static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.10"); // 10%

    public enum Priority {
        HIGH(1),
        MEDIUM(2),
        STANDARD(3);

        private final int rank;

        Priority(int rank) {
            this.rank = rank;
        }

        public int getRank() {
            return rank;
        }
    }

    public record OrderParcel(
            String parcelId,
            String destinationZip,
            int weightGrams,
            Priority priority,
            boolean isHazardous,
            BigDecimal declaredValue
    ) {}

    public static class Pallet {
        private final String palletId;
        private final String regionPrefix;
        private final List<OrderParcel> items = new ArrayList<>();
        private int totalWeightGrams = 0;

        public Pallet(String regionPrefix) {
            this.palletId = "PLT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            this.regionPrefix = regionPrefix;
        }

        public void addParcel(OrderParcel parcel) {
            items.add(parcel);
            totalWeightGrams += parcel.weightGrams();
        }

        public String getPalletId() {
            return palletId;
        }

        public String getRegionPrefix() {
            return regionPrefix;
        }

        public List<OrderParcel> getItems() {
            return Collections.unmodifiableList(items);
        }

        public int getItemCount() {
            return items.size();
        }

        public int getTotalWeightGrams() {
            return totalWeightGrams;
        }
    }

    public record ConsolidationSummary(
            int totalProcessedParcels,
            int totalPalletsCreated,
            List<Pallet> pallets,
            BigDecimal totalBaseFee,
            BigDecimal totalSurcharges,
            BigDecimal volumeDiscount,
            BigDecimal netInvoiceAmount
    ) {}

    /**
     * Consolidates and manifests an inbound batch of orders for fulfillment dispatch.
     */
    public ConsolidationSummary processShipmentBatch(List<OrderParcel> parcels) {
        if (parcels == null || parcels.isEmpty()) {
            return new ConsolidationSummary(0, 0, List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        // 1. Filter out hazardous parcels (routed via specialized Hazmat network)
        List<OrderParcel> eligibleParcels = parcels.stream()
                .filter(parcel -> !parcel.isHazardous())
                .collect(Collectors.toList());

        // 2. Group by 3-digit regional zip prefix
        Map<String, List<OrderParcel>> parcelsByRegion = eligibleParcels.stream()
                .collect(Collectors.groupingBy(p -> p.destinationZip().substring(0, 3)));

        // 3. For each region, sort and pack into pallets
        List<Pallet> totalPallets = new ArrayList<>();
        Comparator<OrderParcel> parcelComparator = Comparator
                .comparingInt((OrderParcel p) -> p.priority().getRank())
                .thenComparingInt(OrderParcel::weightGrams);

        for (Map.Entry<String, List<OrderParcel>> entry : parcelsByRegion.entrySet()) {
            String regionPrefix = entry.getKey();
            List<OrderParcel> sortedParcels = entry.getValue().stream()
                    .sorted(parcelComparator)
                    .toList();

            totalPallets.addAll(packParcelsIntoPallets(regionPrefix, sortedParcels));
        }

        // 4. Financial billing calculations
        BigDecimal totalBaseFee = BASE_HANDLING_FEE.multiply(BigDecimal.valueOf(eligibleParcels.size()))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalSurcharges = eligibleParcels.stream()
                .map(this::calculateParcelSurcharge)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalDeclaredValue = eligibleParcels.stream()
                .map(OrderParcel::declaredValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal volumeDiscount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (totalDeclaredValue.compareTo(VOLUME_DISCOUNT_THRESHOLD) >= 0) {
            volumeDiscount = totalBaseFee.multiply(DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal netInvoiceAmount = totalBaseFee.subtract(volumeDiscount)
                .add(totalSurcharges)
                .setScale(2, RoundingMode.HALF_UP);

        return new ConsolidationSummary(
                eligibleParcels.size(),
                totalPallets.size(),
                totalPallets,
                totalBaseFee,
                totalSurcharges,
                volumeDiscount,
                netInvoiceAmount
        );
    }

    /**
     * Allocates parcels into pallets respecting maximum weight and item limits.
     */
    private List<Pallet> packParcelsIntoPallets(String regionPrefix, List<OrderParcel> parcels) {
        List<Pallet> pallets = new ArrayList<>();
        Pallet currentPallet = new Pallet(regionPrefix);

        for (OrderParcel parcel : parcels) {
            // Check if adding this parcel exceeds capacity limits
            if (currentPallet.getTotalWeightGrams() + parcel.weightGrams() >= MAX_PALLET_WEIGHT_GRAMS
                    || currentPallet.getItemCount() == MAX_ITEMS_PER_PALLET) {
                if (!currentPallet.getItems().isEmpty()) {
                    pallets.add(currentPallet);
                    currentPallet = new Pallet(regionPrefix);
                }
            }
            currentPallet.addParcel(parcel);
        }

        if (!currentPallet.getItems().isEmpty()) {
            pallets.add(currentPallet);
        }

        return pallets;
    }

    /**
     * Calculates overweight surcharge:
     * First 2,000g has no surcharge.
     * Each 500g increment (or fraction thereof) above 2,000g incurs $1.25 surcharge.
     */
    public BigDecimal calculateParcelSurcharge(OrderParcel parcel) {
        if (parcel.weightGrams() <= 2000) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        int excessWeight = parcel.weightGrams() - 2000;
        int billableUnits = (int) Math.ceil(excessWeight / 500);

        return SURCHARGE_PER_UNIT.multiply(BigDecimal.valueOf(billableUnits))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
```

---

## 5. Test Execution Report
*(Executed using `mvn test` in `level-1`)*

### Passing Scenarios
* **Scenario A: Standard parcel under 2,000g**
  * **Input**: 1 parcel (`1,500g`, `declaredValue = $100.00`, `isHazardous = false`, `destinationZip = "98101"`).
  * **Expected Result**: Surcharge = `$0.00`, Net Invoice = `$2.50`, Pallet Count = `1`.
  * **Observed Result**: Matches expected.
* **Scenario B: Parcel with exact 1,000g excess**
  * **Input**: 1 parcel (`3,000g`, `declaredValue = $200.00`, `isHazardous = false`, `destinationZip = "98102"`).
  * **Expected Result**: Excess = 1,000g (exact 2 units), Surcharge = `$2.50`, Net Invoice = `$5.00`.
  * **Observed Result**: Matches expected.
* **Scenario C: Exclusion of Hazmat parcels**
  * **Input**: 1 standard parcel (`1,200g`) and 1 hazardous parcel (`2,500g`, `isHazardous = true`).
  * **Expected Result**: 1 parcel processed, 1 pallet created, net invoice = `$2.50`.
  * **Observed Result**: Matches expected.

### Failing Scenarios
* **Scenario 1: Fractional overweight parcel surcharge**
  * **Input**: 1 parcel with `weightGrams = 2,450g`, `destinationZip = "98101"`, `isHazardous = false`.
  * **Expected Result**:
    * Excess weight: $2,450\text{g} - 2,000\text{g} = 450\text{g}$.
    * Any fraction of 500g incurs 1 billable unit ($1 \times \$1.25 = \mathbf{\$1.25}$).
    * Expected Surcharge: **`$1.25`**
  * **Actual Broken Result**:
    * Actual Surcharge: **`$0.00`**
  * **Reproduction**: Triggered when `(parcel.weightGrams() - 2000) % 500 != 0` and excess is less than 500g.

* **Scenario 2: Pallet capacity boundary condition at exact limit**
  * **Input**: 2 parcels in region `"981"`:
    * Parcel 1: `weightGrams = 15,000g`
    * Parcel 2: `weightGrams = 10,000g`
  * **Expected Result**:
    * Total combined weight = $15,000\text{g} + 10,000\text{g} = 25,000\text{g} \le \text{MAX\_PALLET\_WEIGHT\_GRAMS}$.
    * Both parcels consolidate into a single pallet.
    * Expected Pallet Count: **`1`**
  * **Actual Broken Result**:
    * Actual Pallet Count: **`2`**
  * **Reproduction**: Triggered when cumulative pallet weight plus next parcel weight exactly equals `25,000g`.

---

## 6. Debugging Constraints
* **Maximum Line Alterations**: Maximum **2 lines total** across the entire codebase (1 single-line fix per defect).
* **Public API & Contracts**: Do not change method signatures, class names, or public record contracts.
* **No Rewrite**: You cannot rewrite the algorithms or change control flow architecture.
* **Strict Root Cause**: Do not use ad-hoc hardcoded values or bypass calculations to satisfy single test inputs.

---

## 7. Your Task
Identify each defect and submit your findings using this format:

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
