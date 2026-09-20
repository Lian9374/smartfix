package com.smartfix.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.UUID;

/** Shared browser errors. Never render exception messages, submitted values, SQL or paths. */
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class,
            NoHandlerFoundException.class})
    public ModelAndView notFound(Exception exception) { return error(HttpStatus.NOT_FOUND); }

    @ExceptionHandler({InputValidationException.class, BindException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ModelAndView invalidInput(Exception exception) { return error(HttpStatus.BAD_REQUEST); }

    @ExceptionHandler({BusinessConflictException.class, DataIntegrityViolationException.class})
    public ModelAndView conflict(Exception exception) { return error(HttpStatus.CONFLICT); }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView forbidden(AccessDeniedException exception) { return error(HttpStatus.FORBIDDEN); }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView tooLarge(MaxUploadSizeExceededException exception) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ModelAndView wrongMethod(HttpRequestMethodNotSupportedException exception) {
        return error(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ModelAndView wrongContentType(HttpMediaTypeNotSupportedException exception) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ModelAndView notAcceptable(HttpMediaTypeNotAcceptableException exception) {
        return error(HttpStatus.NOT_ACCEPTABLE);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception exception) {
        String reference = UUID.randomUUID().toString();
        // Exception messages/stacks may contain credentials, SQL or filesystem paths.
        log.error("Unexpected request failure: reference={}, type={}", reference,
                exception.getClass().getSimpleName());
        ModelAndView result = error(HttpStatus.INTERNAL_SERVER_ERROR);
        result.addObject("reference", reference);
        return result;
    }

    private ModelAndView error(HttpStatus status) {
        ModelAndView result = new ModelAndView("error");
        result.setStatus(status);
        result.addObject("status", status.value());
        return result;
    }
}
