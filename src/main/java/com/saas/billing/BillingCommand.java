package com.saas.billing;

import java.time.LocalDate;
import java.util.Objects;

public record BillingCommand(
        String customerId,
        String planType,
        Money basePrice,
        LocalDate signupDate,
        int overdueDays,
        String promoCode,
        boolean nonProfit,
        String paymentCardToken) {

    public BillingCommand {
        Objects.requireNonNull(customerId);
        Objects.requireNonNull(basePrice);
        Objects.requireNonNull(signupDate);
    }

    public boolean hasPromoCode() {
        return promoCode != null && !promoCode.trim().isEmpty();
    }
}
