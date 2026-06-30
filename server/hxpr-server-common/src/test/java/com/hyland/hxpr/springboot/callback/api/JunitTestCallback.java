/*
 * (C) Copyright 2024 Hyland (https://hyland.com/) and others.
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
package com.hyland.hxpr.springboot.callback.api;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Interface to extends Junit 5 Callback
 */
public interface JunitTestCallback {
    default void beforeClass(ExtensionContext context) {

    }

    default void beforeEach(ExtensionContext context) {

    }

    default void afterAll(ExtensionContext context) {

    }

    default void afterEach(ExtensionContext context) {

    }
}
