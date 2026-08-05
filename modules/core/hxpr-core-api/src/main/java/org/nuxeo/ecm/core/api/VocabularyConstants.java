/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.api;

/**
 * Constants for vocabulary system fields.
 */
public class VocabularyConstants {

    private VocabularyConstants() {}

    public static final String VOCABULARY_ID = "sysvocab_id";

    public static final String VOCABULARY_KEY = "sysvocab_key";

    public static final String VOCABULARY_LABEL = "sysvocab_label";

    public static final String VOCABULARY_TYPE = "SysVocabulary";

    public static final String VOCABULARY_ENTRY_TYPE = "SysVocabularyEntry";

    public static final String VOCABULARY_PATH = "/api/vocabularies"; // NOSONAR

    public static final String VOCABULARY_REPOSITORY_SUFFIX = "_vocabularies";
}
