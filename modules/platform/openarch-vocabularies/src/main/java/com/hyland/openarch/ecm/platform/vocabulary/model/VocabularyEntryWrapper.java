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
package com.hyland.openarch.ecm.platform.vocabulary.model;

import static com.hyland.openarch.ecm.core.api.VocabularyConstants.VOCABULARY_ID;
import static com.hyland.openarch.ecm.core.api.VocabularyConstants.VOCABULARY_KEY;
import static com.hyland.openarch.ecm.core.api.VocabularyConstants.VOCABULARY_LABEL;

import java.io.Serializable;
import java.util.Objects;

import com.hyland.openarch.ecm.core.api.DocumentModel;

/**
 * Wrapper for a DocumentModel representing a Vocabulary entry.
 */
public class VocabularyEntryWrapper implements Serializable {

    private final DocumentModel documentModel;

    public VocabularyEntryWrapper(DocumentModel documentModel) {
        this.documentModel = documentModel;
    }

    public String getLabel() {
        return (String) documentModel.getPropertyValue(VOCABULARY_LABEL);
    }

    public String getKey() {
        return (String) documentModel.getPropertyValue(VOCABULARY_KEY);
    }

    public String getVocabularyId() {
        return (String) documentModel.getPropertyValue(VOCABULARY_ID);
    }

    public String getRepositoryName() {
        return documentModel.getRepositoryName();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VocabularyEntryWrapper that //
                && Objects.equals(getRepositoryName(), that.getRepositoryName()) //
                && Objects.equals(getVocabularyId(), that.getVocabularyId()) //
                && Objects.equals(getKey(), that.getKey()) //
                && Objects.equals(getLabel(), that.getLabel());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getRepositoryName(), getVocabularyId(), getKey(), getLabel());
    }
}
