package org.ecom.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/payments")
class AdminPaymentController {

    private final PaymentService payments;

    AdminPaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /** Every payment of an order, failed attempts included, oldest first. */
    @GetMapping
    List<PaymentView> list(@RequestParam UUID orderId) {
        return payments.forOrder(orderId);
    }

    @PostMapping("/{id}/refund")
    PaymentView refund(@PathVariable UUID id, @Valid @RequestBody RefundRequest request) {
        return payments.refund(id, request.amount());
    }

    record RefundRequest(@Schema(nullable = true, description = "Defaults to whatever remains to refund")
                         @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount) {
    }

}
