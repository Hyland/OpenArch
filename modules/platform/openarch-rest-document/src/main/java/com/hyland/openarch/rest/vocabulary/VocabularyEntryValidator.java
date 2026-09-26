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
package com.hyland.openarch.rest.vocabulary;

import com.hyland.openarch.rest.vocabulary.exception.VocabularyEntryValidationException;
import com.hyland.openarch.rest.vocabulary.io.VocabularyEntryRequestDTO;
import org.springframework.stereotype.Component;

@Component
class VocabularyEntryValidator {

    private static final String ENTRY_KEY_NULL_OR_BLANK_MESSAGE = "Vocabulary entry key is null or blank";
    private static final String ENTRY_LABEL_NULL_OR_BLANK_MESSAGE = "Vocabulary entry label is null or blank";

    public void validate(VocabularyEntryRequestDTO entryRequestDTO) {
        if (entryRequestDTO.vocabularyKey() == null || entryRequestDTO.vocabularyKey().isBlank()) {
            throw new VocabularyEntryValidationException(ENTRY_KEY_NULL_OR_BLANK_MESSAGE);
        }
        if (entryRequestDTO.vocabularyLabel() == null || entryRequestDTO.vocabularyLabel().isBlank()) {
            throw new VocabularyEntryValidationException(ENTRY_LABEL_NULL_OR_BLANK_MESSAGE);
        }
    }
}
