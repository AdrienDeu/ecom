package org.ecom.quote;

import java.util.UUID;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.ecom.quote.QuoteDtos.QuoteRequestForm;
import org.ecom.quote.QuoteDtos.QuoteRequestView;
import org.ecom.shared.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final QuoteRequestRepository quotes;
    private final ApplicationEventPublisher events;
    private final Counter received;
    private final Counter rejected;

    QuoteService(QuoteRequestRepository quotes, ApplicationEventPublisher events, MeterRegistry registry) {
        this.quotes = quotes;
        this.events = events;
        this.received = Counter.builder("ecom.quote.requests").description("Consulting quote requests")
                .tag("outcome", "received").register(registry);
        this.rejected = Counter.builder("ecom.quote.requests").description("Consulting quote requests")
                .tag("outcome", "spam").register(registry);
    }

    /** Records a request from the public site. Bots caught by the honeypot get the same answer but nothing is kept. */
    @Transactional
    public void submit(QuoteRequestForm form) {
        if (StringUtils.hasText(form.website())) {
            rejected.increment();
            return;
        }
        var quote = quotes.save(new QuoteRequest(form.fullName().strip(), form.email().strip(), blankToNull(form.company()),
                blankToNull(form.phone()), blankToNull(form.topic()), form.message().strip()));
        received.increment();
        events.publishEvent(QuoteRequested.of(quote));
        // No personal data in the logs: the id is enough to find the request in the back-office
        log.info("Quote request {} received", quote.getId());
    }

    @Transactional(readOnly = true)
    public Page<QuoteRequestView> list(QuoteStatus status, Pageable pageable) {
        var page = status == null ? quotes.findAll(pageable) : quotes.findByStatus(status, pageable);
        return page.map(QuoteRequestView::of);
    }

    @Transactional
    public QuoteRequestView changeStatus(UUID id, QuoteStatus status) {
        var quote = quotes.findById(id).orElseThrow(() -> new ResourceNotFoundException("Quote request", id));
        quote.changeStatus(status);
        return QuoteRequestView.of(quote);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

}
