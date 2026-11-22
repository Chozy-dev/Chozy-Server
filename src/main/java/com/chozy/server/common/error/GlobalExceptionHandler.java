package com.chozy.server.common.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<Object> integrity(org.springframework.dao.DataIntegrityViolationException ex) {
    Throwable cause = ex;
    while (cause != null) {
      if (cause instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState()))
        return response(ErrorCode.DUPLICATE);
      cause = cause.getCause();
    }
    return response(ErrorCode.INTERNAL_ERROR);
  }

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<Object> business(BusinessException ex) {
    return response(ex.errorCode());
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<Object> validation(ConstraintViolationException ex) {
    return response(ErrorCode.INVALID_INPUT);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Object> forbidden(AccessDeniedException ex) {
    return response(ErrorCode.FORBIDDEN);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> unexpected(Exception ex) {
    // Exception messages and stack traces can contain SQL, credentials and rejected input.
    log.error("Unhandled server exception type={}", ex.getClass().getName());
    return response(ErrorCode.INTERNAL_ERROR);
  }

  private ResponseEntity<Object> response(ErrorCode code) {
    return ResponseEntity.status(code.status()).body(ErrorResponse.of(code));
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ErrorCode code;
    if (ex instanceof MethodArgumentNotValidException
        || ex instanceof HandlerMethodValidationException) code = ErrorCode.INVALID_INPUT;
    else if (ex instanceof TypeMismatchException) code = ErrorCode.TYPE_MISMATCH;
    else if (ex instanceof MissingServletRequestParameterException
        || ex instanceof ServletRequestBindingException) code = ErrorCode.MISSING_PARAMETER;
    else if (ex instanceof HttpMessageNotReadableException) code = ErrorCode.INVALID_JSON;
    else if (ex instanceof HttpRequestMethodNotSupportedException)
      code = ErrorCode.METHOD_NOT_ALLOWED;
    else if (ex instanceof HttpMediaTypeNotSupportedException)
      code = ErrorCode.UNSUPPORTED_MEDIA_TYPE;
    else if (ex instanceof NoResourceFoundException || ex instanceof NoHandlerFoundException)
      code = ErrorCode.NOT_FOUND;
    else if (ex instanceof MaxUploadSizeExceededException) code = ErrorCode.FILE_TOO_LARGE;
    else if (ex instanceof AsyncRequestTimeoutException) code = ErrorCode.REQUEST_TIMEOUT;
    else code = ErrorCode.INTERNAL_ERROR;
    return new ResponseEntity<>(
        ErrorResponse.of(code), headers, HttpStatusCode.valueOf(code.status()));
  }
}
