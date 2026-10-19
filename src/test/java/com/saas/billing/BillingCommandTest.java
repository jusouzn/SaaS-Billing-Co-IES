package com.saas.billing;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BillingCommandTest {

    private BillingCommand comPromo(String promoCode) {
        return new BillingCommand("cus_1", "PRO", Money.of("100.00"), LocalDate.of(2025, 1, 10),
                0, promoCode, false, "tok_123");
    }

    @Test
    void identificaSeTemCupom() {
        assertTrue(comPromo("WELCOME10").hasPromoCode());
        assertFalse(comPromo(null).hasPromoCode());
        assertFalse(comPromo("   ").hasPromoCode());
    }

    @Test
    void exigeCamposObrigatorios() {
        assertThrows(NullPointerException.class, () -> new BillingCommand(null, "PRO",
                Money.of("100.00"), LocalDate.of(2025, 1, 10), 0, null, false, "tok_1"));
        assertThrows(NullPointerException.class, () -> new BillingCommand("cus_1", "PRO",
                null, LocalDate.of(2025, 1, 10), 0, null, false, "tok_1"));
        assertThrows(NullPointerException.class, () -> new BillingCommand("cus_1", "PRO",
                Money.of("100.00"), null, 0, null, false, "tok_1"));
    }
}
