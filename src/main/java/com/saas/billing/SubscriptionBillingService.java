package com.saas.billing;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SubscriptionBillingService {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final Clock clock;
    private final PricingCalculator pricingCalculator = new PricingCalculator();

    public SubscriptionBillingService(
            CustomerRepository customerRepository,
            InvoiceRepository invoiceRepository,
            PaymentGateway paymentGateway,
            NotificationService notificationService,
            Clock clock) {
        this.customerRepository = Objects.requireNonNull(customerRepository);
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository);
        this.paymentGateway = Objects.requireNonNull(paymentGateway);
        this.notificationService = Objects.requireNonNull(notificationService);
        this.clock = Objects.requireNonNull(clock);
    }

    // planType nunca foi usado no cálculo; continua na assinatura só para não quebrar quem chama
    @Deprecated
    public List<String> processMonthlyBilling(String customerId, String planType, double basePrice,
                                             String signupDateStr, int overdueDays, String promoCode,
                                             boolean isNonProfit, String paymentCardToken) {
        return processMonthlyBilling(new BillingCommand(customerId, Money.of(basePrice),
                LocalDate.parse(signupDateStr), overdueDays, promoCode, isNonProfit, paymentCardToken));
    }

    public List<String> processMonthlyBilling(BillingCommand command) {
        Objects.requireNonNull(command);
        Money price = pricingCalculator.calculate(command, LocalDate.now(clock));

        Optional<Customer> customer = customerRepository.findById(command.customerId());
        if (customer.isEmpty()) {
            return new ArrayList<>();
        }
        if (!customer.get().isActive()) {
            List<String> logs = new ArrayList<>();
            logs.add("Cliente inativo. Cobrança cancelada: " + command.customerId());
            return logs;
        }
        return chargeAndNotify(command, customer.get(), price);
    }

    private List<String> chargeAndNotify(BillingCommand command, Customer customer, Money price) {
        List<String> logs = new ArrayList<>();

        boolean paid = paymentGateway.charge(command.paymentCardToken(), price);
        if (paid) {
            logs.add("Pagamento processado com sucesso: R$ " + price);
        } else {
            logs.add("Falha no pagamento do cartão para: " + command.customerId());
        }

        InvoiceStatus status = paid ? InvoiceStatus.PAID : InvoiceStatus.FAILED;
        invoiceRepository.saveInvoice(command.customerId(), price, status, clock.instant());

        notificationService.sendInvoiceReceipt(customer.email(), price);
        logs.add("E-mail disparado para: " + customer.email());
        return logs;
    }
}
