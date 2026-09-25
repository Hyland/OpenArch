/*
 * (C) Copyright 2012-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.version.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentModelList;

public class TestVersioningRemovalPolicy extends AbstractTestVersioning {

    protected DocumentModelList getVersion() {
        return session.query("select * FROM SysContent where sysver_isVersion = TRUE");
    }

    @Test
    public void shouldRemoveOrphanVersionWhenLiveRemoved() {
        DocumentModel doc = session.createDocumentModel("/", "testfile1", MY_FILE);
        doc.create();
        // create version
        doc.checkIn();
        // check out live doc
        doc.checkOut();

        DocumentModelList vs = getVersion();
        assertEquals(1, vs.size());

        // now remove the doc
        session.removeDocument(doc.getRef());
        session.save();
        waitForAsyncCompletion();

        // version should not be found
        vs = getVersion();
        assertEquals(0, vs.size());
    }

    @Test
    public void shouldRemoveOrphanVersionWhenLiveCheckedInRemoved() {
        DocumentModel doc = session.createDocumentModel("/", "testfile1", MY_FILE);
        doc.create();
        // create version, live doc is checked in
        session.checkIn(doc.getRef());
        session.save();

        // version is found
        DocumentModelList vs = getVersion();
        assertEquals(1, vs.size());

        // now remove the doc
        session.removeDocument(doc.getRef());
        session.save();

        waitForAsyncCompletion();

        // version should not be found
        vs = getVersion();
        assertEquals(0, vs.size());
    }

    // NXP-14187
    @Test
    public void shouldRemoveOrphanVersionWhenContainingFolderRemoved() {
        DocumentModel ws = session.createDocumentModel("/", "ws", SYS_FOLDER);
        ws.create();
        DocumentModel folder = session.createDocumentModel("/ws", "folder", SYS_FOLDER);
        folder.create();
        DocumentModel doc = session.createDocumentModel("/ws/folder", "file", MY_FILE);
        doc.create();
        // create first version
        session.checkIn(doc.getRef());
        session.save();
        waitForAsyncCompletion();

        // versions found
        DocumentModelList vs = getVersion();
        assertEquals(1, vs.size());

        // delete folder containing the doc
        session.removeDocument(folder.getRef());
        session.save();
        waitForAsyncCompletion();

        // versions should not be found
        vs = getVersion();
        assertEquals(0, vs.size());
    }

}
