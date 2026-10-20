package com.saas.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SubscriptionBillingServiceTest {

    private static final Instant NOW = Instant.parse("2025-11-15T12:00:00Z");

    @Test
    void processesBlackFridayUsingInjectedClockAndPorts() {
        InMemoryCustomerRepository customers = new InMemoryCustomerRepository(
                Optional.of(new Customer("customer@example.com", "ACTIVE")));
        InMemoryInvoiceRepository invoices = new InMemoryInvoiceRepository();
        RecordingPaymentGateway payments = new RecordingPaymentGateway(true);
        RecordingNotificationService notifications = new RecordingNotificationService();
        SubscriptionBillingService service = service(customers, invoices, payments, notifications);
        BillingCommand command = command("BLACKFRIDAY", 0);

        List<String> logs = service.processMonthlyBilling(command);

        assertEquals(Money.of("70.00"), payments.chargedAmount);
        assertEquals(Money.of("70.00"), invoices.savedAmount);
        assertEquals(InvoiceStatus.PAID, invoices.savedStatus);
        assertEquals(NOW, invoices.savedAt);
        assertEquals("customer@example.com", notifications.recipient);
        assertEquals(Money.of("70.00"), notifications.amount);
        assertEquals(2, logs.size());
        assertTrue(logs.get(0).contains("Pagamento processado com sucesso"));
        assertTrue(logs.get(1).contains("E-mail disparado"));
    }

    @Test
    void inactiveCustomerIsNotChargedOrNotified() {
        InMemoryCustomerRepository customers = new InMemoryCustomerRepository(
                Optional.of(new Customer("customer@example.com", "INACTIVE")));
        InMemoryInvoiceRepository invoices = new InMemoryInvoiceRepository();
        RecordingPaymentGateway payments = new RecordingPaymentGateway(true);
        RecordingNotificationService notifications = new RecordingNotificationService();
        SubscriptionBillingService service = service(customers, invoices, payments, notifications);

        List<String> logs = service.processMonthlyBilling(command(null, 0));

        assertEquals(1, logs.size());
        assertTrue(logs.get(0).contains("Cliente inativo"));
        assertEquals(0, payments.calls);
        assertEquals(0, invoices.invoiceCount);
        assertNull(notifications.recipient);
    }

    @Test
    void customerWithNullStatusIsInactive() {
        InMemoryCustomerRepository customers = new InMemoryCustomerRepository(
                Optional.of(new Customer("customer@example.com", null)));
        InMemoryInvoiceRepository invoices = new InMemoryInvoiceRepository();
        RecordingPaymentGateway payments = new RecordingPaymentGateway(true);
        RecordingNotificationService notifications = new RecordingNotificationService();
        SubscriptionBillingService service = service(customers, invoices, payments, notifications);

        List<String> logs = service.processMonthlyBilling(command(null, 0));

        assertEquals(1, logs.size());
        assertTrue(logs.get(0).contains("Cliente inativo"));
        assertEquals(0, payments.calls);
        assertEquals(0, invoices.invoiceCount);
        assertNull(notifications.recipient);
    }

    @Test
    void failedPaymentStillPersistsInvoiceAndSendsReceipt() {
        InMemoryCustomerRepository customers = new InMemoryCustomerRepository(
                Optional.of(new Customer("customer@example.com", "ACTIVE")));
        InMemoryInvoiceRepository invoices = new InMemoryInvoiceRepository();
        RecordingPaymentGateway payments = new RecordingPaymentGateway(false);
        RecordingNotificationService notifications = new RecordingNotificationService();
        SubscriptionBillingService service = service(customers, invoices, payments, notifications);

        List<String> logs = service.processMonthlyBilling(command(null, 0));

        assertEquals(InvoiceStatus.FAILED, invoices.savedStatus);
        assertEquals(1, invoices.invoiceCount);
        assertEquals("customer@example.com", notifications.recipient);
        assertTrue(logs.get(0).contains("Falha no pagamento"));
        assertTrue(logs.get(1).contains("E-mail disparado"));
    }

    @Test
    void missingCustomerDoesNotChargeOrCreateInvoice() {
        InMemoryCustomerRepository customers = new InMemoryCustomerRepository(Optional.empty());
        InMemoryInvoiceRepository invoices = new InMemoryInvoiceRepository();
        RecordingPaymentGateway payments = new RecordingPaymentGateway(true);
        RecordingNotificationService notifications = new RecordingNotificationService();
        SubscriptionBillingService service = service(customers, invoices, payments, notifications);

        List<String> logs = service.processMonthlyBilling(command(null, 0));

        assertTrue(logs.isEmpty());
        assertEquals(0, payments.calls);
        assertEquals(0, invoices.invoiceCount);
        assertNull(notifications.recipient);
    }

    private SubscriptionBillingService service(
            InMemoryCustomerRepository customers,
            InMemoryInvoiceRepository invoices,
            RecordingPaymentGateway payments,
            RecordingNotificationService notifications) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new SubscriptionBillingService(customers, invoices, payments, notifications, clock);
    }

    private BillingCommand command(String promoCode, int overdueDays) {
        return new BillingCommand("customer-1", "PRO", Money.of("100.00"),
                LocalDate.of(2025, 11, 1), overdueDays, promoCode, false, "tok_test");
    }

    private static class InMemoryCustomerRepository implements CustomerRepository {
        private final Optional<Customer> customer;

        private InMemoryCustomerRepository(Optional<Customer> customer) {
            this.customer = customer;
        }

        @Override
        public Optional<Customer> findById(String customerId) {
            return customer;
        }
    }

    private static class InMemoryInvoiceRepository implements InvoiceRepository {
        private int invoiceCount;
        private Money savedAmount;
        private InvoiceStatus savedStatus;
        private Instant savedAt;

        @Override
        public void saveInvoice(String customerId, Money amount, InvoiceStatus status, Instant createdAt) {
            invoiceCount++;
            savedAmount = amount;
            savedStatus = status;
            savedAt = createdAt;
        }
    }

    private static class RecordingPaymentGateway implements PaymentGateway {
        private final boolean result;
        private int calls;
        private Money chargedAmount;

        private RecordingPaymentGateway(boolean result) {
            this.result = result;
        }

        @Override
        public boolean charge(String paymentCardToken, Money amount) {
            calls++;
            chargedAmount = amount;
            return result;
        }
    }

    private static class RecordingNotificationService implements NotificationService {
        private String recipient;
        private Money amount;

        @Override
        public void sendInvoiceReceipt(String email, Money amount) {
            recipient = email;
            this.amount = amount;
        }
    }
}
