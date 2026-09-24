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
package com.hyland.openarch.ecm.core.event.raiser;

import com.hyland.openarch.common.xmap.registry.MapRegistry;

/**
 * Registry for the {@code raisers} extension point of the document event raiser service. Keeps the
 * contributed {@link DocumentEventRaiserDescriptor}s keyed by their event {@code name}.
 *
 */
public class DocumentEventRaiserRegistry extends MapRegistry {
}
