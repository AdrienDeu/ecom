package org.ecom.customer;

import java.util.List;
import java.util.UUID;

import org.ecom.shared.web.BusinessException;
import org.ecom.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressService {

    static final int MAX_ADDRESSES = 20;

    private final AddressRepository addresses;

    AddressService(AddressRepository addresses) {
        this.addresses = addresses;
    }

    @Transactional(readOnly = true)
    public List<AddressView> list(String customerId) {
        return addresses.findByCustomerIdOrderByCreatedAtAsc(customerId).stream().map(AddressView::of).toList();
    }

    /** Fails with 404 when the address does not belong to the customer, without revealing that it exists. */
    @Transactional(readOnly = true)
    public PostalAddress get(String customerId, UUID addressId) {
        return owned(customerId, addressId).getDetails();
    }

    @Transactional
    public AddressView create(String customerId, PostalAddress details) {
        if (addresses.countByCustomerId(customerId) >= MAX_ADDRESSES) {
            throw new BusinessException("TOO_MANY_ADDRESSES", "A customer can have at most %d addresses".formatted(MAX_ADDRESSES));
        }
        return AddressView.of(addresses.save(new Address(customerId, details)));
    }

    @Transactional
    public AddressView update(String customerId, UUID addressId, PostalAddress details) {
        var address = owned(customerId, addressId);
        address.setDetails(details);
        return AddressView.of(address);
    }

    @Transactional
    public void delete(String customerId, UUID addressId) {
        addresses.delete(owned(customerId, addressId));
    }

    private Address owned(String customerId, UUID addressId) {
        return addresses.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
    }

    public record AddressView(UUID id, PostalAddress address) {

        static AddressView of(Address address) {
            return new AddressView(address.getId(), address.getDetails());
        }

    }

}
