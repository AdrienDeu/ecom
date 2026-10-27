package org.ecom.quote;

import java.util.UUID;

/** Published once a quote request is saved; carries what the shop needs to answer it. */
record QuoteRequested(UUID id, String fullName, String email, String company, String phone, String topic,
                      String message) {

    static QuoteRequested of(QuoteRequest quote) {
        return new QuoteRequested(quote.getId(), quote.getFullName(), quote.getEmail(), quote.getCompany(),
                quote.getPhone(), quote.getTopic(), quote.getMessage());
    }

}
