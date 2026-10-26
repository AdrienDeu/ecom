package org.ecom.payment;

import java.util.Map;

/**
 * @param settings public settings the SPA needs to display the method, e.g. {@code clientId} and {@code currency} for PayPal
 */
public record PaymentMethodView(String id, Map<String, String> settings) {
}
