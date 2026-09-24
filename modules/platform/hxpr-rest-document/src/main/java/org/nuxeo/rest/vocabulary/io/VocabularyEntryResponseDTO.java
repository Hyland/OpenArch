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

package org.nuxeo.rest.vocabulary.io;

import static com.hyland.openarch.ecm.core.api.VocabularyConstants.VOCABULARY_ID;
import static com.hyland.openarch.ecm.core.api.VocabularyConstants.VOCABULARY_KEY;
import static com.hyland.openarch.ecm.core.api.VocabularyConstants.VOCABULARY_LABEL;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;

@Schema(name = "VocabularyEntry", description = "Represents a VocabularyEntry.")
public record VocabularyEntryResponseDTO( //
        @JsonProperty(VOCABULARY_ID) String vocabularyId, //
        @JsonProperty(VOCABULARY_KEY) String vocabularyKey, //
        @JsonProperty(VOCABULARY_LABEL) String vocabularyLabel) {

    public static final class Builder {

        private String vocabId;

        private String vocabularyKey;

        private String vocabularyLabel;

        public Builder withVocabularyId(String vocabId) {
            this.vocabId = vocabId;
            return this;
        }

        public Builder withVocabularyKey(String vocabularyKey) {
            this.vocabularyKey = vocabularyKey;
            return this;
        }

        public Builder withVocabularyLabel(String vocabularyLabel) {
            this.vocabularyLabel = vocabularyLabel;
            return this;
        }

        public VocabularyEntryResponseDTO build() {
            return new VocabularyEntryResponseDTO(vocabId, vocabularyKey, vocabularyLabel);
        }
    }
}
