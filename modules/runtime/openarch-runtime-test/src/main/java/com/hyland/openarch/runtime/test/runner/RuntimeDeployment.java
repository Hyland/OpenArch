/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.test.runner;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hyland.openarch.runtime.model.ComponentManager;
import org.osgi.framework.Bundle;

import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class RuntimeDeployment {

    Set<String> bundles = new HashSet<>();

    Map<String, Collection<String>> mainContribs = new HashMap<>();

    SetMultimap<String, String> mainIndex = Multimaps.newSetMultimap(mainContribs, LinkedHashSet::new);

    protected void index(AnnotationScanner scanner, Class<?> clazz) {
        scanner.scan(clazz);
        List<? extends Annotation> annos = scanner.getAnnotations(clazz);
        if (annos == null) {
            return;
        }
        for (Annotation anno : annos) {
            if (anno.annotationType() == Deploy.class) {
                index((Deploy) anno);
            } else if (anno.annotationType() == Deploys.class) {
                index((Deploys) anno);
            }
        }
    }

    protected void index(AnnotationScanner scanner, RunnerFeature feature) {
        index(scanner, feature.getClass());
    }

    protected void index(Method method) {
        index(method.getAnnotation(Deploy.class));
        index(method.getAnnotation(Deploys.class));
    }

    protected void index(Deploy config) {
        if (config == null) {
            return;
        }
        for (String each : config.value()) {
            index(each, mainIndex);
        }
    }

    private void index(Deploys deploys) {
        if (deploys == null) {
            return;
        }
        for (Deploy value : deploys.value()) {
            index(value);
        }
    }

    protected void index(AnnotationScanner scanner, Features features) {
        for (Class<?> each : features.value()) {
            index(scanner, each);
        }
    }

    protected void index(String directive, SetMultimap<String, String> contribs) {
        int sepIndex = directive.indexOf(':');
        if (sepIndex == -1) {
            bundles.add(directive);
        } else {
            String bundle = directive.substring(0, sepIndex);
            String resource = directive.substring(sepIndex + 1);
            contribs.put(bundle, resource);
        }
    }

    protected void deploy(FeaturesLoader loader, RuntimeHarness harness) { // NOSONAR
        AssertionError errors = new AssertionError("deployment errors");
        for (String name : bundles) {
            Bundle bundle = harness.getOSGiAdapter().getBundle(name);
            if (bundle == null) {
                try {
                    harness.deployBundle(name);
                    bundle = harness.getOSGiAdapter().getBundle(name);
                    if (bundle == null) {
                        throw new UnsupportedOperationException("Should not occur");
                    }
                } catch (Exception error) {
                    errors.addSuppressed(error);
                    continue;
                }
            }
            try {
                // deploy bundle contribs
                for (String resource : mainIndex.removeAll(name)) {
                    try {
                        harness.deployContrib(name, resource);
                    } catch (Exception error) {
                        errors.addSuppressed(error);
                    }
                }
            } catch (Exception error) {
                errors.addSuppressed(error);
            }
        }

        for (Map.Entry<String, String> resource : mainIndex.entries()) {
            try {
                harness.deployContrib(resource.getKey(), resource.getValue());
            } catch (Exception error) {
                errors.addSuppressed(error);
            }
        }

        if (errors.getSuppressed().length > 0) {
            throw errors;
        }

    }

    public static RuntimeDeployment onTest(FeaturesLoader loader) {
        RuntimeDeployment deployment = new RuntimeDeployment();
        deployment.index(loader.getScanner(), loader.getTargetTestClass());
        for (RunnerFeature each : loader.getFeatures()) {
            deployment.index(loader.getScanner(), each);
        }
        return deployment;
    }

    public static void onMethod(FeaturesLoader loader, Method method) {
        RuntimeDeployment deployment = new RuntimeDeployment();
        deployment.index(method);
        deployment.deploy(loader, loader.getFeature(RuntimeFeature.class).harness, method);
    }

    protected void deploy(FeaturesLoader loader, RuntimeHarness harness, Method method) { // NOSONAR
        // the registry is updated here and not using before or teardown methods.
        // this approach ensure the components are not stopped between tearDown and the next test
        // (so that custom feature that rely on the runtime between the two tests are not affected by stopping
        // components)
        ComponentManager mgr = harness.getContext().getRuntime().getComponentManager();
        // the stash may already contains contribs (from @Setup methods)
        if (mgr.hasChanged()) { // first reset the registry if it was changed by the last test
            mgr.reset();
            // the registry is now stopped
        }
        // deploy current test contributions if any
        deploy(loader, harness);
        mgr.refresh();
        // now the stash is empty
        mgr.start(); // ensure components are started
    }

}
