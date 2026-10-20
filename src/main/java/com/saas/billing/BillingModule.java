package com.saas.billing;

import java.time.Clock;
import java.util.Objects;
import javax.sql.DataSource;

public final class BillingModule {

    private BillingModule() {
    }

    public static SubscriptionBillingService create(
            DataSource dataSource,
            String smtpHost,
            int smtpPort) {
        return create(dataSource, smtpHost, smtpPort, Clock.systemDefaultZone());
    }

    public static SubscriptionBillingService create(
            DataSource dataSource,
            String smtpHost,
            int smtpPort,
            Clock clock) {
        Objects.requireNonNull(dataSource);
        Objects.requireNonNull(clock);
        return new SubscriptionBillingService(
                new JdbcCustomerRepository(dataSource),
                new JdbcInvoiceRepository(dataSource),
                new TokenPrefixPaymentGateway(),
                new SmtpNotificationService(smtpHost, smtpPort),
                clock);
    }
}
