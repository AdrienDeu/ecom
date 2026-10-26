package org.ecom.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Takes precedence over the global handler, whose catch-all would turn these into 500s. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class PaymentExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentExceptionHandler.class);

    @ExceptionHandler
    ProblemDetail handleProviderUnavailable(PaymentProviderException ex) {
        log.warn("Payment provider unavailable: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "The payment provider is unavailable, please retry");
        problem.setProperty("code", "PAYMENT_PROVIDER_UNAVAILABLE");
        return problem;
    }

    @ExceptionHandler
    ProblemDetail handleInvalidWebhook(InvalidWebhookException ex) {
        log.warn("Rejected webhook: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid webhook");
    }

}
