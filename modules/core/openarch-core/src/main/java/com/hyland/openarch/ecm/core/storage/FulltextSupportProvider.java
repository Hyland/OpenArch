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
package com.hyland.openarch.ecm.core.storage;

import com.hyland.openarch.ecm.core.storage.config.FulltextSpringContext;
import org.springframework.stereotype.Component;

@Component
public class FulltextSupportProvider {

    private static FulltextSupportProvider instance;
    private final FulltextSpringContext fulltextSpringContext;

    public FulltextSupportProvider(FulltextSpringContext fulltextSpringContext) {
        this.fulltextSpringContext = fulltextSpringContext;
        FulltextSupportProvider.instance = this;
    }

    /**
     * Get the FulltextSupport bean using dependency injection.
     *
     * @return FulltextSupport bean if available, otherwise FulltextExtractionSupport fallback
     */
    public FulltextSupport getFulltextSupport() {
        return fulltextSpringContext.getFulltextSupport();
    }

    /**
     * Static method for backward compatibility.
     * Delegates to the singleton instance if available.
     *
     * @return FulltextSupport bean or fallback instance
     */
    public static FulltextSupport get() {
        if (instance != null) {
            return instance.getFulltextSupport();
        }
        return new FulltextExtractionSupport();
    }
}
