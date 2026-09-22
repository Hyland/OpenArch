/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.runtime.test.runner;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opentest4j.TestAbortedException;
import com.hyland.openarch.common.function.ThrowableConsumer;
import org.nuxeo.runtime.RuntimeServiceException;

import com.google.inject.Module;

public class FeaturesLoader {

    private static final Logger log = LogManager.getLogger(FeaturesLoader.class);

    public enum Direction {
        FORWARD, BACKWARD
    }

    protected final Class<?> targetTestClass;

    protected static final AnnotationScanner scanner = new AnnotationScanner();

    public FeaturesLoader(Class<?> targetTestClass) {
        this.targetTestClass = targetTestClass;
    }

    private final Map<Class<? extends RunnerFeature>, RunnerFeature> index = new HashMap<>();

    private final List<RunnerFeature> features = new ArrayList<>();

    public void loadFeatures(Class<?> classToRun) throws Exception {
        scanner.scan(classToRun);
        // load required features from annotation
        List<Features> annos = scanner.getAnnotations(classToRun, Features.class);
        if (annos != null) {
            for (Features anno : annos) {
                for (Class<? extends RunnerFeature> cl : anno.value()) {
                    loadFeature(new HashSet<>(), cl);
                }
            }
        }
    }

    protected void loadFeature(HashSet<Class<?>> cycles, Class<? extends RunnerFeature> clazz) throws Exception {
        if (index.containsKey(clazz)) {
            return;
        }
        if (cycles.contains(clazz)) {
            throw new IllegalStateException("Cycle detected in features dependencies of " + clazz);
        }
        cycles.add(clazz);
        scanner.scan(clazz);
        // load required features from annotation
        List<Features> annos = scanner.getAnnotations(clazz, Features.class);
        if (annos != null) {
            for (Features anno : annos) {
                for (Class<? extends RunnerFeature> cl : anno.value()) {
                    loadFeature(cycles, cl);
                }
            }
        }
        RunnerFeature feature = clazz.getDeclaredConstructor().newInstance();
        features.add(feature);
        index.put(clazz, feature);
    }

    public Collection<RunnerFeature> getFeatures() {
        return features;
    }

    @SuppressWarnings("unchecked")
    public <T extends RunnerFeature> T getFeature(Class<T> aType) {
        return (T) index.get(aType);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Module onModule() {
        return aBinder -> {
            for (RunnerFeature feature : features) {
                feature.configure(aBinder);
                aBinder.bind((Class) feature.getClass()).toInstance(feature);
                aBinder.requestInjection(feature);
            }
        };
    }

    /**
     * @since 5.6
     */
    public <T extends Annotation> T getConfig(Class<T> type) {
        List<T> configs = new ArrayList<>();
        T annotation = scanner.getAnnotation(getTargetTestClass(), type);
        if (annotation != null) {
            configs.add(annotation);
        }
        apply("getAnnotation", Direction.BACKWARD, feature -> {
            T hAnnotation = scanner.getAnnotation(feature.getClass(), type);
            if (hAnnotation != null) {
                configs.add(hAnnotation);
            }
        });
        return Defaults.of(type, configs);
    }

    /**
     * Get the annotation on the test method, if no annotation has been found, get the annotation from the test class
     * (See {@link #getConfig(Class)})
     *
     * @since 5.7
     */
    public <T extends Annotation> T getConfig(Method method, Class<T> type) {
        T config = method.getAnnotation(type);
        if (config != null) {
            return config;
        }
        // if not define, try to get the config of the class
        return getConfig(type);
    }

    /**
     * @since 11.1
     */
    public void apply(String id, Direction direction, ThrowableConsumer<RunnerFeature, Exception> consumer) {
        apply(id, direction == Direction.FORWARD ? features : features.reversed(), consumer);
    }

    /**
     * @since 11.1
     */
    public void apply(String id, Collection<RunnerFeature> features,
            ThrowableConsumer<RunnerFeature, Exception> consumer) {
        log.trace("Invoke: {} on features: {}", () -> id, () -> formatFeatures(features));
        Supplier<String> msg = () -> String.format("Error while invoking %s on features: %s", id,
                formatFeatures(features));
        var errors = new ArrayList<Throwable>();
        for (RunnerFeature feature : features) {
            try {
                consumer.accept(feature);
            } catch (TestAbortedException cause) {
                log.debug("Test ignored by the feature: {}", feature.getClass());
                throw cause;
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
                throw new RuntimeServiceException(msg.get(), cause);
            } catch (Throwable cause) { // NOSONAR
                log.debug("Error while invoking: {} on feature: {}", id, feature.getClass());
                throwSeriousError(cause);
                errors.add(cause);
            }
        }
        if (!errors.isEmpty()) {
            var exception = new AssertionError(msg.get());
            errors.forEach(exception::addSuppressed);
            throw exception;
        }
    }

    protected String formatFeatures(Collection<RunnerFeature> features) {
        return features.stream().map(f -> f.getClass().getName()).collect(Collectors.joining(", ", "[", "]"));
    }

    protected void throwSeriousError(Throwable cause) {
        if (cause instanceof Error error && !(cause instanceof AssertionError)) {
            throw error; // serious error, rethrow immediately
        }
    }

    public AnnotationScanner getScanner() {
        return scanner;
    }

    public Class<?> getTargetTestClass() {
        return targetTestClass;
    }

}
