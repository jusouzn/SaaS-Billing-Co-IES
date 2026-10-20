package com.saas.billing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenPrefixPaymentGatewayTest {

    private final TokenPrefixPaymentGateway gateway = new TokenPrefixPaymentGateway();

    @Test
    void approvesTokenWithLegacyPrefix() {
        assertTrue(gateway.charge("tok_card_123", Money.of("10.00")));
    }

    @Test
    void rejectsNullOrUnknownToken() {
        assertFalse(gateway.charge(null, Money.of("10.00")));
        assertFalse(gateway.charge("card_123", Money.of("10.00")));
    }
}
