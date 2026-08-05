/*
 * (C) Copyright 2015-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.platform.test;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Basic class for messages files translations.
 *
 * @since 7.3
 */
public class TranslationTestCase extends AbstractTranslationTestCase {

    private static final String EN_TRANSLATIONS_PATH = "OSGI-INF/l10n/messages_en_US.properties";

    /**
     * Useful for override.
     */
    protected String getEnTranslationsPath() {
        return EN_TRANSLATIONS_PATH;
    }

    @Test
    public void testTranslationsLoading() throws IOException {
        checkFormat(getEnTranslationsPath());
    }

    @Test
    public void testTranslationsDupes() throws IOException {
        checkDuplicates(getEnTranslationsPath());
    }

}
