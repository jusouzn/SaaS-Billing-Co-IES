package com.saas.billing;

import java.util.Optional;

public interface CustomerRepository {

    Optional<Customer> findById(String customerId);
}
