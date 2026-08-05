/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.opensearch.test.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.opensearch.commands.IndexingCommand;
import org.nuxeo.opensearch.commands.IndexingCommand.Type;

public class TestIndexingCommand {

    @Test
    public void testConstructorOk() throws Exception {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("foo");
        IndexingCommand cmd = new IndexingCommand(doc, Type.INSERT, false, false);
        cmd = new IndexingCommand(doc, Type.INSERT, true, false);
        assertTrue(cmd.isSync());
        assertFalse(cmd.isRecurse());
        cmd = new IndexingCommand(doc, Type.INSERT, false, true);
        // delete recurse and sync is accepted
        cmd = new IndexingCommand(doc, Type.DELETE, true, true);
        assertTrue(cmd.isSync());
        assertTrue(cmd.isRecurse());

    }

    @Test
    public void testConstructorWithRecurseSync() throws Exception {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("foo");
        assertThrows(IllegalArgumentException.class, () -> new IndexingCommand(doc, Type.INSERT, true, true));
    }

    @Test
    public void testConstructorWithNullDoc() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new IndexingCommand(null, Type.INSERT, true, false));
    }

    @Test
    public void testConstructorWithNullDocId() throws Exception {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> new IndexingCommand(doc, Type.INSERT, true, false));
    }

    @Test
    public void testAddSchemas() throws Exception {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("foo");
        IndexingCommand cmd = new IndexingCommand(doc, Type.INSERT, true, false);
        assertNull(cmd.getSchemas());
        cmd.addSchemas("mySchema");
        assertEquals(1, cmd.getSchemas().length);
    }

    @Test
    public void testMakeSync() throws Exception {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("foo");
        // ok for non recursive command
        IndexingCommand cmd = new IndexingCommand(doc, Type.INSERT, false, false);
        cmd.makeSync();
        assertTrue(cmd.isSync());
        // recursive command cannot be turned into sync
        cmd = new IndexingCommand(doc, Type.INSERT, false, true);
        cmd.makeSync();
        assertFalse(cmd.isSync());
        // except for deletion
        cmd = new IndexingCommand(doc, Type.DELETE, false, true);
        cmd.makeSync();
        assertTrue(cmd.isSync());
    }

    @Test
    public void testJson() throws Exception {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("foo");
        IndexingCommand cmd = new IndexingCommand(doc, Type.INSERT, false, true);
        String json = cmd.toJSON();
        IndexingCommand cmd2 = IndexingCommand.fromJSON(json);
        String json2 = cmd2.toJSON();
        assertEquals(json, json2);
        assertTrue(cmd2.isRecurse());
    }

    @Test
    public void testInvalidJson() throws Exception {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("foo");
        IndexingCommand cmd = new IndexingCommand(doc, Type.INSERT, false, true);
        String json = "{" + cmd.toJSON();
        assertThrows(IllegalArgumentException.class, () -> IndexingCommand.fromJSON(json));
    }

    @Test
    public void testInvalidJsonDocIdNull() throws Exception {
        String json = "{\"id\": \"124\", \"type\": \"INSERT\"}";
        assertThrows(IllegalArgumentException.class, () -> IndexingCommand.fromJSON(json));
    }

}
