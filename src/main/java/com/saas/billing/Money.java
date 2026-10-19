package com.saas.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount) {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(amount);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money times(BigDecimal factor) {
        return new Money(amount.multiply(factor));
    }

    public Money times(long multiplier) {
        return times(BigDecimal.valueOf(multiplier));
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    // arredonda só na saída para não acumular erro nas contas intermediárias
    public BigDecimal rounded() {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money other && rounded().equals(other.rounded());
    }

    @Override
    public int hashCode() {
        return rounded().hashCode();
    }

    @Override
    public String toString() {
        return rounded().toPlainString();
    }
}
