/*
 * (C) Copyright 2024 Hyland (http://www.hyland.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Srimanta Singh
 */
package org.nuxeo.rest.exception;

import static jakarta.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static jakarta.ws.rs.core.HttpHeaders.RETRY_AFTER;

import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.flipkart.zjsonpatch.JsonPatchApplicationException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.RenditionException;
import org.nuxeo.ecm.core.schema.validation.ValidationException;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.rest.validation.io.ValidationErrorDTO;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.springboot.security.SpringSecurityHelper;
import org.nuxeo.runtime.transaction.TransactionRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

@ControllerAdvice
public class NuxeoExceptionController {

    private static final String CONSTRAINT_VIOLATION_SEPARATOR = ": ";

    private static final Logger log = LoggerFactory.getLogger(NuxeoExceptionController.class);

    private static final String RETRY_AFTER_DEFAULT_DELAY = "2"; // 2s

    @Value("${nuxeo.rest.error.stack.show:false}")
    private boolean showStackTrace;

    HxprErrorResponse createErrorResponse(int statusCode, Throwable cause) {
        // Jackson 3 appends source location to getMessage(); prefer the original message to keep responses clean.
        String message = (cause instanceof JacksonException je) ? je.getOriginalMessage() : cause.getMessage();
        if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            message = HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase();
        }
        return createErrorResponse(statusCode, message, cause);
    }

    HxprErrorResponse createErrorResponse(int status, String message, Throwable cause) {
        StackTraceElement[] stackTrace = null;
        Throwable exception = null;
        if (showStackTrace) {
            stackTrace = cause.getStackTrace();
            exception = cause;
        }
        String causeMessage = cause.toString();
        if (status == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.error(causeMessage, cause);
        } else if (Framework.isInitialized() && Framework.isDevModeSet()) {
            log.warn(causeMessage, cause);
        } else {
            log.debug(causeMessage, cause);
        }
        String userId = SpringSecurityHelper.getPrincipalId();
        UserManager userManager = getUserManager();
        NuxeoPrincipal principal = (StringUtils.isNotEmpty(userId) && userManager != null) ? userManager.getPrincipal(userId) : null;
        LoggingEventBuilder statusLog = log.atInfo();
        if (principal != null){
            String userGroups = principal.getAllGroups() != null ? StringUtils.join(principal.getAllGroups(), ";") : StringUtils.EMPTY;
            statusLog = statusLog.addKeyValue("userId", principal.getId())
                               .addKeyValue("userGroups", userGroups)
                               .addKeyValue("isAdmin", principal.isAdministrator());
        }
        statusLog.log("STATUS is {} | {}", status, message);
        return new HxprErrorResponse("exception", status, message, stackTrace, exception);
    }

    protected <T> ResponseEntity<T> createResponseEntity(int status, T error) {
        BodyBuilder builder = ResponseEntity.status(status);
        if (status == SC_SERVICE_UNAVAILABLE) {
            // for now default to a fixed delay
            builder.header(RETRY_AFTER, RETRY_AFTER_DEFAULT_DELAY);
        }
        return builder.body(error);
    }

    protected ResponseEntity<HxprErrorResponse> createHxprErrorResponseResponseEntity(int status, String message,
            Throwable cause) {
        return createResponseEntity(status, createErrorResponse(status, message, cause));
    }

    protected ResponseEntity<HxprErrorResponse> createHxprErrorResponseResponseEntity(int status, Throwable cause) {
        return createResponseEntity(status, createErrorResponse(status, cause));
    }

    protected ResponseEntity<HxprErrorResponse> handleNuxeoException(NuxeoException exception) {
        return createHxprErrorResponseResponseEntity(exception.getStatusCode(), exception);
    }

    protected ResponseEntity<Map<String, List<ValidationErrorDTO>>> handleValidationException(ValidationException ve) {
        Map<String, List<ValidationErrorDTO>> errors = new HashMap<>();
        errors.put("errors", toValidationErrorDTOList(ve, ValidationViolation.Type.ERROR));
        if (ve.getReport().asList().stream().anyMatch(vv -> vv.getType() == ValidationViolation.Type.WARNING)) {
            errors.put("warnings", toValidationErrorDTOList(ve, ValidationViolation.Type.WARNING));
        }
        return createResponseEntity(ve.getStatusCode(), errors);
    }

    private List<ValidationErrorDTO> toValidationErrorDTOList(ValidationException ve, ValidationViolation.Type type) {
        return ve.getReport().asList().stream().filter(e -> e.getType() == type).map(ValidationErrorDTO::new).toList();
    }

    @ExceptionHandler(value = InvalidFormatException.class)
    public ResponseEntity<HxprErrorResponse> handleInvalidFormatException(InvalidFormatException exception) {
        int status = HttpStatus.BAD_REQUEST.value();
        if (exception.getCause() instanceof DateTimeParseException) {
            String messageFormat = "Invalid date format for '%s' date";
            var exceptionPath = exception.getPath();
            String fieldName = exceptionPath.get(exceptionPath.size() - 1).getPropertyName();
            return createHxprErrorResponseResponseEntity(status, messageFormat.formatted(fieldName),
                    exception.getCause());
        }
        return createHxprErrorResponseResponseEntity(status, exception);
    }

    @ExceptionHandler(value = JsonPatchApplicationException.class)
    public ResponseEntity<HxprErrorResponse> handleJsonPatchApplicationException(JsonPatchApplicationException cause) {
        return createHxprErrorResponseResponseEntity(HttpStatus.BAD_REQUEST.value(), cause.getMessage(), cause);
    }

    @ExceptionHandler(value = HttpMessageConversionException.class)
    public ResponseEntity<HxprErrorResponse> handleHttpMessageConversionException(
            HttpMessageConversionException cause) {
        int status = getStatusCode(cause.getCause());
        if (cause.getCause() instanceof InvalidFormatException ife) {
            return handleInvalidFormatException(ife);
        }
        // Covers both databind failures and stream-level parse errors (e.g. malformed JSON / illegal control
        // characters), which are JacksonExceptions but not DatabindExceptions.
        if (cause.getCause() instanceof JacksonException jme) {
            if (cause.getCause() instanceof UnrecognizedPropertyException upe) {
                return createHxprErrorResponseResponseEntity(HttpStatus.UNPROCESSABLE_ENTITY.value(), upe.getMessage(), upe);
            }
            return createHxprErrorResponseResponseEntity(status, "Invalid input", jme);
        }
        return createHxprErrorResponseResponseEntity(status, cause);
    }

    @ExceptionHandler(value = TransactionRuntimeException.class)
    public ResponseEntity<HxprErrorResponse> handleTransactionRuntimeException(TransactionRuntimeException cause) {
        return createHxprErrorResponseResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR.value(), cause.getMessage(),
                cause);
    }

    @ExceptionHandler(value = RenditionException.class)
    public ResponseEntity<HxprErrorResponse> handleRenditionException(RenditionException cause) {
        return createHxprErrorResponseResponseEntity(cause.getStatusCode(), cause);
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    public ResponseEntity<HxprErrorResponse> handleAccessDeniedException(AccessDeniedException cause) {
        return createHxprErrorResponseResponseEntity(HttpStatus.UNAUTHORIZED.value(), cause.getMessage(), cause);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<HxprErrorResponse> handleConstraintViolationException(ConstraintViolationException cause) {
        String message = cause.getMessage();
        if (message != null && message.indexOf(CONSTRAINT_VIOLATION_SEPARATOR) >= 1) {
            message = message.split(CONSTRAINT_VIOLATION_SEPARATOR)[1];
        }
        return createHxprErrorResponseResponseEntity(HttpStatus.BAD_REQUEST.value(), message, cause);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<HxprErrorResponse> handleUnsupportedOperationException(UnsupportedOperationException cause) {
        return createHxprErrorResponseResponseEntity(HttpStatus.NOT_IMPLEMENTED.value(), HttpStatus.NOT_IMPLEMENTED.getReasonPhrase(), cause);
    }

    @ExceptionHandler(value = Throwable.class)
    public ResponseEntity<?> handleThrowable(Throwable cause) { // NOSONAR
        if (cause instanceof ValidationException ve) {
            // ExceptionHandler cannot handle interfaces, so we need to handle it here
            return handleValidationException(ve);
        }
        if (cause instanceof NuxeoException ne) {
            // Because ValidationExceptions are not always NuxeoExceptions, we need to handle both as Throwable
            return handleNuxeoException(ne);
        }
        int statusCode = getStatusCode(cause);
        return createHxprErrorResponseResponseEntity(statusCode, cause);
    }

    protected static int getStatusCode(Throwable t) {
        if (t instanceof ErrorResponse e) {
            return e.getStatusCode().value();
        } else if (t instanceof WebApplicationException e) {
            return e.getResponse().getStatus();
        } else if (t instanceof SecurityException) {
            return HttpStatus.FORBIDDEN.value();
        } else if (t instanceof IllegalArgumentException || t instanceof JacksonException) {
            return HttpStatus.BAD_REQUEST.value();
        } else if (t instanceof DateTimeParseException) {
            return HttpStatus.BAD_REQUEST.value();
        }

        Throwable cause = t.getCause();
        if (cause == null || t == cause) {
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
        return getStatusCode(cause);
    }

    protected UserManager getUserManager() {
        return Framework.getService(UserManager.class);
    }
}
