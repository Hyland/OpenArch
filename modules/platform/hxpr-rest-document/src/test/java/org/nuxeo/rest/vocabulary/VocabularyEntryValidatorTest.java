/*
 * (C) Copyright 2024 Hyland (http://www.hyland.com/) and others.
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
 *
 * Contributors:
 *     Adam Dunajewski
 *     janv
 */
package org.nuxeo.rest.vocabulary;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.nuxeo.rest.vocabulary.exception.VocabularyEntryValidationException;
import org.nuxeo.rest.vocabulary.io.VocabularyEntryRequestDTO;

class VocabularyEntryValidatorTest {

    private VocabularyEntryValidator entryValidator = new VocabularyEntryValidator();

    @Test
    void shouldValidateValidEntry() {
        VocabularyEntryRequestDTO vocabularyEntryRequestDTO = new VocabularyEntryRequestDTO("key", "label");
        entryValidator.validate(vocabularyEntryRequestDTO);
    }

    @Test
    void shouldThrowValidationExceptionWhenValidatingEntryWithNullKey() {
        VocabularyEntryRequestDTO vocabularyEntryRequestDTO = new VocabularyEntryRequestDTO(null, "label");
        assertThrows(VocabularyEntryValidationException.class, () -> entryValidator.validate(vocabularyEntryRequestDTO));
    }

    @Test
    void shouldThrowValidationExceptionWhenValidatingEntryWithBlankKey() {
        VocabularyEntryRequestDTO vocabularyEntryRequestDTO = new VocabularyEntryRequestDTO("", "label");
        assertThrows(VocabularyEntryValidationException.class, () -> entryValidator.validate(vocabularyEntryRequestDTO));
    }

    @Test
    void shouldThrowValidationExceptionWhenValidatingEntryWithNullLabel() {
        VocabularyEntryRequestDTO vocabularyEntryRequestDTO = new VocabularyEntryRequestDTO("key", null);
        assertThrows(VocabularyEntryValidationException.class, () -> entryValidator.validate(vocabularyEntryRequestDTO));
    }

    @Test
    void shouldThrowValidationExceptionWhenValidatingEntryWithBlankLabel() {
        VocabularyEntryRequestDTO vocabularyEntryRequestDTO = new VocabularyEntryRequestDTO("key", " ");
        assertThrows(VocabularyEntryValidationException.class, () -> entryValidator.validate(vocabularyEntryRequestDTO));
    }
}
