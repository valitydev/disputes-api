package dev.vality.disputes.controller;

import dev.vality.disputes.exception.AuthorizationException;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.exception.ProviderErrorException;
import dev.vality.disputes.exception.TokenKeeperException;
import dev.vality.swag.disputes.model.DefaultLogicError;
import dev.vality.swag.disputes.model.GeneralError;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
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
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.http.ResponseEntity.status;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ErrorControllerAdvice {

    // ----------------- 4xx -----------------------------------------------------

    @ExceptionHandler({ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("<- Res [400]: Not valid", e);
        var errorMessage = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        return new DefaultLogicError()
                .code(DefaultLogicError.CodeEnum.INVALIDREQUEST)
                .message(errorMessage);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("<- Res [400]: MethodArgument not valid", e);
        return new DefaultLogicError()
                .code(DefaultLogicError.CodeEnum.INVALIDREQUEST)
                .message(e.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("<- Res [400]: Missing ServletRequestParameter", e);
        return new DefaultLogicError()
                .code(DefaultLogicError.CodeEnum.INVALIDREQUEST)
                .message(e.getMessage());

    }

    @ExceptionHandler({TokenKeeperException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void handleAccessDeniedException(TokenKeeperException e) {
        log.warn("<- Res [401]: Request denied access", e);
    }

    @ExceptionHandler({AuthorizationException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void handleAccessDeniedException(AuthorizationException e) {
        log.warn("<- Res [401]: Request denied access", e);
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNotFoundException(NotFoundException e) {
        log.warn("<- Res [404]: Not found", e);
    }

    @ExceptionHandler({HttpMediaTypeNotAcceptableException.class})
    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    public void handleHttpMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        log.warn("<- Res [406]: MediaType not acceptable", e);
    }

    @ExceptionHandler({HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<?> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException e, WebRequest request) {
        log.warn("<- Res [415]: MediaType not supported", e);
        return status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .headers(httpHeaders(e))
                .build();
    }

    private HttpHeaders httpHeaders(HttpMediaTypeNotSupportedException e) {
        HttpHeaders headers = new HttpHeaders();
        List<MediaType> mediaTypes = e.getSupportedMediaTypes();
        if (!CollectionUtils.isEmpty(mediaTypes)) {
            headers.setAccept(mediaTypes);
        }
        return headers;
    }

//    @ExceptionHandler({MethodNotSupported.class})
//    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
//    public void handleMethodNotSupported(MethodNotSupported e) {
//        log.warn("<- Res [422]: Method not supported", e);
//    }

    // ----------------- 5xx -----------------------------------------------------

    @ExceptionHandler(HttpClientErrorException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public void handleHttpClientErrorException(HttpClientErrorException e) {
        log.error("<- Res [500]: Error with using inner http client, code={}, body={}",
                e.getStatusCode(), e.getResponseBodyAsString(), e);
    }

    @ExceptionHandler(HttpTimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public void handleHttpTimeoutException(HttpTimeoutException e) {
        log.error("<- Res [504]: Timeout with using inner http client", e);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public void handleException(Exception e) {
        log.error("<- Res [500]: Unrecognized inner error", e);
    }

    @ExceptionHandler({ProviderErrorException.class})
    public ResponseEntity<GeneralError> handleProviderError(ProviderErrorException e) {
        log.warn("<- Res [500]: {}", e.getMessage());
        return ResponseEntity.internalServerError().body(e.getGeneralError());
    }
}
