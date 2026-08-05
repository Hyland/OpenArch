/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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

package org.nuxeo.rest.archtest;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static jakarta.ws.rs.core.MediaType.WILDCARD;
import static org.nuxeo.rest.utils.Elements.DESCRIPTION;
import static org.nuxeo.rest.utils.Elements.NAME;
import static org.nuxeo.rest.utils.Elements.SPRING_REQUESTMAPPING;
import static org.nuxeo.rest.utils.Elements.TAG;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_OCTET_STREAM;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.RequestMapping;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.lang.syntax.elements.GivenClassesConjunction;
import com.tngtech.archunit.lang.syntax.elements.GivenMethodsConjunction;

public class ArchtestHelpers {
    public static final List<String> HXCS_JSON_REQUIRED_COMPAT_MEDIATYPES_FOR_PRODUCES = List.of(APP_HXCS_JSON,
            APPLICATION_JSON);

    public static final List<String> HXCS_OCTET_STREAM_REQUIRED_COMPAT_MEDIATYPES_FOR_PRODUCES = List.of(
            APP_HXCS_OCTET_STREAM, APPLICATION_OCTET_STREAM);

    public static final List<String> HXCS_JSON_REQUIRED_COMPAT_MEDIATYPES_FOR_CONSUMES = List.of(WILDCARD,
            APPLICATION_JSON);

    public static final List<String> HXCS_TAG_REQUIRED_ELEMENTS = List.of(NAME, DESCRIPTION);

    static final List<String> REQUEST_MAPPING_STR = Arrays.asList("RequestMapping", "GetMapping", "PostMapping",
            "PutMapping", "DeleteMapping", "PatchMapping");

    public static final String API_RESPONSE = "ApiResponse";

    public static final String RESPONSE_CODE = "responseCode";

    public static final ArchCondition<JavaClass> acceptJsonCompatMediaTypesForProduces = containsListOfMediaTypes(
            HXCS_JSON_REQUIRED_COMPAT_MEDIATYPES_FOR_PRODUCES, RequestMapping.class);

    public static final ArchCondition<JavaClass> acceptOctetStreamCompatMediaTypesForProduces = containsListOfMediaTypes(
            HXCS_OCTET_STREAM_REQUIRED_COMPAT_MEDIATYPES_FOR_PRODUCES, RequestMapping.class);

    public static final ArchCondition<JavaClass> containMandatoryTagElements = containsMandatoryTagElements();

    public static final ArchCondition<JavaClass> containsProduces = containsProduces(
            HXCS_JSON_REQUIRED_COMPAT_MEDIATYPES_FOR_CONSUMES, RequestMapping.class);

