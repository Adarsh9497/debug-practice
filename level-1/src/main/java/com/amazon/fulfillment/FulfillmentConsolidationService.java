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
