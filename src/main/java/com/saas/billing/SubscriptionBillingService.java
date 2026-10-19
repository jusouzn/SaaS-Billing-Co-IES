package com.saas.billing;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SubscriptionBillingService {

    private static final BigDecimal DISCOUNT_2_YEARS = new BigDecimal("0.15");
    private static final BigDecimal DISCOUNT_1_YEAR = new BigDecimal("0.10");
    private static final BigDecimal NON_PROFIT_FACTOR = new BigDecimal("0.70");
    private static final Money BLACKFRIDAY_DISCOUNT = Money.of("30.00");
    private static final BigDecimal WELCOME10_FACTOR = new BigDecimal("0.90");
    private static final BigDecimal LATE_PENALTY = new BigDecimal("0.02");
    private static final BigDecimal DAILY_INTEREST = new BigDecimal("0.001");

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final Clock clock;

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

    @Deprecated
    public List<String> processMonthlyBilling(String customerId, String planType, double basePrice,
                                             String signupDateStr, int overdueDays, String promoCode,
                                             boolean isNonProfit, String paymentCardToken) {
        return processMonthlyBilling(new BillingCommand(customerId, planType, Money.of(basePrice),
                LocalDate.parse(signupDateStr), overdueDays, promoCode, isNonProfit, paymentCardToken));
    }

    public List<String> processMonthlyBilling(BillingCommand command) {
        Objects.requireNonNull(command);
        List<String> logs = new ArrayList<>();
        LocalDate today = LocalDate.now(clock);
        int monthsActive = (today.getYear() - command.signupDate().getYear()) * 12
                + (today.getMonthValue() - command.signupDate().getMonthValue());
        Money basePrice = command.basePrice();
        Money calculatedPrice = basePrice;

        if (monthsActive > 24) {
            calculatedPrice = calculatedPrice.minus(basePrice.times(DISCOUNT_2_YEARS));
        } else if (monthsActive > 12) {
            calculatedPrice = calculatedPrice.minus(basePrice.times(DISCOUNT_1_YEAR));
        }

        if (command.nonProfit()) {
            calculatedPrice = calculatedPrice.times(NON_PROFIT_FACTOR);
        }

        if (command.hasPromoCode()) {
            if (command.promoCode().equalsIgnoreCase("BLACKFRIDAY") && today.getMonthValue() == 11) {
                calculatedPrice = calculatedPrice.minus(BLACKFRIDAY_DISCOUNT);
            } else if (command.promoCode().equalsIgnoreCase("WELCOME10") && monthsActive <= 1) {
                calculatedPrice = calculatedPrice.times(WELCOME10_FACTOR);
            }
        }

        if (command.overdueDays() > 0) {
            Money penalty = calculatedPrice.times(LATE_PENALTY);
            Money dailyInterest = calculatedPrice.times(DAILY_INTEREST).times(command.overdueDays());
            calculatedPrice = calculatedPrice.plus(penalty).plus(dailyInterest);
        }

        if (calculatedPrice.isNegative()) {
            calculatedPrice = Money.ZERO;
        }

        Optional<Customer> customerResult = customerRepository.findById(command.customerId());
        if (customerResult.isEmpty()) {
            return logs;
        }

        Customer customer = customerResult.get();
        if (!customer.isActive()) {
            logs.add("Cliente inativo. Cobrança cancelada: " + command.customerId());
            return logs;
        }

        boolean paymentSuccess = paymentGateway.charge(command.paymentCardToken(), calculatedPrice);
        if (paymentSuccess) {
            logs.add("Pagamento processado com sucesso: R$ " + calculatedPrice);
        } else {
            logs.add("Falha no pagamento do cartão para: " + command.customerId());
        }

        invoiceRepository.saveInvoice(command.customerId(), calculatedPrice,
                paymentSuccess ? InvoiceStatus.PAID : InvoiceStatus.FAILED, clock.instant());
        notificationService.sendInvoiceReceipt(customer.email(), calculatedPrice);
        logs.add("E-mail disparado para: " + customer.email());
        return logs;
    }

}
