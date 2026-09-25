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
package com.hyland.openarch.opensearch.test.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.event.DocumentEventTypes;
import com.hyland.openarch.opensearch.commands.IndexingCommand;
import com.hyland.openarch.opensearch.commands.IndexingCommand.Type;
import com.hyland.openarch.opensearch.commands.IndexingCommands;
import com.hyland.openarch.opensearch.commands.IndexingCommandsStacker;

/**
 * Test that the logic for transforming CoreEvents in OpenSearch commands
 *
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 */
public class TestIndexingCommandsStacker extends IndexingCommandsStacker {

    protected Map<String, IndexingCommands> commands = new HashMap<>();

    protected List<IndexingCommand> flushedSyncCommands;

    protected List<IndexingCommand> flushedAsyncCommands;

    @Override
    protected Map<String, IndexingCommands> getAllCommands() {
        return commands;
    }

    @Override
    protected boolean isSyncIndexingByDefault() {
        return false;
    }

    @BeforeEach
    public void reset() {
        flushedSyncCommands = new ArrayList<>();
        flushedAsyncCommands = new ArrayList<>();
    }

    protected void fireSyncIndexing(List<IndexingCommand> syncCommands) {
        flushedSyncCommands.addAll(syncCommands);
    }

    protected void fireAsyncIndexing(List<IndexingCommand> asyncCommands) {
        flushedAsyncCommands.addAll(asyncCommands);
    }

    protected void flushCommands() {
        Map<String, IndexingCommands> allCmds = getAllCommands();

        List<IndexingCommand> syncCommands = new ArrayList<>();
        List<IndexingCommand> asyncCommands = new ArrayList<>();

        for (IndexingCommands cmds : allCmds.values()) {
            for (IndexingCommand cmd : cmds.getCommands()) {
                if (cmd.isSync()) {
                    syncCommands.add(cmd);
                } else {
                    asyncCommands.add(cmd);
                }
            }
        }
        getAllCommands().clear();

        if (syncCommands.size() > 0) {
            fireSyncIndexing(syncCommands);
        }
        if (asyncCommands.size() > 0) {
            fireAsyncIndexing(asyncCommands);
        }
    }

    @Test
    public void shouldRemoveDuplicatedEvents() {
        DocumentModel doc1 = mock(DocumentModel.class);
        when(doc1.getId()).thenReturn("1");
        DocumentModel doc2 = mock(DocumentModel.class);
        when(doc2.getId()).thenReturn("2");
        DocumentModel doc3 = mock(DocumentModel.class);
        when(doc3.getId()).thenReturn("3");

        stackCommand(doc1, DocumentEventTypes.DOCUMENT_CREATED, false);
        stackCommand(doc1, DocumentEventTypes.DOCUMENT_UPDATED, false);

        stackCommand(doc2, DocumentEventTypes.DOCUMENT_UPDATED, false);
        stackCommand(doc2, DocumentEventTypes.DOCUMENT_UPDATED, false);
        stackCommand(doc2, DocumentEventTypes.DOCUMENT_UPDATED, false);

        stackCommand(doc3, DocumentEventTypes.DOCUMENT_CREATED, false);
        stackCommand(doc3, DocumentEventTypes.DOCUMENT_UPDATED, false);
        stackCommand(doc3, DocumentEventTypes.DOCUMENT_REMOVED, false);

        assertEquals(3, commands.size());

        IndexingCommands ic1 = getCommands(doc1);
        assertEquals(1, ic1.getCommands().size());
        assertTrue(ic1.contains(Type.INSERT));
        assertEquals(Type.INSERT, ic1.getCommands().get(0).getType());

        IndexingCommands ic2 = getCommands(doc2);
        assertEquals(1, ic2.getCommands().size());
        assertTrue(ic2.contains(Type.UPDATE));
        assertEquals(Type.UPDATE, ic2.getCommands().get(0).getType());

        IndexingCommands ic3 = getCommands(doc3);
        assertEquals(0, ic3.getCommands().size());

        flushCommands();
        assertEquals(0, flushedSyncCommands.size());
        assertEquals(2, flushedAsyncCommands.size());
    }