    protected static ArchCondition<JavaClass> containsListOfMediaTypes(List<String> types,
            Class<? extends Annotation> classType) {
        return new ArchCondition<>("contain %s in @" + classType + " annotation", types) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {

                if (!item.isAnnotatedWith(classType)) {
                    events.add(SimpleConditionEvent.violated(item,
                            " class %s is not annotated with %s".formatted(item.getName(), classType)));
                } else {
                    switch (classType.getName()) {
                    case SPRING_REQUESTMAPPING -> {
                        RequestMapping annotationOfType = item.getAnnotationOfType(RequestMapping.class);
                        if (!Arrays.asList(annotationOfType.produces()).containsAll(types)) {
                            events.add(SimpleConditionEvent.violated(item,
                                    "On class %s, Produces Media-Type is %s and must contain %s".formatted(
                                            item.getName(), Arrays.toString(annotationOfType.value()), types)));
                        }
                    }
                    default -> events.add(SimpleConditionEvent.violated(item,
                            "On class %s, must contain Produces/Consumes Media-Type and must contain %s".formatted(
                                    item.getName(), types)));
                    }

                }
            }
        };
    }

    protected static ArchCondition<JavaClass> containsProduces(List<String> types,
            Class<? extends Annotation> classType) {
        return new ArchCondition<>("contain %s in @" + classType + " annotation", types) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {

                if (!item.isAnnotatedWith(classType)) {
                    events.add(SimpleConditionEvent.violated(item,
                            " class %s is not annotated with %s".formatted(item.getName(), classType)));
                } else {
                    if (item.getAnnotationOfType(RequestMapping.class).produces().length == 0) {
                        events.add(SimpleConditionEvent.violated(item,
                                " class %s of type %s does not define produces".formatted(item.getName(), classType)));
                    }
                }
            }
        };
    }

    public static GivenClassesConjunction endpoints() {
        return ArchRuleDefinition.classes().that().areAnnotatedWith(RequestMapping.class);
    }

    public static ArchCondition<JavaMethod> containDescriptionResponseCodeConditions(
            Class<? extends Annotation> annotation) {
        return new ArchCondition<JavaMethod>("provide response code and description ", (Object) null) {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean satisfied = method.isAnnotatedWith(annotation)
                        || isAnnotatedViaSupertypeOfResponseApiandHasDescriptionAndResponseCode(method);
                String message = method.getFullName() + " is " + (satisfied ? "" : "not ") + " annotated with @"
                        + annotation.getSimpleName() + " or a subtype of it and has a description and response code";
                events.add(new SimpleConditionEvent(method, satisfied, message));
            }

            int annotationSearchDepth = 0;

            void getAllAnnotations(JavaClass javaClass, Set<JavaAnnotation<?>> annotationSet) {
                assignableTo(Object.class).test(javaClass);
                if (!javaClass.getAnnotations().isEmpty() && annotationSearchDepth < 5) {
                    annotationSearchDepth++;
                    javaClass.getAnnotations()
                             .stream()
                             .filter(annotation -> annotation.getRawType().getName().contains(API_RESPONSE))
                             .collect(Collectors.toSet())
                             .forEach(annotation -> {
                                 annotationSet.add(annotation);
                                 getAllAnnotations(annotation.getRawType(), annotationSet);
                             });
                }
            }

            boolean isAnnotatedViaSupertypeOfResponseApiandHasDescriptionAndResponseCode(JavaMethod method) {
                boolean result = true, methodAnnotatedWithApiResponse = false;
                if (method.isAnnotatedWith(ApiResponse.class)) {
                    return !method.getAnnotationOfType(ApiResponse.class).description().isBlank()
                            && !method.getAnnotationOfType(ApiResponse.class).responseCode().isBlank();
                } else {
                    Set<JavaAnnotation<?>> annotationSet;
                    for (JavaAnnotation<?> javaClass : method.getAnnotations()) {
                        if (javaClass.getRawType().getName().contains(API_RESPONSE)) {
                            methodAnnotatedWithApiResponse = true;
                            annotationSet = new HashSet<>();
                            annotationSet.add(javaClass);
                            annotationSearchDepth = 0;
                            getAllAnnotations(javaClass.getRawType(), annotationSet);
                            result = result && checkAnnotationSet(annotationSet);
                        }
                    }
                }
                return result && methodAnnotatedWithApiResponse;
            }

            private boolean checkAnnotationSet(Set<JavaAnnotation<?>> annotationSet) {
                boolean apiResponsePresent = false, descriptionPresent = false, responseCodePresent = false;
                for (JavaAnnotation<?> annotation : annotationSet) {
                    if (annotation.getRawType().getName().equals(ApiResponse.class.getName())) {
                        apiResponsePresent = true;
                    }
                    if (!descriptionPresent) {
                        descriptionPresent = hasDescription(annotation);
                    }
                    if (!responseCodePresent) {
                        responseCodePresent = hasResponseCode(annotation);
                    }
                }
                return apiResponsePresent && descriptionPresent && responseCodePresent;
            }

            boolean hasDescription(JavaAnnotation<?> javaAnnotation) {
                if (javaAnnotation.get(DESCRIPTION).isPresent()) {
                    return !javaAnnotation.get(DESCRIPTION).get().toString().isBlank();
                }
                return false;
            }

            boolean hasResponseCode(JavaAnnotation<?> javaAnnotation) {
                if (javaAnnotation.get(RESPONSE_CODE).isPresent()) {
                    return !javaAnnotation.get(RESPONSE_CODE).get().toString().isBlank();
                }
                return false;
            }
        };
    }

    public static GivenMethodsConjunction method_endpoints() {
        return methods().that(new DescribedPredicate<>("are annotated with either of these %s",
                REQUEST_MAPPING_STR.stream().map(s -> "@" + s).toList()) {

            @Override
            public boolean test(JavaMethod input) {
                boolean status = false;
                final String prefix = "org.springframework.web.bind.annotation.";
                for (String httpVerb : REQUEST_MAPPING_STR) {
                    if (input.isAnnotatedWith(prefix + httpVerb) && !input.isAnnotatedWith(Hidden.class)) {
                        status = true;
                        break;
                    }
                }
                return status;
            }
        });
    }

    private static String prepareMethodName(JavaMethod javaMethod) {
        return javaMethod.getOwner().getFullName() + "." + javaMethod.getName();
    }

    /**
     * Checks only one endpoint is not hidden for all endpoints using the same PATH.
     * <p>
     * If multiple endpoints are sharing the same PATH value, only one endpoint should be exposed and rest of them
     * should be marked as hidden. This is specifically important when newer versions of the API are getting exposed.
     */
    public static final ArchCondition<JavaMethod> ensureOldEndpointIsHidden = new ArchCondition<>(
            "ensure old endpoint is hidden ", (Object) null) {
        private final List<String> alreadyTestedMethodNames = new LinkedList<>();

        private List<String> prepareMethodNames(List<JavaMethod> javaMethods) {
            return javaMethods.stream().map(ArchtestHelpers::prepareMethodName).toList();
        }

        private String getHttpAnnotation(JavaMethod method) {
            final String prefix = "org.springframework.web.bind.annotation.";
            String httpMethod = "";
            for (String httpVerb : REQUEST_MAPPING_STR) {
                if (method.isAnnotatedWith(prefix + httpVerb)) {
                    httpMethod = httpVerb;
                    break;
                }
            }
            return prefix + httpMethod;
        }

        private List<JavaMethod> getEqualRESTEndpoints(JavaMethod method, String pathValue, String finalHttpMethod) {
            return method.getOwner().getAllMethods().stream().filter(javaMethod -> {
                if (javaMethod.isAnnotatedWith(finalHttpMethod)
                        && javaMethod.getAnnotationOfType(finalHttpMethod).get("path").isPresent()) {
                    return pathValue.equals(
                            javaMethod.getAnnotationOfType(finalHttpMethod).get("path").get().toString());
                }
                return false;
            }).toList();
        }

        @Override
        public void check(JavaMethod method, ConditionEvents events) {
            String finalHttpMethod = getHttpAnnotation(method);
            JavaAnnotation<JavaMethod> path = method.getAnnotationOfType(finalHttpMethod);
            String pathValue = String.valueOf(path.get("path"));
            List<JavaMethod> equalRESTEndpoints = getEqualRESTEndpoints(method, pathValue, finalHttpMethod);
            if (equalRESTEndpoints.size() > 1) {
                equalRESTEndpoints.forEach(javaMethod -> {
                    String javaMethodName = prepareMethodName(javaMethod);
                    if (alreadyTestedMethodNames.stream().noneMatch(s -> s.equals(javaMethodName))) {
                        alreadyTestedMethodNames.addAll(prepareMethodNames(equalRESTEndpoints));
                        long hiddenCount = equalRESTEndpoints.stream()
                                                             .map(m -> m.getAnnotationOfType(Operation.class))
                                                             .filter(Operation::hidden)
                                                             .count();
                        if (hiddenCount != equalRESTEndpoints.size() - 1) {
                            events.add(SimpleConditionEvent.violated(method,
                                    "Within these methods %s, Old endpoints must be hidden. @Operation".formatted(
                                            prepareMethodNames(equalRESTEndpoints))
                                            + "annotation should be properly defined."));
                        }
                    }
                });
            }
        }
    };

    protected static ArchCondition<JavaClass> containsMandatoryTagElements() {
        return new ArchCondition<>("contain  @Tag annotation") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {

                if (!item.isAnnotatedWith(Tag.class)) {
                    events.add(SimpleConditionEvent.violated(item,
                            " class %s is not annotated with %s".formatted(item.getName(), TAG)));
                } else {
                    Tag annotationOfType = item.getAnnotationOfType(Tag.class);
                    if (annotationOfType.name().isBlank() || annotationOfType.description().isBlank()) {
                        events.add(SimpleConditionEvent.violated(item,
                                "On class %s, Tag name and description elements are \"%s\" and \"%s\" and must contain proper values for %s".formatted(
                                        item.getName(), annotationOfType.name(), annotationOfType.description(),
                                        ArchtestHelpers.HXCS_TAG_REQUIRED_ELEMENTS)));
                    }
                }
            }
        };
    }

    public static final ArchCondition<JavaMethod> containParameterDescription = new ArchCondition<>(
            "provide parameter description ", (Object) null) {
        @Override
        public void check(JavaMethod method, ConditionEvents events) {
            method.getParameters().forEach(p -> {
                if (p.isAnnotatedWith(Parameter.class)) {
                    Parameter param = p.getAnnotationOfType(Parameter.class);
                    if (!param.hidden() && param.description().isBlank()) {
                        events.add(SimpleConditionEvent.violated(method,
                                "On parameters in method %s annotated with @Parameter, Description is blank. Please provide description in the Parameter annotation.".formatted(
                                        method.getName())));
                    }
                }
            });
        }
    };

    public static final ArchCondition<JavaMethod> operationContainsOperationId = new ArchCondition<>(
            "provide operationId ", (Object) null) {
        @Override
        public void check(JavaMethod method, ConditionEvents events) {
            Operation operation = method.getAnnotationOfType(Operation.class);

            if (operation.operationId().isBlank()) {
                events.add(SimpleConditionEvent.violated(method,
                        "On method %s annotated with @Operation, operationId is blank. Please provide operationId in the operation annotation.".formatted(
                                method.getName())));
            }
        }
    };
}
