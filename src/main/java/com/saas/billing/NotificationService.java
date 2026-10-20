package com.saas.billing;

public interface NotificationService {

    void sendInvoiceReceipt(String email, Money amount);
}