    @Test
    public void shouldMergeDuplicatedEventsAndSwitchToSync() {
        DocumentModel doc1 = mock(DocumentModel.class);
        when(doc1.getId()).thenReturn("1");
        DocumentModel doc2 = mock(DocumentModel.class);
        when(doc2.getId()).thenReturn("2");

        stackCommand(doc1, DocumentEventTypes.DOCUMENT_UPDATED, false);
        stackCommand(doc1, DocumentEventTypes.DOCUMENT_UPDATED, true);

        stackCommand(doc2, DocumentEventTypes.DOCUMENT_CREATED, false);
        stackCommand(doc2, DocumentEventTypes.DOCUMENT_UPDATED, false);
        stackCommand(doc2, DocumentEventTypes.DOCUMENT_UPDATED, true);

        assertEquals(2, commands.size());

        IndexingCommands ic1 = getCommands(doc1);
        assertEquals(1, ic1.getCommands().size());
        assertTrue(ic1.contains(Type.UPDATE));
        assertEquals(Type.UPDATE, ic1.getCommands().get(0).getType());
        assertTrue(ic1.getCommands().get(0).isSync());

        IndexingCommands ic2 = getCommands(doc2);
        assertEquals(1, ic2.getCommands().size());
        assertTrue(ic2.contains(Type.INSERT));
        assertEquals(Type.INSERT, ic2.getCommands().get(0).getType());
        assertTrue(ic2.getCommands().get(0).isSync());

        flushCommands();
        assertEquals(2, flushedSyncCommands.size());
        assertEquals(0, flushedAsyncCommands.size());
    }

    @Test
    public void shouldRecurseReindex() {
        DocumentModel doc1 = mock(DocumentModel.class);
        when(doc1.getId()).thenReturn("1");
        when(doc1.isFolder()).thenReturn(true);
        DocumentModel doc2 = mock(DocumentModel.class);
        when(doc2.getId()).thenReturn("2");
        when(doc2.isFolder()).thenReturn(true);

        stackCommand(doc1, DocumentEventTypes.DOCUMENT_MOVED, false);

        stackCommand(doc2, DocumentEventTypes.DOCUMENT_SECURITY_UPDATED, false);

        IndexingCommands ic1 = getCommands(doc1);
        assertEquals(1, ic1.getCommands().size());
        assertTrue(ic1.contains(Type.UPDATE));
        assertEquals(Type.UPDATE, ic1.getCommands().get(0).getType());
        assertTrue(ic1.getCommands().get(0).isRecurse());

        IndexingCommands ic2 = getCommands(doc2);
        assertEquals(1, ic2.getCommands().size());
        assertTrue(ic2.contains(Type.UPDATE_SECURITY));
        assertEquals(Type.UPDATE_SECURITY, ic2.getCommands().get(0).getType());
        assertTrue(ic2.getCommands().get(0).isRecurse());

        flushCommands();
        assertEquals(0, flushedSyncCommands.size());
        assertEquals(2, flushedAsyncCommands.size());

    }

    @Test
    public void shouldRecurseReindexInSync() {
        DocumentModel doc1 = mock(DocumentModel.class);
        when(doc1.getId()).thenReturn("1");
        when(doc1.isFolder()).thenReturn(true);

        stackCommand(doc1, DocumentEventTypes.DOCUMENT_MOVED, true);

        IndexingCommands ic1 = getCommands(doc1);
        // We should have 2 commands 1 sync + 1 async and recursive
        assertEquals(2, ic1.getCommands().size());
        assertTrue(ic1.contains(Type.UPDATE));
        assertEquals(Type.UPDATE, ic1.getCommands().get(0).getType());
        assertFalse(ic1.getCommands().get(0).isRecurse());
        assertTrue(ic1.getCommands().get(1).isRecurse());

        flushCommands();
        assertEquals(1, flushedSyncCommands.size());
        assertEquals(1, flushedAsyncCommands.size());
    }

