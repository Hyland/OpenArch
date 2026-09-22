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
package org.nuxeo.rest.exception;

import static jakarta.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static jakarta.ws.rs.core.HttpHeaders.RETRY_AFTER;
import static org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver.NAME_KEY;
import static org.skyscreamer.jsonassert.JSONCompareMode.NON_EXTENSIBLE;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.common.RetryAfterHint;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.RenditionException;
import org.nuxeo.ecm.core.api.local.DummyLoginFeature;
import org.nuxeo.ecm.core.api.validation.ConstraintViolation.PathNode;
import org.nuxeo.ecm.core.api.validation.ConstraintViolation;
import org.nuxeo.ecm.core.api.validation.DocumentValidationException;
import org.nuxeo.ecm.core.api.validation.DocumentValidationReport;
import org.nuxeo.ecm.core.model.DocumentModelResolver;
import org.nuxeo.ecm.core.schema.dto.DTOPath;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.FieldImpl;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.ObjectResolverConstraint;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver;
import org.nuxeo.ecm.core.schema.validation.SchemaValidationException;
import org.nuxeo.ecm.core.schema.validation.ValidationReport;
import org.nuxeo.ecm.core.schema.validation.violations.InvalidObjectViolation;
import org.nuxeo.ecm.core.schema.validation.violations.TypeRecursionViolation;
import org.nuxeo.ecm.platform.test.UserManagerMockFeature;
import org.nuxeo.ecm.platform.test.UserManagerMockTestConfiguration;
import org.nuxeo.runtime.springboot.junit.RuntimeSpringBootTest;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.transaction.TransactionRuntimeException;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;

/**
 * Unit test for {@link org.nuxeo.rest.exception.NuxeoExceptionController}
 */
@Features({ UserManagerMockFeature.class, DummyLoginFeature.class })
@Import(UserManagerMockTestConfiguration.class)
@SpringBootTest(classes = { NuxeoExceptionController.class, NuxeoExceptionControllerTest.ExceptionTestResource.class })
class NuxeoExceptionControllerTest extends RuntimeSpringBootTest {

    @RestController
    @EnableWebMvc
    @RequestMapping(path = "/exception", produces = MediaType.APPLICATION_JSON_VALUE)
    public static class ExceptionTestResource {

        @GetMapping("/nuxeo")
        public void nuxeo() {
            throw new NuxeoException("oops", 456);
        }

        @GetMapping("/serviceunavail")
        public void serviceunvail() {
            throw new NuxeoException("service unavail", SC_SERVICE_UNAVAILABLE);
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

        @GetMapping("/rendition")
        public void rendition() {
            throw new RenditionException("Rendition Forbidden", 403);
        }
    }

    static class RetryAfterHintException extends NuxeoException implements RetryAfterHint {

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
    public void testNuxeoException() throws Exception {
        doTestException("/exception/nuxeo", 456, "oops");
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
                Map.of(RETRY_AFTER, "2")); // NuxeoExceptionController.RETRY_AFTER_DEFAULT_DELAY
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
