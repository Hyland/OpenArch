/*
 * (C) Copyright 2023 Hyland (http://www.hyland.com/) and others.
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
 */
package com.hyland.hxpr.springboot.server;

import static io.restassured.RestAssured.given;

import com.hyland.hxpr.springboot.AbstractSpringTest;
import com.hyland.hxpr.springboot.server.config.CorsPolicyConfiguration;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.nuxeo.rest.query.QueryAPI;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Import(CorsPolicyConfiguration.class)
@TestPropertySource(properties = {
        "hxpr.http.cors.origins=https://*.studio.*.experience.hyland.com,https://localhost:9081",
        "feature.flag.default.flags.content-context-graph-api=true"})
public class CorsPolicyTest extends AbstractSpringTest {

    private static final List<String> ALLOW_ORIGINS = List.of("https://hxp-inc.studio.dev.experience.hyland.com",
            "https://clothestore.studio.dev.experience.hyland.com",
            "https://child-hospital.studio.dev.experience.hyland.com",
            "https://candy-shop.studio.dev.experience.hyland.com",
            "https://localhost:9081");

    private static final List<String> NOT_ALLOW_ORIGINS = List.of("https://clothestore.com.pt",
            "https://clothestore.studio.dev.experience.rocket.com",
            "https://localhost:4200",
            "https://hxp-inc.studio.dev.test.hyland.com");

    public static Stream<String> getAllowOrigins() {
        return ALLOW_ORIGINS.stream();
    }

    public static Stream<String> getNotAllowOrigins() {
        return NOT_ALLOW_ORIGINS.stream();
    }

    @ParameterizedTest
    @MethodSource("getAllowOrigins")
    public void corsRegexValidOriginTest(String origin) {
        authenticated().header("Origin", origin)
                       .when()
                       .get("/")
                       .then()
                       .onFailMessage("Failed for origin: " + origin)
                       .statusCode(Status.OK.getStatusCode())
                       .header("Access-Control-Allow-Origin", origin);

    }

