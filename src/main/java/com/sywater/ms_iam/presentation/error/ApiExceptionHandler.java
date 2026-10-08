package com.sywater.ms_iam.presentation.error;

import com.sywater.ms_iam.domain.exception.AccountBlockedException;
import com.sywater.ms_iam.domain.exception.AccountLockedException;
import com.sywater.ms_iam.domain.exception.AccountNotFoundException;
import com.sywater.ms_iam.domain.exception.AccountNotVerifiedException;
import com.sywater.ms_iam.domain.exception.AlreadyVerifiedException;
import com.sywater.ms_iam.domain.exception.CodeRecentlySentException;
import com.sywater.ms_iam.domain.exception.DomainException;
import com.sywater.ms_iam.domain.exception.EmailAlreadyRegisteredException;
import com.sywater.ms_iam.domain.exception.EmailNotFoundException;
import com.sywater.ms_iam.domain.exception.InvalidCodeException;
import com.sywater.ms_iam.domain.exception.InvalidRefreshTokenException;
import com.sywater.ms_iam.domain.exception.PhoneAlreadyRegisteredException;
import com.sywater.ms_iam.domain.exception.UserNotFoundException;
import com.sywater.ms_iam.domain.exception.WeakPasswordException;
import com.sywater.ms_iam.domain.exception.WrongPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> domain(DomainException e) {
        HttpStatus status = statusOf(e);
        ProblemDetail problem = problem(status, e.code(), e.getMessage());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);

        if (e instanceof WrongPasswordException w) problem.setProperty("remainingAttempts", w.remainingAttempts());
        if (e instanceof InvalidCodeException c) problem.setProperty("remainingAttempts", c.remainingAttempts());
        if (e instanceof AccountLockedException l) problem.setProperty("lockedUntil", l.lockedUntil().toString());
        if (e instanceof WeakPasswordException w) problem.setProperty("unmetRules", w.unmetRules());
        if (e instanceof CodeRecentlySentException r) {
            long seconds = Math.max(1, r.retryAfter().toSeconds());
            problem.setProperty("retryAfterSeconds", seconds);
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        }
        return response.body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException e) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f ->
                errors.computeIfAbsent(f.getField(), k -> new java.util.ArrayList<>()).add(f.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation.failed", "One or more fields are invalid.");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "validation.malformed_body",
                "The request body is not valid JSON."));
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    ResponseEntity<ProblemDetail> upload(Exception e) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "user.invalid_avatar",
                "The photo is too big or was not sent as multipart/form-data."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(HttpStatus.NOT_FOUND, "not_found", "Not found."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.internalServerError().body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "server.error",
                "Unexpected error. Try again later."));
    }

    static HttpStatus statusOf(DomainException e) {
        if (e instanceof EmailNotFoundException || e instanceof WrongPasswordException
                || e instanceof InvalidRefreshTokenException) return HttpStatus.UNAUTHORIZED;
        if (e instanceof AccountNotVerifiedException || e instanceof AccountBlockedException
                || e instanceof com.sywater.ms_iam.domain.exception.InternalOnlyException) return HttpStatus.FORBIDDEN;
        if (e instanceof AccountNotFoundException || e instanceof UserNotFoundException) return HttpStatus.NOT_FOUND;
        if (e instanceof EmailAlreadyRegisteredException || e instanceof PhoneAlreadyRegisteredException
                || e instanceof AlreadyVerifiedException) return HttpStatus.CONFLICT;
        if (e instanceof AccountLockedException) return HttpStatus.LOCKED;                  // 423
        if (e instanceof CodeRecentlySentException) return HttpStatus.TOO_MANY_REQUESTS;    // 429
        return HttpStatus.BAD_REQUEST;   // invalid data, weak password, wrong/expired code, avatar...
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(code);
        problem.setType(URI.create("https://sywater.dev/errors/" + code));
        return problem;
    }
}
