package org.nuxeo.rest.vocabulary.exception;

import jakarta.ws.rs.core.Response;

import org.nuxeo.ecm.core.api.NuxeoException;

public class VocabularyValidationException extends NuxeoException {

    public static final String VOCABULARY_ID_BLANK_OR_NULL_MESSAGE = "Vocabulary id blank or null";

    public VocabularyValidationException() {
        super(VOCABULARY_ID_BLANK_OR_NULL_MESSAGE, Response.Status.BAD_REQUEST.getStatusCode());
    }
}
