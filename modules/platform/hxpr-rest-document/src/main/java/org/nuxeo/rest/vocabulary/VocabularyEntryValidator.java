package org.nuxeo.rest.vocabulary;

import org.nuxeo.rest.vocabulary.exception.VocabularyEntryValidationException;
import org.nuxeo.rest.vocabulary.io.VocabularyEntryRequestDTO;
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
