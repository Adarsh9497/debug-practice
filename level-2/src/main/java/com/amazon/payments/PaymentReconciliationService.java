package com.amazon.payments;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class PaymentReconciliationService {

    public enum EventType {
        PAYMENT,
        REFUND
    }

    public record PaymentEvent(
            String providerTransactionId,
            String merchantAccount,
            Instant settledAt,
            long amountMinor,
            String currency,
            EventType type,
            String ingestionBatch
    ) {}

    public record MerchantSettlement(
            String merchantAccount,
            String currency,
            long paymentsMinor,
            long refundsMinor,
            long netMinor,
            int eventCount
    ) {}

    public record SettlementReport(
            List<MerchantSettlement> merchants,
            long totalNetMinor,
            int processedEventCount
    ) {}

    private record MerchantCurrency(String merchantAccount, String currency) {}

    /**
     * Produces the settlement ledger for events in the half-open window
     * [startInclusive, endExclusive).
     */
    public SettlementReport reconcile(
            List<PaymentEvent> events,
            Instant startInclusive,
            Instant endExclusive
    ) {
        Objects.requireNonNull(startInclusive, "startInclusive");
        Objects.requireNonNull(endExclusive, "endExclusive");
        if (!startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("Settlement window must have positive duration");
        }
        if (events == null || events.isEmpty()) {
            return new SettlementReport(List.of(), 0L, 0);
        }

        List<PaymentEvent> inWindow = events.stream()
                .filter(Objects::nonNull)
                .filter(event -> !event.settledAt().isBefore(startInclusive))
                .filter(event -> !event.settledAt().isAfter(endExclusive))
                .distinct()
                .toList();

        Map<MerchantCurrency, List<PaymentEvent>> grouped = inWindow.stream()
                .collect(Collectors.groupingBy(event -> new MerchantCurrency(
                        event.merchantAccount().trim().toUpperCase(Locale.ROOT),
                        event.currency().toUpperCase(Locale.ROOT)
                )));

        List<MerchantSettlement> settlements = new ArrayList<>();
        for (Map.Entry<MerchantCurrency, List<PaymentEvent>> entry : grouped.entrySet()) {
            List<PaymentEvent> merchantEvents = entry.getValue();
            long payments = sumByType(merchantEvents, EventType.PAYMENT);
            long refunds = sumByType(merchantEvents, EventType.REFUND);
            settlements.add(new MerchantSettlement(
                    entry.getKey().merchantAccount(),
                    entry.getKey().currency(),
                    payments,
                    refunds,
                    payments - refunds,
                    merchantEvents.size()
            ));
        }

        settlements.sort((left, right) -> {
            int merchantOrder = left.merchantAccount().compareTo(right.merchantAccount());
            return merchantOrder != 0
                    ? merchantOrder
                    : left.currency().compareTo(right.currency());
        });

        long totalNetMinor = settlements.stream()
                .mapToLong(MerchantSettlement::netMinor)
                .sum();
        int processedEventCount = settlements.stream()
                .mapToInt(MerchantSettlement::eventCount)
                .sum();

        return new SettlementReport(List.copyOf(settlements), totalNetMinor, processedEventCount);
    }

    private long sumByType(List<PaymentEvent> events, EventType type) {
        return events.stream()
                .filter(event -> event.type() == type)
                .mapToLong(PaymentEvent::amountMinor)
                .sum();
    }
}
