package org.ecom.customer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByCustomerIdOrderByCreatedAtAsc(String customerId);

    Optional<Address> findByIdAndCustomerId(UUID id, String customerId);

    long countByCustomerId(String customerId);

}
