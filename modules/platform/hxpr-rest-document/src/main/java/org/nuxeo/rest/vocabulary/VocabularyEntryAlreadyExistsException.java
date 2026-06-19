package org.nuxeo.rest.vocabulary;

import static jakarta.servlet.http.HttpServletResponse.SC_CONFLICT;

import org.nuxeo.ecm.core.api.NuxeoException;

public class VocabularyEntryAlreadyExistsException extends NuxeoException {

    private static final long serialVersionUID = 1L;
    private static final String defaultMessage = "Vocabulary Entry with given key already exists";

    public VocabularyEntryAlreadyExistsException() {
        super(defaultMessage, SC_CONFLICT);
    }

}
