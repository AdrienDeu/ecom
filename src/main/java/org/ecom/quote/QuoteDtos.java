package org.ecom.quote;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public final class QuoteDtos {

    private QuoteDtos() {
    }

    /**
     * {@code website} is a honeypot: hidden from people by the form, only bots fill it in.
     */
    public record QuoteRequestForm(@NotBlank @Size(max = 200) String fullName,
                                   @NotBlank @Email @Size(max = 254) String email,
                                   @Schema(nullable = true) @Size(max = 200) String company,
                                   @Schema(nullable = true) @Size(max = 30) String phone,
                                   @Schema(nullable = true) @Size(max = 200) String topic,
                                   @NotBlank @Size(min = 20, max = 5000) String message,
                                   @Schema(nullable = true) String website) {
    }

    public record QuoteRequestView(UUID id, String fullName, String email, @Schema(nullable = true) String company,
                                   @Schema(nullable = true) String phone, @Schema(nullable = true) String topic,
                                   String message, QuoteStatus status, Instant createdAt) {

        static QuoteRequestView of(QuoteRequest quote) {
            return new QuoteRequestView(quote.getId(), quote.getFullName(), quote.getEmail(), quote.getCompany(),
                    quote.getPhone(), quote.getTopic(), quote.getMessage(), quote.getStatus(), quote.getCreatedAt());
        }

    }

    public record ChangeQuoteStatusRequest(@NotNull QuoteStatus status) {
    }

}
