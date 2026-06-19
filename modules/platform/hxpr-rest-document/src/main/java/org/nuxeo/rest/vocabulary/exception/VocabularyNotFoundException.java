package org.nuxeo.rest.vocabulary.exception;

import jakarta.ws.rs.core.Response;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;

public class VocabularyNotFoundException extends NuxeoException {
    public VocabularyNotFoundException(PathRef vocabId) {
        super(String.format("Vocabulary of id=[%s] not found.", vocabId.toString()),
                Response.Status.NOT_FOUND.getStatusCode());
    }
}
