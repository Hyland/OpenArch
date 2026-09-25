/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.hxpr.springboot.server;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.nullValue;

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
import com.hyland.openarch.rest.document.AbstractDocumentAPI;
import com.hyland.openarch.rest.query.QueryAPI;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * DocumentAPI and DocumentMultipartController are both mapped here and are told apart only by consumes, which
     * preflight ignores. Spring answers such an ambiguous preflight with a wildcard configuration as soon as every
     * matching handler carries its own CORS configuration, so this path is where a permissive answer would appear.
     */
    private static final String AMBIGUOUS_DOCUMENT_PATH = AbstractDocumentAPI.PATH + "/doc-id";

    public static Stream<String> getAllowOrigins() {
        return ALLOW_ORIGINS.stream();
    }

    public static Stream<String> getNotAllowOrigins() {
        return NOT_ALLOW_ORIGINS.stream();
    }

    public static Stream<Arguments> getNotAllowOriginsAndPaths() {
        return NOT_ALLOW_ORIGINS.stream()
                                .flatMap(origin -> Stream.of("/", AMBIGUOUS_DOCUMENT_PATH)
                                                         .map(path -> Arguments.of(origin, path)));
    }

    public static Stream<Arguments> getNotAllowOriginsAndPreflightTargets() {
        return NOT_ALLOW_ORIGINS.stream()
                                .flatMap(origin -> Stream.of(
                                        Arguments.of(origin, "/", HttpMethod.GET.name()),
                                        Arguments.of(origin, AMBIGUOUS_DOCUMENT_PATH, HttpMethod.PUT.name())));
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
    @MethodSource("getNotAllowOriginsAndPaths")
    public void corsRegexInvalidOriginTest(String origin, String path) {
        authenticated().header("Origin", origin)
                       .when()
                       .get(path)
                       .then()
                       .onFailMessage("Failed for origin: " + origin + " and path: " + path)
                       .statusCode(Status.FORBIDDEN.getStatusCode())
                       // asserted explicitly: a 403 alone could come from the handler rather than the CORS filter
                       .header("Access-Control-Allow-Origin", nullValue());
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
    @MethodSource("getNotAllowOriginsAndPreflightTargets")
    public void optionsMethodCallShouldReturn403WhenNotAuthenticatedAndNotAllowOrigin(String notAllowedOrigin,
            String path, String method) {
        given().header("Origin", notAllowedOrigin)
               .header("Access-Control-Request-Method", method)
               .when()
               .options(path)
               .then()
               .onFailMessage("Failed for origin: " + notAllowedOrigin + " and path: " + path)
               .statusCode(Status.FORBIDDEN.getStatusCode());
    }

    @Test
    public void optionsMethodCallToMethodOutsideCorsPolicyShouldReturn403WhenNotAuthenticatedAndAllowOrigin() {
        String allowedOrigin = ALLOW_ORIGINS.get(0);

        given().header("Origin", allowedOrigin)
               .header("Access-Control-Request-Method", HttpMethod.TRACE.name())
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
        // Both packages are scanned: the REST API lives under com.hyland.openarch.rest, the app's own controllers and the
        // enterprise ones (graph, evstore, ...) under com.hyland.hxpr. Only what is on the classpath is found.
        try (ScanResult scanResult = new ClassGraph().acceptPackages("com.hyland.openarch.rest", "com.hyland.hxpr")
                                                    .enableAllInfo()
                                                    .scan()) {
            return scanResult.getAllClasses()
                             .filter(classInfo -> classInfo.hasAnnotation(RestController.class))
                             .loadClasses();
        }
    }

    /**
     * A mapping that omits both {@code path} and {@code value} serves the root of its controller. It is represented
     * as an empty sub-path so those endpoints still produce a case instead of dropping out of the scan.
     */
    private static String[] pathsOf(String[] path, String[] value) {
        var paths = path.length == 0 ? value : path;
        return paths.length == 0 ? new String[] { "" } : paths;
    }

    private static String joinPaths(String classPath, String subPath) {
        // a root mapping takes no separator: the trailing slash would not match the handler
        var fullPath = subPath.isEmpty() ? classPath
                : classPath + (subPath.startsWith("/") ? subPath : "/" + subPath);
        fullPath = fullPath.replace("{", "").replace("}", "");
        return fullPath.isEmpty() ? "/" : fullPath;
    }

    private static void addRequestMethod(Map<String, Set<String>> methodsNames, String[] paths, String httpMethod) {
        for (var path : paths) {
            methodsNames.computeIfAbsent(path, key -> new HashSet<>()).add(httpMethod);
        }
    }

    /**
     * Maps every sub-path to <em>all</em> the verbs it exposes. Keying a single verb per path would leave the other
     * verbs on that path untested, which is how a rejected PUT preflight went unnoticed.
     */
    private static Map<String, Set<String>> getAllRequestMethods(Class<?> apiClass) {
        Map<String, Set<String>> methodsNames = new HashMap<>();
        for (var method : apiClass.getMethods()) {
            if (method.isAnnotationPresent(RequestMapping.class)) {
                var mapping = method.getAnnotation(RequestMapping.class);
                for (var methodAnno : mapping.method()) {
                    addRequestMethod(methodsNames, pathsOf(mapping.path(), mapping.value()), methodAnno.name());
                }
            }
            if (method.isAnnotationPresent(GetMapping.class)) {
                var mapping = method.getAnnotation(GetMapping.class);
                addRequestMethod(methodsNames, pathsOf(mapping.path(), mapping.value()), HttpMethod.GET.name());
            }
            if (method.isAnnotationPresent(PostMapping.class)) {
                var mapping = method.getAnnotation(PostMapping.class);
                addRequestMethod(methodsNames, pathsOf(mapping.path(), mapping.value()), HttpMethod.POST.name());
            }
            if (method.isAnnotationPresent(PutMapping.class)) {
                var mapping = method.getAnnotation(PutMapping.class);
                addRequestMethod(methodsNames, pathsOf(mapping.path(), mapping.value()), HttpMethod.PUT.name());
            }
            if (method.isAnnotationPresent(PatchMapping.class)) {
                var mapping = method.getAnnotation(PatchMapping.class);
                addRequestMethod(methodsNames, pathsOf(mapping.path(), mapping.value()), HttpMethod.PATCH.name());
            }
            if (method.isAnnotationPresent(DeleteMapping.class)) {
                var mapping = method.getAnnotation(DeleteMapping.class);
                addRequestMethod(methodsNames, pathsOf(mapping.path(), mapping.value()), HttpMethod.DELETE.name());
            }
        }
        return methodsNames;
    }

    private static Stream<Arguments> getApiPathsAndMethods() {
        List<Class<?>> apiClasses = getAllApiClasses();
        List<Arguments> apiClassesAndMethods = new ArrayList<>();
        for (var apiClass : apiClasses) {
            Map<String, Set<String>> methods = getAllRequestMethods(apiClass);
            // A controller without a class-level mapping serves its method paths unprefixed.
            var classMapping = apiClass.getAnnotation(RequestMapping.class);
            var pathMapping = classMapping == null ? new String[] { "" }
                    : pathsOf(classMapping.path(), classMapping.value());
            for (var path : pathMapping) {
                for (var mapping : methods.entrySet()) {
                    String fullPath = joinPaths(path, mapping.getKey());
                    for (var httpMethod : mapping.getValue()) {
                        apiClassesAndMethods.add(Arguments.of(fullPath, httpMethod));
                    }
                }
            }
        }
        return apiClassesAndMethods.stream();
    }

}
