package org.ecom.media;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Takes precedence over the global handler, whose catch-all would turn these into 500s. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class MediaExceptionHandler {

    @ExceptionHandler
    ProblemDetail handleInvalidImage(InvalidImageException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
        problem.setProperty("code", "UNSUPPORTED_IMAGE");
        return problem;
    }

}
