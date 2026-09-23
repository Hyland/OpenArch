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

package com.hyland.openarch.runtime.test.runner;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Feature handling {@link WithApplicationProperties} annotations.
 *
 * @since 11.1
 */
public class WithApplicationPropertiesFeature implements RunnerFeature {

    protected FeaturesLoader loader;

    protected List<Properties> typeFiles = new ArrayList<>();

    protected Properties previousTypeProperties = new Properties();

    protected List<Properties> methodFiles = new ArrayList<>();

    protected Properties previousMethodProperties = new Properties();

    @Override
    public void initialize(FeaturesLoader loader) {
        this.loader = loader;
    }

    @Override
    public void start() {
        for (var annot : loader.getTargetTestClass().getAnnotationsByType(WithApplicationProperties.class)) {
            setupFor(annot, typeFiles, previousTypeProperties);
        }
    }

    @Override
    public void stop() {
        for (Properties fileProperties : typeFiles) {
            cleanupFor(fileProperties, previousTypeProperties);
        }
        previousTypeProperties.clear();
        typeFiles.clear();
    }

    @Override
    public void beforeSetup(Method method, Object test) {
        for (var annot : method.getAnnotationsByType(WithApplicationProperties.class)) {
            setupFor(annot, methodFiles, previousMethodProperties);
        }
    }

    @Override
    public void afterTeardown(Method method, Object test) {
        for (Properties fileProperties : methodFiles) {
            cleanupFor(fileProperties, previousMethodProperties);
        }
        previousMethodProperties.clear();
        methodFiles.clear();
    }

    protected void setupFor(WithApplicationProperties annot, List<Properties> files, Properties previousProperties) {
        Properties properties = System.getProperties();
        Properties fileProperties = new Properties();
        URL url = Thread.currentThread().getContextClassLoader().getResource(annot.value());
        try (InputStream is = url.openStream()) {
            fileProperties.load(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        files.add(fileProperties);
        fileProperties.forEach((k, v) -> {
            Object previousProperty = properties.remove(k);
            if (previousProperty != null) {
                previousProperties.put(k, previousProperty);
            }
            properties.put(k, v);
        });
    }

    protected void cleanupFor(Properties fileProperties, Properties previousProperties) {
        Properties properties = System.getProperties();
        fileProperties.forEach((k, v) -> {
            if (previousProperties.contains(k)) {
                properties.put(k, previousProperties.get(k));
            } else {
                properties.remove(k);
            }
        });
    }

}
