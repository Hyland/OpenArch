/*
 * (C) Copyright 2026 Hyland (http://www.hyland.com/) and others.
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
package org.nuxeo.ecm.core.storage;

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT_BINARY;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.repository.FulltextConfiguration;
import org.nuxeo.ecm.core.model.Repository;
import org.nuxeo.ecm.core.repository.RepositoryService;
import org.nuxeo.ecm.core.utils.BlobsExtractor;
import org.nuxeo.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared fulltext extraction helpers.
 */
public class FulltextExtractionSupport implements FulltextSupport {

    private static final Logger log = LoggerFactory.getLogger(FulltextExtractionSupport.class);

    public static final int DEBUG_TEXT_CUTOFF = 50;

    public String textFromUrl(InputStream targetInputStream, URL url, int sizeLimit) {
        try (Writer writer = new StringBuilderWriter();
             Reader reader = createReader(targetInputStream, sizeLimit)) {
            IOUtils.copyLarge(reader, writer, 0, sizeLimit);
            return writer.toString();
        } catch (IOException e) {
            throw new NuxeoException("Could not read text from transform result " + url, e);
        }
    }

    private Reader createReader(InputStream inputStream, int sizeLimit) throws IOException {
        return new InputStreamReader(BoundedInputStream
            .builder()
            .setInputStream(inputStream)
            .setMaxCount(sizeLimit)
            .setPropagateClose(false) // automatically closed by t-client
            .get());
    }

    public void handlePlainText(String textFromBlob, int sizeLimit, CoreSession session, List<String> docIdsToUpdate) {
        if (log.isDebugEnabled()) {
            log.debug("handlePlainText \"{}\"", (textFromBlob.length() <= DEBUG_TEXT_CUTOFF ? textFromBlob
                    : textFromBlob.substring(0, DEBUG_TEXT_CUTOFF) + "..."));
        }
        String text = replace0chars(textFromBlob);
        for (String docIdToUpdate : docIdsToUpdate) {
            DocumentRef docRef = new IdRef(docIdToUpdate);
            session.setDocumentSystemProp(docRef, SYS_FULLTEXT_BINARY,
                    appendText(
                            session.getBinaryFulltext(docRef),
                            text,
                            sizeLimit));
        }
    }

    public String replace0chars(String string) {
        // strip '\0 chars from text - Java does not normally expect these in Strings.
        if (string.indexOf('\0') >= 0) {
            string = string.replace("\0", " ");
        }
        return string;
    }

    public String appendText(String currentText, String text, int sizeLimit) {
        // If we can, quit fast without using lots of memory
        int len = currentText == null ? 0 : currentText.length();
        if (len >= sizeLimit) {
            return currentText;
        }

        // We no longer need to a spaces at the beginning and end of extracted text for simulated phrase search
        // using LIKE "% foo bar %", but we do add one at the start if there is an existing value, so the text
        // is separated.
        // The text value should never be null.
        StringBuilder sb = new StringBuilder(currentText == null ? "" : currentText);
        if (!text.isBlank()) {
            int initialSpace = len == 0 ? 0 : 1;
            int limit = sizeLimit - len - initialSpace;
            if (limit >= initialSpace) {
                if (initialSpace == 1) {
                    sb.append(' ');
                }
                // We could text = text.trim(), but search can handle multiple spaces and trim requires more memory.
                limit -= text.length();
                if (limit >= 0) {
                    sb.append(text);
                } else {
                    sb.append(text, 0, text.length() + limit);
                }
            }
        }
        return sb.toString();
    }

    public static FulltextConfiguration getFulltextConfiguration(String repositoryName) {
        RepositoryService repositoryService = Framework.getService(RepositoryService.class);
        Repository repository = repositoryService.getRepository(repositoryName);
        return repository.getFulltextConfiguration();
    }

    // Returns a list blobs to extract
    public static List<Blob> getBlobs(DocumentModel document, FulltextConfiguration fulltextConfiguration) {
        BlobsExtractor blobsExtractor = new BlobsExtractor(fulltextConfiguration.getPropPathBlobProperties());
        return blobsExtractor.getBlobs(document);
    }

    public List<String> getDocIdsToUpdate(String docIdsToUpdate) {
        return Arrays.asList(StringUtils.split(docIdsToUpdate, ','));
    }

    public String getDocIdsToUpdateAsString(List<String> docIdsToUpdate) {
        return StringUtils.join(docIdsToUpdate, ",");
    }

    public String getBlobInfo(Blob blob) {
        return blob.getFilename() == null
                ? getAnonymousSimpleName(blob.getClass()) + '(' + blob.getDigest() + ')'
                : "'" + blob.getFilename() + "'";
    }

    // Similar to Class#getSimpleName but for Anonymous classes returns "<parentClassSimpleName>$X" rather than "".
    private <T> String getAnonymousSimpleName(Class<? extends T> clazz) {
        String name = clazz.getName();
        int i = name.lastIndexOf('.');
        if (i > -1) {
            name = name.substring(i + 1);
        }
        return name;
    }
}
