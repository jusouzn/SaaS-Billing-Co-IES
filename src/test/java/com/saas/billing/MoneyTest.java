package com.saas.billing;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void somaESubtrai() {
        Money valor = Money.of("100.00");

        assertEquals(Money.of("130.00"), valor.plus(Money.of("30.00")));
        assertEquals(Money.of("70.00"), valor.minus(Money.of("30.00")));
    }

    @Test
    void multiplicaPorFatorEPorInteiro() {
        Money valor = Money.of("100.00");

        assertEquals(Money.of("70.00"), valor.times(new BigDecimal("0.70")));
        assertEquals(Money.of("300.00"), valor.times(3));
    }

    @Test
    void naoTemErroDePontoFlutuante() {
        // com double: 0.1 + 0.2 = 0.30000000000000004
        Money soma = Money.of(0.1).plus(Money.of(0.2));

        assertEquals(new BigDecimal("0.3"), soma.amount());
    }

    @Test
    void arredondaParaCentavosComHalfUp() {
        assertEquals(new BigDecimal("10.13"), Money.of("10.125").rounded());
        assertEquals(new BigDecimal("10.12"), Money.of("10.124").rounded());
        assertEquals("76.50", Money.of("76.4999999").toString());
    }

    @Test
    void valoresIguaisComEscalasDiferentesSaoIguais() {
        assertEquals(Money.of("10.0"), Money.of("10.00"));
        assertEquals(Money.of("10.0").hashCode(), Money.of("10.00").hashCode());
    }

    @Test
    void ehImutavel() {
        Money original = Money.of("50.00");

        original.plus(Money.of("10.00"));
        original.times(2);

        assertEquals(Money.of("50.00"), original);
    }

    @Test
    void detectaValorNegativo() {
        assertTrue(Money.of("-0.01").isNegative());
        assertFalse(Money.ZERO.isNegative());
    }

    @Test
    void naoAceitaValorNulo() {
        assertThrows(NullPointerException.class, () -> new Money(null));
    }
}
