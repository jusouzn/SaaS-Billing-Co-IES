package com.saas.billing;

import java.time.Instant;

public interface InvoiceRepository {

    void saveInvoice(String customerId, Money amount, InvoiceStatus status, Instant createdAt);
}
