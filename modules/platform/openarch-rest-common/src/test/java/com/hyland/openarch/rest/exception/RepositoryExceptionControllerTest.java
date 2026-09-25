/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
 */
package com.hyland.openarch.rest.exception;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE;
import static jakarta.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.HttpHeaders.RETRY_AFTER;
import static com.hyland.openarch.ecm.core.schema.types.resolver.ObjectResolver.NAME_KEY;
import static org.skyscreamer.jsonassert.JSONCompareMode.NON_EXTENSIBLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.common.RetryAfterHint;
import com.hyland.openarch.ecm.core.api.RenditionException;
import com.hyland.openarch.ecm.core.api.local.DummyLoginFeature;
import com.hyland.openarch.ecm.core.api.validation.ConstraintViolation.PathNode;
import com.hyland.openarch.ecm.core.api.validation.ConstraintViolation;
import com.hyland.openarch.ecm.core.api.validation.DocumentValidationException;
import com.hyland.openarch.ecm.core.api.validation.DocumentValidationReport;
import com.hyland.openarch.ecm.core.model.DocumentModelResolver;
import com.hyland.openarch.ecm.core.schema.dto.DTOPath;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.FieldImpl;
import com.hyland.openarch.ecm.core.schema.types.constraints.Constraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.ObjectResolverConstraint;
import com.hyland.openarch.ecm.core.schema.types.resolver.ObjectResolver;
import com.hyland.openarch.ecm.core.schema.validation.SchemaValidationException;
import com.hyland.openarch.ecm.core.schema.validation.ValidationReport;
import com.hyland.openarch.ecm.core.schema.validation.violations.InvalidObjectViolation;
import com.hyland.openarch.ecm.core.schema.validation.violations.TypeRecursionViolation;
import com.hyland.openarch.ecm.platform.test.UserManagerMockFeature;
import com.hyland.openarch.ecm.platform.test.UserManagerMockTestConfiguration;
import com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.transaction.TransactionRuntimeException;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;

/**
 * Unit test for {@link RepositoryExceptionController}
 */
@Features({ UserManagerMockFeature.class, DummyLoginFeature.class })
@Import(UserManagerMockTestConfiguration.class)
@SpringBootTest(classes = { RepositoryExceptionController.class, RepositoryExceptionControllerTest.ExceptionTestResource.class })
class RepositoryExceptionControllerTest extends RuntimeSpringBootTest {

    @RestController
    @EnableWebMvc
    @RequestMapping(path = "/exception", produces = MediaType.APPLICATION_JSON_VALUE)
    public static class ExceptionTestResource {

        @GetMapping("/oops")
        public void oops() {
            throw new RepositoryException("oops", 456);
        }

        @GetMapping("/serviceunavail")
        public void serviceunvail() {
            throw new RepositoryException("service unavail", SC_SERVICE_UNAVAILABLE);
        }

        @GetMapping("/serviceunavailRetryAfterHint")
        public void serviceunvailRetryAfterHint() {
            throw new RetryAfterHintException("breaker open", 30);
        }

        @GetMapping("/runtime")
        public void runtime() {
            throw new RuntimeException("oops");
        }

        @GetMapping("/transactionTimeout")
        public void transaction() {
            throw new TransactionRuntimeException("timeout");
        }

        @GetMapping("/jsonParse")
        public void jsonParse() {
            throw new StreamReadException((JsonParser) null, "fix your json");
        }

        @GetMapping("/JsonMappingException")
        public void jsonMappingException() {
            throw DatabindException.from((JsonParser) null, "Invalid field type!");
        }

        protected ConstraintViolation getResolverConstraintViolation(String name, String value) {
            Field field = new FieldImpl(name, null, null, null, null);
            ObjectResolver resolver = new DocumentModelResolver();
            resolver.configure(Map.of(NAME_KEY, "documentResolver"));
            Constraint constraint = new ObjectResolverConstraint(resolver);
            return new ConstraintViolation(List.of(new PathNode(field, 0)), constraint, value);
        }

        @GetMapping("/validate")
        public void validate() {
            DocumentValidationReport report = new DocumentValidationReport(
                    List.of(getResolverConstraintViolation("linkfield", "foo")));
            throw new DocumentValidationException(report);
        }

