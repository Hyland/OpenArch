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
package org.nuxeo.ecm.platform.vocabulary;

import java.util.Comparator;
import java.util.List;

import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentNotFoundException;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.ecm.core.api.PathRefUtils;
import com.hyland.openarch.ecm.core.api.VocabularyConstants;
import org.nuxeo.ecm.platform.vocabulary.exception.VocabularyAlreadyExistsException;
import org.nuxeo.ecm.platform.vocabulary.exception.VocabularyEntryAlreadyExistsException;
import org.nuxeo.ecm.platform.vocabulary.exception.VocabularyEntryNotFoundException;
import org.nuxeo.ecm.platform.vocabulary.exception.VocabularyNotFoundException;
import org.nuxeo.ecm.platform.vocabulary.model.CreateVocabulary;
import org.nuxeo.ecm.platform.vocabulary.model.CreateVocabularyEntry;
import org.nuxeo.ecm.platform.vocabulary.model.VocabularyEntryWrapper;
import org.nuxeo.ecm.platform.vocabulary.model.VocabularyWrapper;

public class VocabularyService {

    private InternalVocabularyMapper internalVocabularyMapper;

    public VocabularyService(InternalVocabularyMapper internalVocabularyMapper) {
        this.internalVocabularyMapper = internalVocabularyMapper;
    }

    public VocabularyWrapper createVocabulary(String repositoryId, CreateVocabulary vocabulary) {
        var session = getSession(getVocabulariesRepositoryName(repositoryId));
        if (session.exists(PathRefUtils.getPathRef(vocabulary.vocabularyId()))) {
            throw new VocabularyAlreadyExistsException(vocabulary.vocabularyId());
        }
        var document = internalVocabularyMapper.toDocumentModel(vocabulary, session);
        document.create();
        session.save();

        return internalVocabularyMapper.toVocabularyWrapper(document);
    }

    public VocabularyEntryWrapper createVocabularyEntry(String repositoryId, CreateVocabularyEntry entry) {
        var session = getSession(getVocabulariesRepositoryName(repositoryId));
        if (!session.exists(PathRefUtils.getPathRef(entry.vocabularyId()))) {
            throw new VocabularyNotFoundException(entry.vocabularyId());
        }
        if (session.exists(new PathRef(PathRefUtils.getPathRef(entry.vocabularyId()), entry.vocabularyKey()))) {
            throw new VocabularyEntryAlreadyExistsException(entry.vocabularyId(), entry.vocabularyKey());
        }
        var document = internalVocabularyMapper.toDocumentModel(entry, session);
        document.create();
        session.save();

        return internalVocabularyMapper.toVocabularyEntryWrapper(document);
    }

    public VocabularyWrapper getVocabulary(String repositoryId, String vocabularyId) {
        var ref = new PathRef(PathRefUtils.root(), vocabularyId);
        try {
            var document = getSession(getVocabulariesRepositoryName(repositoryId)).getDocument(ref);
            return internalVocabularyMapper.toVocabularyWrapper(document);
        } catch (DocumentNotFoundException exception) {
            throw new VocabularyNotFoundException(vocabularyId);
        }
    }

    public VocabularyEntryWrapper getVocabularyEntry(String repositoryId, String vocabularyId, String vocabularyKey) {
        var session = getSession(getVocabulariesRepositoryName(repositoryId));
        var parentRef = new PathRef(PathRefUtils.root(), vocabularyId);
        if (!session.exists(parentRef)) {
            throw new VocabularyNotFoundException(vocabularyId);
        }
        var entryRef = new PathRef(parentRef, vocabularyKey);
        if (!session.exists(entryRef)) {
            throw new VocabularyEntryNotFoundException(vocabularyId, vocabularyKey);
        }

        var docVocabularyEntry = session.getDocument(entryRef);
        return internalVocabularyMapper.toVocabularyEntryWrapper(docVocabularyEntry);
    }

    public VocabularyWrapper updateVocabulary(String repositoryId, CreateVocabulary vocabulary) {
        var session = getSession(getVocabulariesRepositoryName(repositoryId));
        var vocabularyRef = new PathRef(PathRefUtils.root(), vocabulary.vocabularyId());
        if (!session.exists(PathRefUtils.getPathRef(vocabulary.vocabularyId()))) {
            throw new VocabularyNotFoundException(vocabulary.vocabularyId());
        }

        var vocabularyDocument = internalVocabularyMapper.updateVocabularyDocumentModel(vocabulary, session);
        session.save();
        return internalVocabularyMapper.toVocabularyWrapper(vocabularyDocument);
    }

    public VocabularyEntryWrapper updateVocabularyEntry(String repositoryId, CreateVocabularyEntry entry) {
        var parentRef = new PathRef(PathRefUtils.root(), entry.vocabularyId());
        var session = getSession(getVocabulariesRepositoryName(repositoryId));
        if (!session.exists(parentRef)) {
            throw new VocabularyNotFoundException(entry.vocabularyId());
        }
        var entryRef = new PathRef(parentRef, entry.vocabularyKey());
        if (!session.exists(entryRef)) {
            throw new VocabularyEntryNotFoundException(entry.vocabularyId(), entry.vocabularyKey());
        }
        var entryDocument = internalVocabularyMapper.updateEntryDocumentModel(entry, session);
        session.save();

        return internalVocabularyMapper.toVocabularyEntryWrapper(entryDocument);
    }

    public List<VocabularyWrapper> getVocabularies(String repositoryId) {
        return getSession(
                getVocabulariesRepositoryName(repositoryId)).getChildren(PathRefUtils.root())
                                                            .stream()
                                                            .map(internalVocabularyMapper::toVocabularyWrapper)
                                                            .sorted(Comparator.comparing(
                                                                    VocabularyWrapper::getVocabularyId))
                                                            .toList();
    }

    public List<VocabularyEntryWrapper> getVocabularyEntries(String repositoryId, String vocabularyId) {
        var session = getSession(getVocabulariesRepositoryName(repositoryId));
        var parentRef = new PathRef(PathRefUtils.root(), vocabularyId);
        if (!session.exists(parentRef)) {
            throw new VocabularyNotFoundException(vocabularyId);
        }
        return session.getChildren(parentRef)
                      .stream()
                      .map(internalVocabularyMapper::toVocabularyEntryWrapper)
                      .sorted(Comparator.comparing(VocabularyEntryWrapper::getKey))
                      .toList();
    }

    public void removeVocabulary(String repositoryId, String vocabularyId) {
        var session = getSession(getVocabulariesRepositoryName(repositoryId));
        var ref = new PathRef(PathRefUtils.root(), vocabularyId);
        if (!session.exists(ref)) {
            throw new VocabularyNotFoundException(vocabularyId);
        }
        session.removeDocument(ref);
    }

    public void removeVocabularyEntry(String repositoryId, String vocabularyId, String key) {
        var vocRef = new PathRef(PathRefUtils.root(), vocabularyId);
        var ref = new PathRef(vocRef, key);
        var session = getSession(getVocabulariesRepositoryName(repositoryId));
        if (!session.exists(ref)) {
            throw new VocabularyEntryNotFoundException(vocabularyId, key);
        }
        session.removeDocument(ref);
    }

    private CoreSession getSession(String repoId) {
        return CoreInstance.getCoreSession(repoId);
    }

    private String getVocabulariesRepositoryName(String repoId) {
        return repoId + VocabularyConstants.VOCABULARY_REPOSITORY_SUFFIX;
    }
}