    @Test
    public void shouldRecurseReindexDeleteEvent() {
        DocumentModel doc1 = mock(DocumentModel.class);
        doc1.setPrimaryTypeAndMixinTypes("SysFile", Set.of("SysFilish", "SysVersionable"));
        when(doc1.getId()).thenReturn("1");

        DocumentModel doc2 = mock(DocumentModel.class);
        doc1.setPrimaryTypeAndMixinTypes("SysRenditionsContainer", Set.of("SysFolderish"));
        when(doc2.getId()).thenReturn("2");
        when(doc2.isFolder()).thenReturn(true);

        DocumentModel doc3 = mock(DocumentModel.class);
        doc1.setPrimaryTypeAndMixinTypes("SysRendition", null);
        when(doc3.getId()).thenReturn("3");

        stackCommand(doc1, DocumentEventTypes.DOCUMENT_REMOVED, false);

        IndexingCommands ic1 = getCommands(doc1);
        assertEquals(1, ic1.getCommands().size());
        assertTrue(ic1.contains(Type.DELETE));
        assertEquals(Type.DELETE, ic1.getCommands().get(0).getType());
        assertTrue(ic1.getCommands().get(0).isRecurse());

        flushCommands();
        assertEquals(0, flushedSyncCommands.size());
        assertEquals(1, flushedAsyncCommands.size());
    }

    // --- New tests for embeddings-aware logic ---

    @Test
    public void shouldRecurseOnCreatedByCopyWhenHasEmbeddings() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("10");
        when(doc.isFolder()).thenReturn(false);
        when(doc.hasEmbeddings()).thenReturn(true);
        when(doc.isVersion()).thenReturn(false);

        stackCommand(doc, DocumentEventTypes.DOCUMENT_CREATED_BY_COPY, false);

