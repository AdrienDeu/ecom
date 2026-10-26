package org.ecom.shared.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 9457 {@link ProblemDetail} responses. Spring MVC's own exceptions
 * (validation, unsupported media type...) are handled by {@link ResponseEntityExceptionHandler}.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handleBusiness(BusinessException ex) {
        return conflict(ex.code(), ex.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return conflict("CONCURRENT_MODIFICATION", "The resource was modified concurrently, please retry");
    }

    @ExceptionHandler
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.debug("Data integrity violation", ex);
        return conflict("CONFLICT", "The request conflicts with existing data");
    }

    /** Unknown {@code sort} property in a paged request. */
    @ExceptionHandler
    ProblemDetail handleInvalidSort(PropertyReferenceException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid sort property '%s'".formatted(ex.getPropertyName()));
    }

    @ExceptionHandler
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        // Never leak internal details to the client
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private static ProblemDetail conflict(String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setProperty("code", code);
        return problem;
    }

}
