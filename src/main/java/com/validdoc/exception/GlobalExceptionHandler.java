package com.validdoc.exception;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Locale;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException e, Locale locale) {
        String message = messageSource.getMessage(e.getErrorCode().getMessageKey(), e.getArgs(), locale);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(e.getErrorCode().getStatus());
        if (e.getRetryAfterSeconds() != null) {
            builder.header("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
        }
        return builder.body(new ApiErrorResponse(
                e.getErrorCode().name(), message, e.getRetryAfterSeconds(), e.getRemainingAttempts()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse("VALIDATION_FAILED", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedBody(HttpMessageNotReadableException e, Locale locale) {
        return respond(ErrorCode.MALFORMED_REQUEST_BODY, locale);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e, Locale locale) {
        return respond(ErrorCode.FILE_TOO_LARGE, locale);
    }

    @ExceptionHandler(TaskRejectedException.class)
    public ResponseEntity<ApiErrorResponse> handleTaskRejected(TaskRejectedException e, Locale locale) {
        return respond(ErrorCode.SERVER_BUSY, locale);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException e, Locale locale) {
        return respond(ErrorCode.BAD_CREDENTIALS, locale);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException e, Locale locale) {
        return respond(ErrorCode.ACCESS_DENIED, locale);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e, Locale locale) {
        return respond(ErrorCode.DUPLICATE_RECORD, locale);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException e, Locale locale) {
        return respond(ErrorCode.RESOURCE_NOT_FOUND, locale);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, Locale locale) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, locale);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException e, Locale locale) {
        return respond(ErrorCode.MISSING_REQUEST_PARAMETER, locale);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e, Locale locale) {
        return respond(ErrorCode.INVALID_PARAMETER_TYPE, locale);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e, Locale locale) {
        log.error("Beklenmeyen hata", e);
        return respond(ErrorCode.INTERNAL_UNEXPECTED, locale);
    }

    private @NonNull ResponseEntity<ApiErrorResponse> respond(ErrorCode errorCode, Locale locale, Object... args) {
        String message = messageSource.getMessage(errorCode.getMessageKey(), args, locale);
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ApiErrorResponse(errorCode.name(), message));
    }
}