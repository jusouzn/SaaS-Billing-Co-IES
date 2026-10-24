package com.saas.billing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

public class JdbcCustomerRepository implements CustomerRepository {

    private static final String FIND_CUSTOMER_SQL =
            "SELECT email, status FROM customers WHERE id = ?";

    private final DataSource dataSource;

    public JdbcCustomerRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_CUSTOMER_SQL)) {
            statement.setString(1, customerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Customer(
                        resultSet.getString("email"),
                        resultSet.getString("status")));
            }
        } catch (SQLException exception) {
            throw new BillingInfrastructureException("Falha ao consultar cliente", exception);
        }
    }

}
