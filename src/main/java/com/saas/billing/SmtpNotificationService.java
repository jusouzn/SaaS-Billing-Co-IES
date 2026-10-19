package com.saas.billing;

import java.util.Objects;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;

public class SmtpNotificationService implements NotificationService {

    private static final String SUBJECT = "Sua Fatura Mensal";
    private static final String SMTP_FROM = "billing@empresa.com";

    private final String hostName;
    private final int smtpPort;

    public SmtpNotificationService(String hostName, int smtpPort) {
        this.hostName = Objects.requireNonNull(hostName);
        this.smtpPort = smtpPort;
    }

    @Override
    public void sendInvoiceReceipt(String email, Money amount) {
        SimpleEmail emailSender = new SimpleEmail();
        emailSender.setHostName(hostName);
        emailSender.setSmtpPort(smtpPort);
        try {
            emailSender.setFrom(SMTP_FROM);
            emailSender.setSubject(SUBJECT);
            emailSender.setMsg("Olá! Sua fatura foi processada no valor de R$ " + amount);
            emailSender.addTo(email);
            emailSender.send();
        } catch (EmailException exception) {
            throw new BillingInfrastructureException("Falha ao enviar recibo por e-mail", exception);
        }
    }
}
