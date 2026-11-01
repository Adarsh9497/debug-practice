package com.amazon.payments;

import com.amazon.payments.PaymentReconciliationService.EventType;
import com.amazon.payments.PaymentReconciliationService.MerchantSettlement;
import com.amazon.payments.PaymentReconciliationService.PaymentEvent;
import com.amazon.payments.PaymentReconciliationService.SettlementReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentReconciliationServiceTest {

    private static final Instant WINDOW_START = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-10-01T11:00:00Z");

    private PaymentReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentReconciliationService();
    }

    @Test
    @DisplayName("PASSING: payments and refunds are summarized by merchant")
    void summarizesPaymentsAndRefunds() {
        List<PaymentEvent> events = List.of(
                event("txn-100", " merchant-west ", "2026-10-01T10:12:00Z", 10_000, "usd",
                        EventType.PAYMENT, "batch-a"),
                event("txn-101", "MERCHANT-WEST", "2026-10-01T10:22:00Z", 3_500, "USD",
                        EventType.REFUND, "batch-a")
        );

        SettlementReport report = service.reconcile(events, WINDOW_START, WINDOW_END);

        assertEquals(2, report.processedEventCount());
        assertEquals(6_500, report.totalNetMinor());
        assertEquals(new MerchantSettlement("MERCHANT-WEST", "USD", 10_000, 3_500, 6_500, 2),
                report.merchants().getFirst());
    }

    @Test
    @DisplayName("PASSING: currencies are reported independently")
    void keepsCurrenciesSeparate() {
        List<PaymentEvent> events = List.of(
                event("txn-200", "MERCHANT-EAST", "2026-10-01T10:10:00Z", 5_000, "USD",
                        EventType.PAYMENT, "batch-b"),
                event("txn-201", "MERCHANT-EAST", "2026-10-01T10:20:00Z", 1_000, "EUR",
                        EventType.REFUND, "batch-b")
        );

        SettlementReport report = service.reconcile(events, WINDOW_START, WINDOW_END);

        assertEquals(2, report.merchants().size());
        assertEquals(4_000, report.totalNetMinor());
    }

    @Test
    @DisplayName("FAILING: the settlement window excludes an event at its end instant")
    void endInstantIsExclusive() {
        List<PaymentEvent> events = List.of(
                event("txn-300", "MERCHANT-NORTH", "2026-10-01T10:59:59Z", 7_000, "USD",
                        EventType.PAYMENT, "batch-c"),
                event("txn-301", "MERCHANT-NORTH", "2026-10-01T11:00:00Z", 8_000, "USD",
                        EventType.PAYMENT, "batch-d")
        );

        SettlementReport report = service.reconcile(events, WINDOW_START, WINDOW_END);

        assertEquals(1, report.processedEventCount());
        assertEquals(7_000, report.totalNetMinor());
    }

    @Test
    @DisplayName("FAILING: a provider transaction redelivered in another batch is counted once")
    void redeliveredTransactionIsIdempotent() {
        List<PaymentEvent> events = List.of(
                event("txn-400", "MERCHANT-SOUTH", "2026-10-01T10:30:00Z", 12_500, "USD",
                        EventType.PAYMENT, "batch-primary"),
                event("txn-400", "MERCHANT-SOUTH", "2026-10-01T10:30:00Z", 12_500, "USD",
                        EventType.PAYMENT, "batch-redelivery")
        );

        SettlementReport report = service.reconcile(events, WINDOW_START, WINDOW_END);

        assertEquals(1, report.processedEventCount());
        assertEquals(12_500, report.totalNetMinor());
    }

    private PaymentEvent event(
            String transactionId,
            String merchant,
            String settledAt,
            long amountMinor,
            String currency,
            EventType type,
            String ingestionBatch
    ) {
        return new PaymentEvent(transactionId, merchant, Instant.parse(settledAt), amountMinor,
                currency, type, ingestionBatch);
    }
}