    @ParameterizedTest
    @MethodSource("getNotAllowOrigins")
    public void corsRegexInvalidOriginTest(String origin) {
        authenticated().header("Origin", origin)
                       .when()
                       .get("/")
                       .then()
                       .onFailMessage("Failed for origin: " + origin)
                       .statusCode(Status.FORBIDDEN.getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = { "/", "/v", QueryAPI.PATH + "/named" })
    public void optionsMethodCallShouldReturn200WhenNotAuthenticatedAndAllowOrigin(String path) {
        String allowedOrigin = ALLOW_ORIGINS.get(0);

        given().header("Origin", allowedOrigin)
               .header("Access-Control-Request-Method", "GET")
               .when()
               .options(path)
               .then()
               .statusCode(Status.OK.getStatusCode());
    }

    @ParameterizedTest
    @MethodSource("getNotAllowOrigins")
    public void optionsMethodCallShouldReturn403WhenNotAuthenticatedAndNotAllowOrigin(String notAllowedOrigin) {
        given().header("Origin", notAllowedOrigin)
               .header("Access-Control-Request-Method", "GET")
               .when()
               .options("/")
               .then()
               .statusCode(Status.FORBIDDEN.getStatusCode());
    }

    @Test
    public void optionsMethodCallToUnknownMethodShouldReturn403WhenNotAuthenticatedAndAllowOrigin() {
        String allowedOrigin = ALLOW_ORIGINS.get(0);

        given().header("Origin", allowedOrigin)
               .header("Access-Control-Request-Method", HttpMethod.PATCH.name())
               .when()
               .options(QueryAPI.PATH + "/named")
               .then()
               .statusCode(Status.FORBIDDEN.getStatusCode());
    }

    @Test
    public void optionsMethodCallShould401WhenNotAuthenticatedAndNoOriginDetails() {
        given().when()
               .options("/")
               .then()
               .statusCode(Status.UNAUTHORIZED.getStatusCode());
    }

    @ParameterizedTest
    @MethodSource("getApiPathsAndMethods")
    public void optionsMethodCallsForDocumentAPIShouldReturnSuccess(String path, String method) {
        given().header("Origin", ALLOW_ORIGINS.get(0))
               .header("Access-Control-Request-Method", method)
               .when()
               .options(path)
               .then()
               .statusCode(Status.OK.getStatusCode());
    }

    private static List<Class<?>> getAllApiClasses() {
        try (ScanResult scanResult = new ClassGraph().acceptPackages("org.nuxeo.rest").enableAllInfo().scan()) {
            return scanResult.getAllClasses()
                             .filter(classInfo -> classInfo.hasAnnotation(RestController.class))
                             .loadClasses();
        }
    }

    private static Map<String, String> getAllRequestMethods(Class<?> apiClass) {
        Map<String, String> methodsNames = new HashMap<>();
        for (var method : apiClass.getMethods()) {
            if (method.isAnnotationPresent(RequestMapping.class)) {
                var mapping = method.getAnnotation(RequestMapping.class);
                var pathMapping = mapping.path().length == 0 ? mapping.value() : mapping.path();
                for (var path : pathMapping) {
                    for (var methodAnno : mapping.method()) {
                        methodsNames.put(path, methodAnno.name());
                    }
                }
            }
            if (method.isAnnotationPresent(GetMapping.class)) {
                var mapping = method.getAnnotation(GetMapping.class);
                var pathMapping = mapping.path().length == 0 ? mapping.value() : mapping.path();
                for (var path : pathMapping) {
                    methodsNames.put(path, HttpMethod.GET.name());
                }
            }
            if (method.isAnnotationPresent(PostMapping.class)) {
                var mapping = method.getAnnotation(PostMapping.class);
                var pathMapping = mapping.path().length == 0 ? mapping.value() : mapping.path();
                for (var path : pathMapping) {
                    methodsNames.put(path, HttpMethod.POST.name());
                }
            }
            if (method.isAnnotationPresent(PutMapping.class)) {
                var mapping = method.getAnnotation(PutMapping.class);
                var pathMapping = mapping.path().length == 0 ? mapping.value() : mapping.path();
                for (var path : pathMapping) {
                    methodsNames.put(path, HttpMethod.PUT.name());
                }
            }
            if (method.isAnnotationPresent(PatchMapping.class)) {
                var mapping = method.getAnnotation(PatchMapping.class);
                var pathMapping = mapping.path().length == 0 ? mapping.value() : mapping.path();
                for (var path : pathMapping) {
                    methodsNames.put(path, HttpMethod.PATCH.name());
                }
            }
            if (method.isAnnotationPresent(DeleteMapping.class)) {
                var mapping = method.getAnnotation(DeleteMapping.class);
                var pathMapping = mapping.path().length == 0 ? mapping.value() : mapping.path();
                for (var path : pathMapping) {
                    methodsNames.put(path, HttpMethod.DELETE.name());
                }
            }
        }
        return methodsNames;
    }

    private static Stream<Arguments> getApiPathsAndMethods() {
        List<Class<?>> apiClasses = getAllApiClasses();
        List<Arguments> apiClassesAndMethods = new ArrayList<>();
        for (var apiClass : apiClasses) {
            Map<String, String> methods = getAllRequestMethods(apiClass);
            var pathMapping = apiClass.getAnnotation(RequestMapping.class).path();
            pathMapping = pathMapping.length == 0 ? apiClass.getAnnotation(RequestMapping.class).value() : pathMapping;
            for (var path : pathMapping) {
                for (var method : methods.entrySet()) {
                    String fullPath = path
                            + (method.getKey().startsWith("/") ? method.getKey() : "/" + method.getKey());
                    fullPath = fullPath.replace("{", "").replace("}", "");
                    apiClassesAndMethods.add(Arguments.of(fullPath, method.getValue()));
                }
            }
        }
        return apiClassesAndMethods.stream();
    }

}
