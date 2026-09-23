/*
 * (C) Copyright 2010-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.version.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;

public class TestVersioningService extends AbstractTestVersioning {

    @Test
    public void testStandardVersioning() {
        DocumentModel folder = session.createDocumentModel("/", "folder", SYS_FOLDER);
        folder.create();
        DocumentModel doc = session.createDocumentModel("/", "testfile1", MY_FILE);
        doc.create();
        doc.setPropertyValue(SYS_TITLE, "A");
        DocumentRef docRef = doc.getRef();
        assertTrue(doc.isCheckedOut());
        assertVersion(0, doc);

        // save with no option, use default
        doc.setPropertyValue(SYS_TITLE, "B");
        assertTrue(doc.isCheckedOut());
        assertVersion(0, doc);

        // change and save with new version
        doc.setPropertyValue(SYS_TITLE, "C");
        maybeSleepToNextSecond();
        DocumentRef v1Ref = doc.checkIn();
        DocumentModel v1 = session.getDocument(v1Ref);
        assertFalse(doc.isCheckedOut());
        assertVersion(1, doc);
        assertVersion(1, v1);

        // checkout
        doc.checkOut();
        assertTrue(doc.isCheckedOut());
        assertVersion(1, doc);

        // change and save with new version
        doc.setPropertyValue(SYS_TITLE, "D");
        maybeSleepToNextSecond();
        DocumentRef v2Ref = doc.checkIn();
        DocumentModel v2 = session.getDocument(v2Ref);
        assertFalse(doc.isCheckedOut());
        assertVersion(2, doc);
        assertVersion(2, v2);

        // direct save for autocheckout
        doc.setPropertyValue(SYS_TITLE, "E");
        assertTrue(doc.isCheckedOut());
        assertVersion(2, doc);

        // checkin
        maybeSleepToNextSecond();
        DocumentRef v3Ref = doc.checkIn();
        DocumentModel v3 = session.getDocument(v3Ref);
        assertFalse(doc.isCheckedOut());
        assertVersion(3, doc);
        assertVersion(3, v3);

        // wait before doing a restore
        session.save();
        waitForAsyncCompletion();

        // restore 1
        doc = session.restoreVersion(v1Ref);
        assertTrue(doc.isCheckedOut());
        assertVersion(3, doc);

        // check in again
        maybeSleepToNextSecond();
        DocumentRef v4Ref = doc.checkIn();
        DocumentModel v4 = session.getDocument(v4Ref);
        assertFalse(doc.isCheckedOut());
        assertVersion(4, doc);
        assertVersion(4, v4);
    }

}
