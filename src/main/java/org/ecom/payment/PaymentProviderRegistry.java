package org.ecom.payment;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.ecom.shared.web.BusinessException;
import org.springframework.stereotype.Component;

/**
 * Every enabled {@link PaymentProvider} bean, by id. Adding a payment method means adding an adapter: nothing here
 * nor in the payment domain changes.
 */
@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providers;

    public PaymentProviderRegistry(List<PaymentProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentProvider::id, Function.identity()));
    }

    public PaymentProvider get(String id) {
        var provider = providers.get(id);
        if (provider == null) {
            throw new BusinessException("UNSUPPORTED_PAYMENT_METHOD", "Payment method '%s' is not available".formatted(id));
        }
        return provider;
    }

    public Collection<PaymentProvider> all() {
        return providers.values();
    }

}
