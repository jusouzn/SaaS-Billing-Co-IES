package com.saas.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PricingCalculator {

    private static final BigDecimal LOYALTY_DISCOUNT_2_YEARS = new BigDecimal("0.15");
    private static final BigDecimal LOYALTY_DISCOUNT_1_YEAR = new BigDecimal("0.10");
    private static final BigDecimal NON_PROFIT_FACTOR = new BigDecimal("0.70");
    private static final Money BLACKFRIDAY_DISCOUNT = Money.of("30.00");
    private static final BigDecimal WELCOME10_FACTOR = new BigDecimal("0.90");
    private static final BigDecimal LATE_PENALTY_RATE = new BigDecimal("0.02");
    private static final BigDecimal DAILY_INTEREST_RATE = new BigDecimal("0.001");

    public Money calculate(BillingCommand command, LocalDate today) {
        int monthsActive = monthsActive(command.signupDate(), today);

        Money price = applyLoyaltyDiscount(command.basePrice(), monthsActive);
        if (command.nonProfit()) {
            price = price.times(NON_PROFIT_FACTOR);
        }
        price = applyPromoCode(price, command, today, monthsActive);
        price = applyLateFees(price, command.overdueDays());

        return price.isNegative() ? Money.ZERO : price;
    }

    // conta só a diferença de meses do calendário, ignorando o dia (igual ao legado)
    private int monthsActive(LocalDate signupDate, LocalDate today) {
        return (today.getYear() - signupDate.getYear()) * 12
                + (today.getMonthValue() - signupDate.getMonthValue());
    }

    private Money applyLoyaltyDiscount(Money basePrice, int monthsActive) {
        if (monthsActive > 24) {
            return basePrice.minus(basePrice.times(LOYALTY_DISCOUNT_2_YEARS));
        }
        if (monthsActive > 12) {
            return basePrice.minus(basePrice.times(LOYALTY_DISCOUNT_1_YEAR));
        }
        return basePrice;
    }

    private Money applyPromoCode(Money price, BillingCommand command, LocalDate today, int monthsActive) {
        if (!command.hasPromoCode()) {
            return price;
        }
        String promoCode = command.promoCode();
        if (promoCode.equalsIgnoreCase("BLACKFRIDAY") && today.getMonthValue() == 11) {
            return price.minus(BLACKFRIDAY_DISCOUNT);
        }
        if (promoCode.equalsIgnoreCase("WELCOME10") && monthsActive <= 1) {
            return price.times(WELCOME10_FACTOR);
        }
        return price;
    }

    private Money applyLateFees(Money price, int overdueDays) {
        if (overdueDays <= 0) {
            return price;
        }
        Money penalty = price.times(LATE_PENALTY_RATE);
        Money interest = price.times(DAILY_INTEREST_RATE).times(overdueDays);
        return price.plus(penalty).plus(interest);
    }
}
