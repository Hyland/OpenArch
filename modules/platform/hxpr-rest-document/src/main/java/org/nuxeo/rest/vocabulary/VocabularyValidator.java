package org.nuxeo.rest.vocabulary;

import org.nuxeo.rest.vocabulary.exception.VocabularyValidationException;
import org.nuxeo.rest.vocabulary.io.VocabularyDTO;
import org.springframework.stereotype.Component;

@Component
class VocabularyValidator {

    public void validate(VocabularyDTO vocabularyDTO) {
        if (vocabularyDTO.vocabId() == null || vocabularyDTO.vocabId().isBlank()) {
            throw new VocabularyValidationException();
        }
    }
}
