/*
 * (C) Copyright 2019-2026 Hyland (https://www.hyland.com/).
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

import java.lang.reflect.Method;
import java.util.Properties;

import com.hyland.openarch.runtime.api.Framework;

/**
 * Features handling {@link WithFrameworkProperty} annotations.
 *
 * @since 11.1
 */
public class WithFrameworkPropertyFeature implements RunnerFeature {

    protected Properties previousProperties = new Properties();

    @Override
    public void beforeSetup(Method method, Object test) {
        Properties properties = Framework.getProperties();
        for (var annot : method.getAnnotationsByType(WithFrameworkProperty.class)) {
            String propertyKey = annot.name();
            Object previousProperty = properties.remove(propertyKey);
            if (previousProperty != null) {
                previousProperties.put(propertyKey, previousProperty);
            }
            properties.put(propertyKey, annot.value());
        }
    }

    @Override
    public void afterTeardown(Method method, Object test) {
        Properties properties = Framework.getProperties();
        for (var annot : method.getAnnotationsByType(WithFrameworkProperty.class)) {
            String propertyKey = annot.name();
            if (previousProperties.contains(propertyKey)) {
                properties.put(propertyKey, previousProperties.get(propertyKey));
            } else {
                properties.remove(propertyKey);
            }
        }
        previousProperties.clear();
    }

}
