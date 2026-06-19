package org.nuxeo.rest.vocabulary;

import org.nuxeo.ecm.core.api.NuxeoException;

import static jakarta.servlet.http.HttpServletResponse.SC_CONFLICT;

public class VocabularyAlreadyExistsException extends NuxeoException {

    private static final long serialVersionUID = 1L;
    private static final String defaultMessage = "Vocabulary already exists";

    public VocabularyAlreadyExistsException() {
        super(defaultMessage, SC_CONFLICT);
    }

}
