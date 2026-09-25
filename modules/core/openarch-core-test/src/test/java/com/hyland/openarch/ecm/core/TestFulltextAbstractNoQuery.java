/*
 * (C) Copyright 2020-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_FULLTEXT_BINARY;

import java.io.IOException;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;

@Features(CoreFeature.class)
@Deploy("com.hyland.openarch.ecm.core.test.tests:OSGI-INF/disable-schedulers.xml")
public abstract class TestFulltextAbstractNoQuery extends AbstractFullTextConfigurationTest {

    protected static final String CONTENT = "hello world";

    protected static final String BINARY_TEXT = "hello world";

    protected static final String BINARY_TEXT_SHA256 = DigestUtils.sha256Hex(BINARY_TEXT);

    @Test
    public void testBinaryText() throws IOException { // NOSONAR (subclass throws)
        DocumentModel doc = session.createDocumentModel("/", "doc", "FileFullText");
        doc.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob(CONTENT));
        doc.create();
        session.save();
        waitForFulltextIndexing();

        // check whether or not we can get the binary text
        doc.refresh();
        String fulltext = (String) doc.getPropertyValue(SYS_FULLTEXT_BINARY);
        if (expectBinaryText()) {
            assertEquals(BINARY_TEXT, fulltext.strip());
        } else {
            assertNull(fulltext);
        }

        // check that repository-based fulltext search fails
        try {
            String nxql = "SELECT * FROM SysContent WHERE sys_fulltext = 'hello'";
            session.query(nxql);
            fail("fulltext query should have failed");
        } catch (QueryParseException e) {
            String message = e.getMessage();
            assertTrue(message.contains("Fulltext search not available"), message);
        }
    }

    protected abstract boolean expectBinaryText();

}
