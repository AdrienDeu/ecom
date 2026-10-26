package org.ecom.payment;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
class PaymentController {

    private final PaymentService payments;

    PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /** Enabled payment methods. */
    @GetMapping("/methods")
    List<PaymentMethodView> methods() {
        return payments.methods();
    }

    /** Starts paying an order of the signed-in customer; the amount is the order total. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PaymentView start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartPaymentRequest request) {
        return payments.start(jwt.getSubject(), request.orderId(), request.provider());
    }

    /** Called once the customer approved the payment on the provider's side. */
    @PostMapping("/{id}/capture")
    PaymentView capture(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return payments.capture(jwt.getSubject(), id);
    }

    record StartPaymentRequest(@NotNull UUID orderId, @NotBlank String provider) {
    }

}
