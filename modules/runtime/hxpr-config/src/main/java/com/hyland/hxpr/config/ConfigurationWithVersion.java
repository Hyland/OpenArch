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
package com.hyland.hxpr.config;

import java.util.Map;

/**
 * A configuration together with its optimistic-locking version.
 * <p>
 * The {@code version} is an integer counter incremented on every persisted write, used to detect concurrent updates
 * (optimistic locking). A configuration that has never been written with a version, or that does not exist, is
 * represented with version {@code 0}.
 *
 * @param config the configuration, or {@code null} if none
 * @param version the optimistic-locking version ({@code 0} when unknown or absent)
 */
public record ConfigurationWithVersion(Map<String, Object> config, long version) {
}
