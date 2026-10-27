package org.ecom.quote;

import java.util.UUID;

import jakarta.validation.Valid;

import org.ecom.quote.QuoteDtos.ChangeQuoteStatusRequest;
import org.ecom.quote.QuoteDtos.QuoteRequestView;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/quote-requests")
class AdminQuoteController {

    private final QuoteService quotes;

    AdminQuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    @GetMapping
    PagedModel<QuoteRequestView> list(@RequestParam(required = false) QuoteStatus status,
                                      @ParameterObject @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(quotes.list(status, pageable));
    }

    @PostMapping("/{id}/status")
    QuoteRequestView changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeQuoteStatusRequest request) {
        return quotes.changeStatus(id, request.status());
    }

}
