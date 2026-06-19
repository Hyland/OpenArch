package org.nuxeo.rest.vocabulary.exception;

import jakarta.ws.rs.core.Response;

import org.nuxeo.ecm.core.api.NuxeoException;

public class VocabularyEntryValidationException extends NuxeoException {
    public VocabularyEntryValidationException(String message) {
        super(message, Response.Status.BAD_REQUEST.getStatusCode());
    }
}
