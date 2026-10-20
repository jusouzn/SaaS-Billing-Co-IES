package com.saas.billing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import javax.sql.DataSource;

public class JdbcInvoiceRepository implements InvoiceRepository {

    private static final String SAVE_INVOICE_SQL =
            "INSERT INTO invoices(customer_id, amount, status, created_at) VALUES (?, ?, ?, ?)";

    private final DataSource dataSource;

    public JdbcInvoiceRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public void saveInvoice(String customerId, Money amount, InvoiceStatus status, Instant createdAt) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SAVE_INVOICE_SQL)) {
            statement.setString(1, customerId);
            statement.setBigDecimal(2, amount.rounded());
            statement.setString(3, status.name());
            statement.setTimestamp(4, Timestamp.from(createdAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new BillingInfrastructureException("Falha ao salvar fatura", exception);
        }
    }
}
