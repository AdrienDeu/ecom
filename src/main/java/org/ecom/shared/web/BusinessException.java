package org.ecom.shared.web;

/**
 * A request that is valid but conflicts with the current state (insufficient stock, forbidden status change...).
 * Mapped to {@code 409 Conflict} with a stable machine-readable {@code code}.
 */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

}
