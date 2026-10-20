package com.saas.billing;

public class TokenPrefixPaymentGateway implements PaymentGateway {

    @Override
    public boolean charge(String paymentCardToken, Money amount) {
        return paymentCardToken != null && paymentCardToken.startsWith("tok_");
    }
}
