/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.mockito;

import java.lang.annotation.Annotation;
import java.util.Map;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.mockito.Mock;
import org.mockito.internal.configuration.FieldAnnotationProcessor;
import org.mockito.internal.configuration.IndependentAnnotationEngine;
import org.mockito.internal.configuration.InjectingAnnotationEngine;
import org.mockito.plugins.AnnotationEngine;

/**
 * Custom Mockito {@link AnnotationEngine} that registers the Nuxeo specific
 * {@link NuxeoServiceMockAnnotationProcessor} for {@link Mock} annotated fields, so that mocks
 * created by Mockito are automatically published to the Nuxeo service registry.
 * <p>
 * Replaces the former {@code org.mockito.configuration.MockitoConfiguration#getAnnotationEngine()}
 * override (removed in Mockito 5). Registered through the Mockito plugin SPI in
 * {@code META-INF/mockito-extensions/org.mockito.plugins.AnnotationEngine}.
 */
public class HxprAnnotationEngine extends InjectingAnnotationEngine {

    public static final String DELEGATE_FIELD_NAME = "delegate";

    public static final String ANNOTATION_PROCESSOR_MAP_FIELD_NAME = "annotationProcessorMap";

    public HxprAnnotationEngine() {
        // InjectingAnnotationEngine internally uses an IndependentAnnotationEngine as 'delegate'
        // to handle field-level annotations such as @Mock. We swap the default @Mock processor
        // with our Nuxeo-aware one via reflection (no public API is exposed for that).
        try {
            Object delegate = FieldUtils.readField(this, DELEGATE_FIELD_NAME, true);
            if (!(delegate instanceof IndependentAnnotationEngine independent)) {
                throw new IllegalStateException("Unexpected Mockito delegate annotation engine: "
                        + (delegate == null ? "null" : delegate.getClass().getName()));
            }
            @SuppressWarnings("unchecked")
            Map<Class<? extends Annotation>, FieldAnnotationProcessor<?>> processors =
                    (Map<Class<? extends Annotation>, FieldAnnotationProcessor<?>>) FieldUtils.readField(
                            independent, ANNOTATION_PROCESSOR_MAP_FIELD_NAME, true);
            processors.put(Mock.class, new NuxeoServiceMockAnnotationProcessor());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Unable to install NuxeoServiceMockAnnotationProcessor into Mockito's InjectingAnnotationEngine",
                    e);
        }
    }
}