        @GetMapping("/validateMultiple")
        public void validateMultiple() {
            DocumentValidationReport report = new DocumentValidationReport(
                    List.of(getResolverConstraintViolation("linkfield", "foo"), //
                            getResolverConstraintViolation("linkfield2", "foo2")));
            throw new DocumentValidationException(report);
        }

        @GetMapping("validateModel")
        public void validateModel() {
            ValidationReport report = new ValidationReport(
                List.of(new TypeRecursionViolation(DTOPath.root("foo", "bar")))
            );
            throw new SchemaValidationException(report);
        }

        @GetMapping("validateModelMultiple")
        public void validateModelMultiple() {
            ValidationReport report = new ValidationReport(
                List.of(new TypeRecursionViolation(DTOPath.root("foo", "bar")), //
                        new InvalidObjectViolation(DTOPath.root("foo", List.of("bar", "foo")),"types", "supertypes")) //"In type: foo, invalid supertype: {List=bar}", List.of("bar", "foo"), "bar", "bar"))
            );
            throw new SchemaValidationException(report);
        }

        @GetMapping("/messageConversion")
        public void messageConversion() {
            throw new HttpMessageConversionException("oops", DatabindException.from((JsonParser) null, "cause"));
        }

        /**
         * A conversion failure with no cause that is not about reading the request: the converters could not
         * write the response. Same shape as a missing body -- no cause to classify -- opposite verdict.
         */
        @GetMapping("/responseNotWritable")
        public void responseNotWritable() {
            throw new HttpMessageNotWritableException("no converter for the return value");
        }

        @GetMapping("/rendition")
        public void rendition() {
            throw new RenditionException("Rendition Forbidden", 403);
        }

        /**
         * What the dispatcher throws for any multipart failure that is not a size limit, whatever its cause:
         * the base class, wrapping whatever {@code request.getParts()} threw.
         */
        @PostMapping("/multipart")
        public void multipart() {
            throw new MultipartException("Failed to parse multipart servlet request",
                    new IOException("the request was rejected because no multipart boundary was found"));
        }

        /** The one multipart failure that carries its own status, as a subclass implementing ErrorResponse. */
        @PostMapping("/multipartTooLarge")
        public void multipartTooLarge() {
            throw new MaxUploadSizeExceededException(4L);
        }

        /**
         * A body Spring insists on. The handler is never reached when the request omits it: the argument
         * resolver rejects the request first, which is the path this test exercises.
         */
        @PostMapping(path = "/requiredBody", consumes = MediaType.APPLICATION_JSON_VALUE)
        public void requiredBody(@RequestBody Map<String, Object> body) {
            throw new IllegalStateException("unreachable: " + body);
        }
    }

    static class RetryAfterHintException extends RepositoryException implements RetryAfterHint {

        private final int retryAfterSeconds;

        RetryAfterHintException(String message, int retryAfterSeconds) {
            super(message, SC_SERVICE_UNAVAILABLE);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        @Override
        public int getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    @Test
    public void testRepositoryException() throws Exception {
        doTestException("/exception/oops", 456, "oops");
    }

    /**
     * An error response produced by an {@code @ExceptionHandler} leaves the resolved exception on the request.
     * {@code TransactionFilter} keys the rollback of the request transaction off that, so it has to be there.
     */
    @Test
    public void testResolvedExceptionIsRecordedOnTheRequest() throws Exception {
        var result = mvc.perform(get("/exception/runtime")).andExpect(status().is(500)).andReturn();

        assertNotNull(result.getRequest().getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE));
    }

    @Test
    public void testServiceUnavailable() throws Exception {
        doTestExceptionAndHeaders("/exception/serviceunavail", SC_SERVICE_UNAVAILABLE, "service unavail",
                Map.of(RETRY_AFTER, "2")); // RepositoryExceptionController.RETRY_AFTER_DEFAULT_DELAY
    }

    /**
     * 500 is the only status this controller logs at ERROR, so moving a search outage from 500 to 503
     * (HXCS-8835) would otherwise have dropped it to debug -- invisible at default level exactly when someone
     * is looking for the outage. It logs at WARN instead, with the cause.
     */
    @Test
    public void testServiceUnavailableIsLoggedAsAWarningWithItsCause() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RepositoryExceptionController.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            mvc.perform(get("/exception/serviceunavail")).andExpect(status().is(SC_SERVICE_UNAVAILABLE));

