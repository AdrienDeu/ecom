package org.ecom.quote;

import jakarta.validation.Valid;

import org.ecom.quote.QuoteDtos.QuoteRequestForm;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public quote form: no account needed to ask for custom consulting.
 */
@RestController
class QuoteController {

    private final QuoteService quotes;

    QuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    @PostMapping("/api/quote-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void submit(@Valid @RequestBody QuoteRequestForm form) {
        quotes.submit(form);
    }

}
