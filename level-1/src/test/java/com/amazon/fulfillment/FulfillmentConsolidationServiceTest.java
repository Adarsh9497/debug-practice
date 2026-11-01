package com.amazon.fulfillment;

import com.amazon.fulfillment.FulfillmentConsolidationService.OrderParcel;
import com.amazon.fulfillment.FulfillmentConsolidationService.Priority;
import com.amazon.fulfillment.FulfillmentConsolidationService.ConsolidationSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FulfillmentConsolidationServiceTest {

    private FulfillmentConsolidationService service;

    @BeforeEach
    void setUp() {
        service = new FulfillmentConsolidationService();
    }

    @Test
    @DisplayName("PASSING: Standard parcel under 2000g receives zero surcharge")
    void testStandardParcelUnder2000g() {
        OrderParcel parcel = new OrderParcel("P-101", "98101", 1500, Priority.STANDARD, false, new BigDecimal("100.00"));
        ConsolidationSummary summary = service.processShipmentBatch(List.of(parcel));

        assertEquals(1, summary.totalProcessedParcels());
        assertEquals(1, summary.totalPalletsCreated());
        assertEquals(new BigDecimal("2.50"), summary.totalBaseFee());
        assertEquals(new BigDecimal("0.00"), summary.totalSurcharges());
        assertEquals(new BigDecimal("0.00"), summary.volumeDiscount());
        assertEquals(new BigDecimal("2.50"), summary.netInvoiceAmount());
    }

    @Test
    @DisplayName("PASSING: Parcel with exact 1000g excess receives exact $2.50 surcharge")
    void testParcelWithExactExcessMultiple() {
        OrderParcel parcel = new OrderParcel("P-102", "98102", 3000, Priority.HIGH, false, new BigDecimal("200.00"));
        ConsolidationSummary summary = service.processShipmentBatch(List.of(parcel));

        assertEquals(1, summary.totalProcessedParcels());
        assertEquals(new BigDecimal("2.50"), summary.totalBaseFee());
        assertEquals(new BigDecimal("2.50"), summary.totalSurcharges());
        assertEquals(new BigDecimal("5.00"), summary.netInvoiceAmount());
    }

    @Test
    @DisplayName("PASSING: Hazardous materials are excluded from standard consolidation")
    void testHazardousParcelsExcluded() {
        OrderParcel standard = new OrderParcel("P-103", "98103", 1200, Priority.MEDIUM, false, new BigDecimal("50.00"));
        OrderParcel hazardous = new OrderParcel("P-104", "98103", 2500, Priority.HIGH, true, new BigDecimal("150.00"));
        ConsolidationSummary summary = service.processShipmentBatch(List.of(standard, hazardous));

        assertEquals(1, summary.totalProcessedParcels());
        assertEquals(1, summary.totalPalletsCreated());
        assertEquals(new BigDecimal("2.50"), summary.netInvoiceAmount());
    }

    @Test
    @DisplayName("FAILING: Fractional overweight parcel (2450g) should incur $1.25 surcharge")
    void testFractionalOverweightSurcharge() {
        OrderParcel parcel = new OrderParcel("P-201", "98101", 2450, Priority.HIGH, false, new BigDecimal("100.00"));
        BigDecimal surcharge = service.calculateParcelSurcharge(parcel);
        // Expected: 450g excess -> ceil(450 / 500.0) = 1 unit -> $1.25
        // Actual (broken): (450 / 500) = 0 -> $0.00
        assertEquals(new BigDecimal("1.25"), surcharge);
    }

    @Test
    @DisplayName("FAILING: Parcels totaling exactly 25,000g should consolidate into a single pallet")
    void testPalletCapacityBoundaryAt25000g() {
        OrderParcel parcel1 = new OrderParcel("P-301", "98101", 15000, Priority.HIGH, false, new BigDecimal("50.00"));
        OrderParcel parcel2 = new OrderParcel("P-302", "98101", 10000, Priority.MEDIUM, false, new BigDecimal("50.00"));
        ConsolidationSummary summary = service.processShipmentBatch(List.of(parcel1, parcel2));

        // Expected: 15,000g + 10,000g = 25,000g <= MAX_PALLET_WEIGHT_GRAMS -> 1 Pallet
        // Actual (broken): 25000 >= 25000 triggers new pallet -> 2 Pallets
        assertEquals(1, summary.totalPalletsCreated());
    }
}
