package dev.vality.disputes.api.controller;

import dev.vality.disputes.exception.*;
import dev.vality.swag.disputes.model.GeneralError;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.InvalidMimeTypeException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.context.request.WebRequest;

import java.net.http.HttpTimeoutException;
import java.util.stream.Collectors;

import static org.springframework.http.ResponseEntity.status;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class ErrorControllerAdvice {

    // ----------------- 4xx -----------------------------------------------------

    @ExceptionHandler({UnexpectedMimeTypeException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleUnexpectedMimeTypeException(UnexpectedMimeTypeException ex) {
        log.warn("<- Res [400]: Unexpected MimeType", ex);
        return new GeneralError()
                .message("Blocked: Unexpected MimeType");
    }

    @ExceptionHandler({PaymentExpiredException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handlePaymentExpiredException(PaymentExpiredException ex) {
        log.warn("<- Res [400]: Payment expired", ex);
        return new GeneralError()
                .message("Blocked: Payment expired");
    }

    @ExceptionHandler({CapturedPaymentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleCapturedPaymentException(CapturedPaymentException ex) {
        log.warn("<- Res [400]: Payment already successful", ex);
        return new GeneralError()
                .message("Blocked: Payment already successful");
    }

    @ExceptionHandler({InvoicingPaymentStatusRestrictionsException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleInvoicingPaymentStatusRestrictionsException(InvoicingPaymentStatusRestrictionsException ex) {
        log.warn("<- Res [400]: Payment should be failed", ex);
        if (ex.getStatus() != null) {
            return new GeneralError()
                    .message("Blocked: Payment should be failed, but status=" + ex.getStatus().getSetField().getFieldName());
        }
        return new GeneralError()
                .message("Blocked: Payment should be failed");
    }

    @ExceptionHandler({InvalidMimeTypeException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleInvalidMimeTypeException(InvalidMimeTypeException ex) {
        log.warn("<- Res [400]: MimeType not valid", ex);
        return new GeneralError()
                .message(ex.getMessage());
    }

    @ExceptionHandler({InvalidMediaTypeException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleInvalidMediaTypeException(InvalidMediaTypeException ex) {
        log.warn("<- Res [400]: MimeType not valid", ex);
        return new GeneralError()
                .message(ex.getMessage());
    }

    @ExceptionHandler({ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleConstraintViolationException(ConstraintViolationException ex) {
        log.warn("<- Res [400]: Not valid", ex);
        var errorMessage = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        return new GeneralError()
                .message(errorMessage);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        log.warn("<- Res [400]: MethodArgument not valid", ex);
        return new GeneralError()
                .message(ex.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        log.warn("<- Res [400]: Missing ServletRequestParameter", ex);
        return new GeneralError()
                .message(ex.getMessage());
    }

    @ExceptionHandler({TokenKeeperException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void handleAccessDeniedException(TokenKeeperException ex) {
        log.warn("<- Res [401]: Request denied access", ex);
    }

    @ExceptionHandler({AuthorizationException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void handleAccessDeniedException(AuthorizationException ex) {
        log.warn("<- Res [401]: Request denied access", ex);
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Object handleNotFoundException(NotFoundException ex) {
        log.warn("<- Res [404]: Not found, type={}", ex.getType(), ex);
        return new GeneralError()
                .message(ex.getType().name() + " not found");
    }

    @ExceptionHandler({HttpMediaTypeNotAcceptableException.class})
    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    public void handleHttpMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        log.warn("<- Res [406]: MediaType not acceptable", ex);
    }

    @ExceptionHandler({HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<?> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, WebRequest request) {
        log.warn("<- Res [415]: MediaType not supported", ex);
        return status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .headers(httpHeaders(ex))
                .build();
    }

    // ----------------- 5xx -----------------------------------------------------

    @ExceptionHandler(HttpClientErrorException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public void handleHttpClientErrorException(HttpClientErrorException ex) {
        log.error("<- Res [500]: Error with using inner http client, code={}, body={}",
                ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
    }

    @ExceptionHandler(HttpTimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public void handleHttpTimeoutException(HttpTimeoutException ex) {
        log.error("<- Res [504]: Timeout with using inner http client", ex);
    }

    @ExceptionHandler(Throwable.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public void handleException(Throwable ex) {
        log.error("<- Res [500]: Unrecognized inner error", ex);
    }

    private HttpHeaders httpHeaders(HttpMediaTypeNotSupportedException ex) {
        var headers = new HttpHeaders();
        var mediaTypes = ex.getSupportedMediaTypes();
        if (!CollectionUtils.isEmpty(mediaTypes)) {
            headers.setAccept(mediaTypes);
        }
        return headers;
    }
}