        IndexingCommands ic = getCommands(doc);
        assertEquals(1, ic.getCommands().size());
        IndexingCommand cmd = ic.getCommands().get(0);
        assertEquals(Type.INSERT, cmd.getType());
        assertTrue(cmd.isRecurse(), "Should recurse due to embeddings");
        assertFalse(cmd.isSync());
    }

    @Test
    public void shouldRecurseOnUpdateWhenHasEmbeddings() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("11");
        when(doc.isFolder()).thenReturn(false);
        when(doc.hasEmbeddings()).thenReturn(true);
        when(doc.isVersion()).thenReturn(false);

        stackCommand(doc, DocumentEventTypes.DOCUMENT_UPDATED, false);

        IndexingCommands ic = getCommands(doc);
        assertEquals(1, ic.getCommands().size());
        IndexingCommand cmd = ic.getCommands().get(0);
        assertEquals(Type.UPDATE, cmd.getType());
        assertTrue(cmd.isRecurse());
    }

    @Test
    public void shouldSplitSyncAndAsyncWhenUpdateWithEmbeddingsAndSyncRequested() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("12");
        when(doc.isFolder()).thenReturn(false);
        when(doc.hasEmbeddings()).thenReturn(true);
        when(doc.isVersion()).thenReturn(false);

        stackCommand(doc, DocumentEventTypes.DOCUMENT_UPDATED, true);

        IndexingCommands ic = getCommands(doc);
        // Expect 2 commands: one sync non-recurse, one async recurse
        assertEquals(2, ic.getCommands().size());
        IndexingCommand first = ic.getCommands().get(0);
        IndexingCommand second = ic.getCommands().get(1);
        assertEquals(Type.UPDATE, first.getType());
        assertEquals(Type.UPDATE, second.getType());
        assertTrue(first.isSync());
        assertFalse(first.isRecurse());
        assertFalse(second.isSync());
        assertTrue(second.isRecurse());
    }

    @Test
    public void shouldRecurseOnMovedWhenHasEmbeddings() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("13");
        when(doc.isFolder()).thenReturn(false);
        when(doc.hasEmbeddings()).thenReturn(true);
        when(doc.isVersion()).thenReturn(false);

        stackCommand(doc, DocumentEventTypes.DOCUMENT_MOVED, false);

        IndexingCommands ic = getCommands(doc);
        assertEquals(1, ic.getCommands().size());
        IndexingCommand cmd = ic.getCommands().get(0);
        assertEquals(Type.UPDATE, cmd.getType());
        assertTrue(cmd.isRecurse());
    }

    @Test
    public void shouldRecurseOnSecurityUpdateWhenHasEmbeddings() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("14");
        when(doc.isFolder()).thenReturn(false);
        when(doc.hasEmbeddings()).thenReturn(true);
        when(doc.isVersion()).thenReturn(false);

        stackCommand(doc, DocumentEventTypes.DOCUMENT_SECURITY_UPDATED, false);

        IndexingCommands ic = getCommands(doc);
        assertEquals(1, ic.getCommands().size());
        IndexingCommand cmd = ic.getCommands().get(0);
        assertEquals(Type.UPDATE_SECURITY, cmd.getType());
        assertTrue(cmd.isRecurse());
    }

    @Test
    public void shouldSplitSyncAndAsyncOnChildrenOrderChanged() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("15");
        when(doc.isFolder()).thenReturn(true); // recurse forced by event anyway
        when(doc.isVersion()).thenReturn(false);

        stackCommand(doc, DocumentEventTypes.DOCUMENT_CHILDREN_ORDER_CHANGED, true);

        IndexingCommands ic = getCommands(doc);
        assertEquals(2, ic.getCommands().size());
        IndexingCommand first = ic.getCommands().get(0);
        IndexingCommand second = ic.getCommands().get(1);
        assertEquals(Type.UPDATE_DIRECT_CHILDREN, first.getType());
        assertEquals(Type.UPDATE_DIRECT_CHILDREN, second.getType());
        assertTrue(first.isSync());
        assertFalse(first.isRecurse());
        assertFalse(second.isSync());
        assertTrue(second.isRecurse());
    }

    @Test
    public void shouldAllowSyncRecursiveDelete() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("16");
        when(doc.isFolder()).thenReturn(true);
        when(doc.isVersion()).thenReturn(false);

        // stack a delete with sync flag
        stackCommand(doc, DocumentEventTypes.DOCUMENT_REMOVED, true);

        IndexingCommands ic = getCommands(doc);
        assertEquals(1, ic.getCommands().size());
        IndexingCommand cmd = ic.getCommands().get(0);
        assertEquals(Type.DELETE, cmd.getType());
        assertTrue(cmd.isRecurse());
        // delete is allowed to be sync + recurse (no split logic in stacker for DELETE)
        assertTrue(cmd.isSync());
    }

    @Test
    public void shouldMergeEmbeddingsUpdateSyncAfterAsyncNonRecurse() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("17");
        when(doc.isFolder()).thenReturn(false);
        when(doc.hasEmbeddings()).thenReturn(false); // first update non recurse
        when(doc.isVersion()).thenReturn(false);

        stackCommand(doc, DocumentEventTypes.DOCUMENT_UPDATED, false); // async non recurse UPDATE

        // Now simulate embeddings appears (e.g. changed mixins): override mock
        when(doc.hasEmbeddings()).thenReturn(true);
        stackCommand(doc, DocumentEventTypes.DOCUMENT_UPDATED, true); // triggers split due to recurse

        IndexingCommands ic = getCommands(doc);
        // After second call we expect 2 UPDATE commands (sync+async) due to split
        assertEquals(2, ic.getCommands().size());
        IndexingCommand first = ic.getCommands().get(0);
        IndexingCommand second = ic.getCommands().get(1);
        assertTrue(first.isSync());
        assertFalse(first.isRecurse());
        assertFalse(second.isSync());
        assertTrue(second.isRecurse());
    }

}
