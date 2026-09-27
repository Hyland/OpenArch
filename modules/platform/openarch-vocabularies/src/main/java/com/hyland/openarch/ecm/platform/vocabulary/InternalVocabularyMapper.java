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

package com.hyland.openarch.ecm.platform.vocabulary;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.api.PathRefUtils.getPathRef;
import static com.hyland.openarch.ecm.core.api.VocabularyConstants.VOCABULARY_ENTRY_TYPE;
import static com.hyland.openarch.ecm.core.api.VocabularyConstants.VOCABULARY_ID;
import static com.hyland.openarch.ecm.core.api.VocabularyConstants.VOCABULARY_KEY;
import static com.hyland.openarch.ecm.core.api.VocabularyConstants.VOCABULARY_LABEL;
import static com.hyland.openarch.ecm.core.api.VocabularyConstants.VOCABULARY_TYPE;

import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.ecm.core.api.PathRefUtils;
import com.hyland.openarch.ecm.platform.vocabulary.model.CreateVocabulary;
import com.hyland.openarch.ecm.platform.vocabulary.model.CreateVocabularyEntry;
import com.hyland.openarch.ecm.platform.vocabulary.model.VocabularyEntryWrapper;
import com.hyland.openarch.ecm.platform.vocabulary.model.VocabularyWrapper;

public class InternalVocabularyMapper {

    public DocumentModel toDocumentModel(CreateVocabulary vocabulary, CoreSession session) {
        var document = session.newDocumentModel(PathRefUtils.root(), vocabulary.vocabularyId(), VOCABULARY_TYPE);
        document.setPropertyValue(SYS_TITLE, vocabulary.vocabularyId());
        document.setPropertyValue(VOCABULARY_ID, vocabulary.vocabularyId());
        return document;
    }

    public DocumentModel toDocumentModel(CreateVocabularyEntry vocabularyEntry, CoreSession session) {
        var document = session.newDocumentModel(new PathRef(PathRefUtils.root(), vocabularyEntry.vocabularyId()),
                vocabularyEntry.vocabularyKey(), VOCABULARY_ENTRY_TYPE);
        document.setPropertyValue(SYS_TITLE, vocabularyEntry.vocabularyKey());
        document.setPropertyValue(VOCABULARY_ID, vocabularyEntry.vocabularyId());
        document.setPropertyValue(VOCABULARY_KEY, vocabularyEntry.vocabularyKey());
        document.setPropertyValue(VOCABULARY_LABEL, vocabularyEntry.vocabularyLabel());
        return document;
    }

    public DocumentModel updateVocabularyDocumentModel(CreateVocabulary vocabularyEntry, CoreSession session) {
        var document = session.getDocument(getPathRef(vocabularyEntry.vocabularyId()));
        document.setPropertyValue(SYS_TITLE, vocabularyEntry.vocabularyId());
        document.setPropertyValue(VOCABULARY_ID, vocabularyEntry.vocabularyId());
        return document;
    }

    public DocumentModel updateEntryDocumentModel(CreateVocabularyEntry vocabularyEntry, CoreSession session) {
        var document = session.getDocument(
                new PathRef(getPathRef(vocabularyEntry.vocabularyId()), vocabularyEntry.vocabularyKey()));
        document.setPropertyValue(SYS_TITLE, vocabularyEntry.vocabularyKey());
        document.setPropertyValue(VOCABULARY_ID, vocabularyEntry.vocabularyId());
        document.setPropertyValue(VOCABULARY_KEY, vocabularyEntry.vocabularyKey());
        document.setPropertyValue(VOCABULARY_LABEL, vocabularyEntry.vocabularyLabel());
        return document;
    }

    public VocabularyWrapper toVocabularyWrapper(DocumentModel documentModel) {
        return new VocabularyWrapper(documentModel);
    }

    public VocabularyEntryWrapper toVocabularyEntryWrapper(DocumentModel documentModel) {
        return new VocabularyEntryWrapper(documentModel);
    }
}