            var warnings = events.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
            assertEquals(1, warnings.size(), "the outage is logged once, at WARN");
            assertNotNull(warnings.get(0).getThrowableProxy(), "the cause travels with it");
        } finally {
            logger.detachAppender(events);
        }
    }

    @Test
    public void testServiceUnavailableWithRetryAfterHint() throws Exception {
        doTestExceptionAndHeaders("/exception/serviceunavailRetryAfterHint", SC_SERVICE_UNAVAILABLE, "breaker open",
                Map.of(RETRY_AFTER, "30"));
    }

    @Test
    public void testRuntimeException() throws Exception {
        doTestException("/exception/runtime", 500, "Internal Server Error");
    }

    @Test
    public void testTransactionTimeoutException() throws Exception {
        doTestException("/exception/transactionTimeout", 500, "timeout");
    }

    @Test
    public void testJsonParseException() throws Exception {
        doTestException("/exception/jsonParse", 400, "fix your json");
    }

    @Test
    public void testJsonMappingException() throws Exception {
        doTestException("/exception/JsonMappingException", 400, "Invalid field type!");
    }

    @Test
    public void testHttpMessageConversionException() throws Exception {
        doTestException("/exception/messageConversion", 400, "Invalid input");
    }

    @Test
    public void testRenditionException() throws Exception {
        doTestException("/exception/rendition", 403, "Rendition Forbidden");
    }

    /**
     * A {@code Content-Type} that declares no boundary makes the body unparseable by definition, so the
     * failure is the caller's: 400, where before HXCS-8835 every multipart failure was 500. An empty boundary
     * value is the same thing said differently.
     */
    @Test
    public void testMultipartWithNoUsableBoundaryIsABadRequest() throws Exception {
        doTestMultipart("multipart/form-data", SC_BAD_REQUEST, "Failed to parse multipart servlet request");
        doTestMultipart("multipart/form-data; boundary=", SC_BAD_REQUEST, "Failed to parse multipart servlet request");
        // a header too malformed to read a boundary from is not this server's doing either
        doTestMultipart("multipart/form-data; boundary", SC_BAD_REQUEST, "Failed to parse multipart servlet request");
    }

    /**
     * The other half of the absent-cause branch. A missing request body is the caller's and answers 400; a
     * response the converters cannot write is this server's and answers 500, message replaced and cause logged
     * at ERROR. Before HXCS-8835 both reached an unconditional read of that absent cause and the advice itself
     * threw.
     */
    @Test
    public void testAResponseTheConvertersCannotWriteIsAServerError() throws Exception {
        doTestException("/exception/responseNotWritable", SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
    }

    /**
     * The counterpart, and the reason the classification asks the request rather than the exception: Spring
     * wraps a container I/O error, a full temp store and an internal parser fault in the very same
     * {@link MultipartException}. With a usable boundary in the header there is nothing to blame the caller
     * for, so it stays 500 -- opaque message, cause logged at ERROR -- and a client is not told to stop
     * retrying an outage.
     */
    @Test
    public void testMultipartFailureWithAUsableBoundaryStaysAServerError() throws Exception {
        doTestMultipart("multipart/form-data; boundary=----abc123", SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
    }

    protected void doTestMultipart(String contentType, int expectedStatus, String expectedMessage)
            throws Exception {
        String body = "{\"entity-type\":\"exception\",\"status\":%s,\"message\":\"%s\"}".formatted(expectedStatus,
                expectedMessage);
        mvc.perform(post("/exception/multipart").header(CONTENT_TYPE, contentType).with(csrf()))
           .andExpect(status().is(expectedStatus))
           .andExpect(content().string(body));
    }

    /**
     * The subclass that does carry a status has to keep it: an upload over the limit is 413 whatever the
     * header says, because Spring classifies that one itself.
     */
    @Test
    public void testMultipartTooLargeKeepsItsOwnStatus() throws Exception {
        mvc.perform(post("/exception/multipartTooLarge").header(CONTENT_TYPE,
                "multipart/form-data; boundary=----abc123").with(csrf()))
           .andExpect(status().is(SC_REQUEST_ENTITY_TOO_LARGE))
           .andExpect(content().string(
                   "{\"entity-type\":\"exception\",\"status\":413,\"message\":\"Maximum upload size of 4 bytes exceeded\"}"));
    }

    /**
     * A request that omits a body Spring requires -- or sends the JSON literal {@code null} -- is rejected by
     * the argument resolver, which throws {@code HttpMessageNotReadableException} with no cause: nothing was
     * parsed, so there is nothing to wrap. Before HXCS-8835 the handler dereferenced that absent cause, so the
     * advice itself threw and the caller got the container's error page instead of this API's error entity.
     */
    @Test
    public void testMissingRequiredBodyIsAnErrorEntity() throws Exception {
        mvc.perform(post("/exception/requiredBody").contentType(MediaType.APPLICATION_JSON)
                                                   .content("null")
                                                   .with(csrf()))
           .andExpect(status().is(SC_BAD_REQUEST))
           .andExpect(content().string(
                   "{\"entity-type\":\"exception\",\"status\":400,\"message\":\"Required request body is missing\"}"));
    }

    protected ResultActions doTestException(String path, int expectedStatus, String expectedMessage) throws Exception {
        String body = "{\"entity-type\":\"exception\",\"status\":%s,\"message\":\"%s\"}".formatted(expectedStatus,
                expectedMessage);
        return mvc.perform(get(path)).andExpect(status().is(expectedStatus)).andExpect(content().string(body));
    }

    protected void doTestExceptionAndHeaders(String path, int expectedStatus, String expectedMessage,
            Map<String, String> headers) throws Exception {
        ResultActions result = doTestException(path, expectedStatus, expectedMessage);
        for (var entry : headers.entrySet()) {
            result.andExpect(header().string(entry.getKey(), entry.getValue()));
        }
    }

    @Test
    public void testValidationException() throws Exception {
        String expected = """
                {
                  "errors": [
                    {
                      "message": "document \\\"foo\\\" not found.",
                      "messageKey": "label.schema.constraint.violation.resolver.documentResolver.linkfield",
                      "parameters": [
                        "foo"
                      ],
                      "invalidValue": "foo",
                      "path": "linkfield/0"
                    }
                  ]
                }""";
        doTestValidationException("/exception/validate", 422, expected);
        expected = """
                {
                  "errors": [
                    {
                      "message": "document \\\"foo\\\" not found.",
                      "messageKey": "label.schema.constraint.violation.resolver.documentResolver.linkfield",
                      "parameters": [
                        "foo"
                      ],
                      "invalidValue": "foo",
                      "path": "linkfield/0"
                    },
                    {
                      "message": "document \\\"foo2\\\" not found.",
                      "messageKey": "label.schema.constraint.violation.resolver.documentResolver.linkfield2",
                      "parameters": [
                        "foo2"
                      ],
                      "invalidValue": "foo2",
                      "path": "linkfield2/0"
                    }
                  ]
                }""";
        doTestValidationException("/exception/validateMultiple", 422, expected);
    }

    protected void doTestValidationException(String path, int expectedStatus, String expectedJsonBody)
            throws Exception {
        var response = mvc.perform(get(path)).andExpect(status().is(expectedStatus)).andReturn().getResponse();
        JSONAssert.assertEquals(expectedJsonBody, response.getContentAsString(), NON_EXTENSIBLE);
    }

    @Test
    public void testModelValidation() throws Exception {
        String expected = """
          {
            "errors":[
              {
                "message":"The value \\"foo\\" is a type recursion.",
                "messageKey":"label.schema.application.violation.TypeRecursionViolation",
                "parameters":[
                  "foo"
                ],
                "invalidValue": "foo",
                "path": "foo"
              }
            ]
          }""";
        doTestValidationException("/exception/validateModel", 422, expected);

    }
    @Test
    public void testModelValidationMultipleFields() throws Exception {

        String expected = """
          {
            "errors": [
                {
                    "message": "The value \\"foo\\" is a type recursion.",
                    "messageKey": "label.schema.application.violation.TypeRecursionViolation",
                    "parameters": [
                        "foo"
                    ],
                    "invalidValue": "foo",
                    "path": "foo"
                },
                {
                    "message": "The value \\"[bar, foo]\\" is an invalid supertype for the type \\"foo\\".",
                    "messageKey": "label.schema.application.violation.InvalidObjectViolation.types.supertypes",
                    "parameters": [
                        [
                            "bar",
                            "foo"
                        ],
                        "foo"
                    ],
                    "invalidValue": [
                        "bar","foo"
                    ],
                    "path": "foo"
                }
            ]
        }""";
        doTestValidationException("/exception/validateModelMultiple", 422, expected);
    }
}
