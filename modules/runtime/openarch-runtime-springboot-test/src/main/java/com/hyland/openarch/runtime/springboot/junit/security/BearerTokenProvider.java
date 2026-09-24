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
package com.hyland.openarch.runtime.springboot.junit.security;

import java.util.function.Supplier;

/**
 * Pluggable source of a bearer token to attach to default {@code MockMvc} requests in
 * {@link com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest}.
 */
public final class BearerTokenProvider {

    private static volatile Supplier<String> supplier = () -> null;

    private BearerTokenProvider() {
        // utility
    }

    public static void register(Supplier<String> tokenSupplier) {
        supplier = tokenSupplier == null ? () -> null : tokenSupplier;
    }

    public static String getOrNull() {
        return supplier.get();
    }

}
