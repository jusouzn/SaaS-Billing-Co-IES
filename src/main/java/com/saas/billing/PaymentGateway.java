package com.saas.billing;

public interface PaymentGateway {

    boolean charge(String paymentCardToken, Money amount);
}
