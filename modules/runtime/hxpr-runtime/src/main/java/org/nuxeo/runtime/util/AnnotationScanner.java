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
package org.nuxeo.runtime.util;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class AnnotationScanner {
    // Search cache
    protected final CopyOnWriteArrayList<Class<? extends Annotation>> typeSearches = new CopyOnWriteArrayList<>();

    // Results cache
    protected final Map<Class<?>, List<Annotation>> classes = new ConcurrentHashMap<>();

    protected final ClassGraph classScanner;

    /**
     * @param packageNames The fully-qualified names of packages to scan (using '.' as a separator). May include a glob
     *            wildcard ('*').
     */
    public AnnotationScanner(String... packageNames) {
        this.classScanner = new ClassGraph().enableClassInfo().enableAnnotationInfo().acceptPackages(packageNames);
    }

    /**
     * @param annotationType
     * @return The set of classes annotated with the supplied class
     */
    public Set<Class<?>> getTypesAnnotatedWith(Class<? extends Annotation> annotationType) {
        // check the cache for the search
        if (typeSearched(annotationType)) {
            return getCachedTypesAnnotatedWith(annotationType);
        }

        // find all the classes that have the supplied annotation
        try (ScanResult scanResult = classScanner.scan()) {
            Set<Class<?>> result = new HashSet<>();
            result = scanResult.getClassesWithAnnotation(annotationType)
                               .stream()
                               .flatMap((classInfo -> extractClass(classInfo).stream()))
                               .collect(Collectors.toSet());
            typeSearches.addIfAbsent(annotationType);
            return result;
        }

    }

    protected List<Class<?>> extractClass(ClassInfo classInfo) {
        // Cannot use classInfo.loadClass() as many of the java Reflection methods do not work as expected
        List<Class<?>> annotatedClasses = new ArrayList<>();
        try {
            Class<?> clazz = Class.forName(classInfo.getName());
            // We don't cache the ClassInfo class as it becomes un-usable once the scan is closed
            classes.putIfAbsent(clazz, Arrays.asList(clazz.getAnnotations()));
            annotatedClasses.add(clazz);
        } catch (ClassNotFoundException e) {
            // Shouldn't get here but if it does just ignore and continue.
        }
        return annotatedClasses;
    }

    protected boolean typeSearched(Class<? extends Annotation> annotationType) {
        return typeSearches.stream().anyMatch(entry -> entry.equals(annotationType));
    }

    /**
     * @param annotationType
     * @return All classes from the cache where their annotation list contains the provided annotation
     */
    protected Set<Class<?>> getCachedTypesAnnotatedWith(Class<? extends Annotation> annotationType) {
        return classes.entrySet()
                      .stream()
                      .filter(e -> e.getValue()
                                    .stream()
                                    .anyMatch(annotation -> annotation.annotationType().equals(annotationType)))
                      .map(Entry::getKey)
                      .collect(Collectors.toSet());
    }

}
