package org.nuxeo.rest.vocabulary.exception;

import jakarta.ws.rs.core.Response;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;

public class VocabularyEntryNotFoundException extends NuxeoException {
    public VocabularyEntryNotFoundException(PathRef entryKey) {
        super(String.format("VocabularyEntry of entry=[%s] not found.", entryKey.toString()),
                Response.Status.NOT_FOUND.getStatusCode());
    }
}
