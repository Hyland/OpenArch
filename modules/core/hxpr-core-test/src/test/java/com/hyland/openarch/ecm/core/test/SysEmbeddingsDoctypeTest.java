/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.test;

import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.SysEmbeddingsPolicies.EMBEDDINGS_NAME_PREFIX;
import static com.hyland.openarch.ecm.core.api.CoreSession.ENFORCE_SYS_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_CHUNKS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_EMBEDDINGS;
import static com.hyland.openarch.ecm.core.schema.MixinNames.SYS_HAS_EMBEDDINGS;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_EMBEDDINGS;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;

import java.io.IOException;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.validation.DocumentValidationException;
import com.hyland.openarch.ecm.core.schema.MixinNames;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.LogFeature;

import jakarta.inject.Inject;

/**
 * Core tests for SysEmbeddings document type operations.
 *
 * This test suite validates the core layer for SysEmbeddings document type
 * creation, validation, renaming, and mixin management policies.
 *
 * The tests are organized by acceptance rules to ensure comprehensive coverage
 * of:
 * - SysHasEmbeddings mixin lifecycle management
 * - Parent mixin requirements enforcement
 * - Name validation, auto-prefixing, and enforcement policies
 * - Rename validation and enforcement
 * - Required blob field validation
 *
 * Each acceptance rule groups related positive and negative test cases for
 * easier maintenance and understanding of the business logic at the
 * core layer.
 */
@ExtendWith(FeaturesExtension.class)
@Features({ CoreFeature.class, LogFeature.class })
class SysEmbeddingsDoctypeTest {

    @SuppressWarnings("java:S6813") // @codebase allow field injection in test for brevity
    @Inject
    protected CoreSession session;

    private static final String NON_PREFIXED_NAME = "not-prefixed"; // @codebase reused literal

    /**
     * With ENFORCE_SYS_NAME=true, creating a SysEmbeddings with a non-prefixed name
     * is rejected with a
     * DocumentValidationException based on the name prefix rule.
     */
    // =======================================================================
    // ACCEPTANCE RULE 1: SysHasEmbeddings Mixin Management
    // =======================================================================

    /**
     * Adding the SysHasEmbeddings mixin to a parent persists and is detected on
     * reload.
     */
    @Test
    void testAddSysHasEmbeddingsMixinOnParentWithoutChildrenShouldSucceed() {
        // given a doc without SysHasEmbeddings mixin
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();

        // when adding the SysHasEmbeddings mixin
        doc.addMixin(SYS_HAS_EMBEDDINGS);
        session.save();
        doc.refresh();

        // then mixin addition succeeds
        assertTrue(doc.hasMixin(SYS_HAS_EMBEDDINGS));
    }

    /**
     * Removing SysHasEmbeddings on a parent that has no SysEmbeddings children
     * should succeed.
     */
    @Test
    void testRemoveSysHasEmbeddingsOnParentWithoutChildShouldSucceed() {
        // given a doc with SysHasEmbeddings mixin
        DocumentModel doc = createDocWithSysHasEmbeddings();

        // when removing the SysHasEmbeddings mixin
        removeSysHasEmbeddings(doc);
        session.save();
        doc.refresh();

        // then mixin removal succeeds
        assertFalse(doc.hasMixin(SYS_HAS_EMBEDDINGS));
    }

    /**
     * Acceptance: Removing SysHasEmbeddings from a document that has SysEmbeddings
     * children is rejected (conflict) to
     * preserve embeddings integrity.
     */
    @Test
    void testRemoveSysHasEmbeddingsOnParentWithSysEmbeddingsChildShouldConflict() {
        // given a doc with SysHasEmbeddings mixin and an embeddings child
        DocumentModel doc = createDocWithSysHasEmbeddings();
        createEmbeddingsWithBlob(doc, EMBEDDINGS_NAME_PREFIX + "foo", null);

        // when removing the SysHasEmbeddings mixin
        Executable run = () -> removeSysHasEmbeddings(doc);

        // then it fails with Conflict
        var exc = assertThrows(RepositoryException.class, run);
        assertEquals(SC_CONFLICT, exc.getStatusCode());
    }

    // =======================================================================
    // ACCEPTANCE RULE 2: Parent Mixin Requirement
    // =======================================================================

    /**
     * Acceptance: Creating a SysEmbeddings child requires the parent to have the
     * SysHasEmbeddings mixin. Enforced at
     * create time: a DocumentValidationException is thrown if the mixin is missing.
     */
    @Test
    void testCreateSysEmbeddingsUnderParentWithoutSysHasEmbeddingsMixinShouldFail() {
        // given a doc without SysHasEmbeddings mixin
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();

        // when creating an embeddings child
        Executable run = () -> createEmbeddingsWithBlob(doc, EMBEDDINGS_NAME_PREFIX + "foo", null);

        // then it fails with a validation exception
        assertThrows(DocumentValidationException.class, run);
    }

    /**
     * Acceptance: Creating a SysEmbeddings child under a parent that already has the
     * SysHasEmbeddings mixin succeeds.
     * This is the positive counterpart of
     * {@link #testCreateSysEmbeddingsUnderParentWithoutSysHasEmbeddingsMixinShouldFail()}.
     */
    @Test
    void testCreateSysEmbeddingsUnderParentWithSysHasEmbeddingsMixinShouldSucceed() {
        // given a doc WITH SysHasEmbeddings mixin
        DocumentModel doc = createDocWithSysHasEmbeddings();

        // when creating an embeddings child with a valid prefixed name
        String name = EMBEDDINGS_NAME_PREFIX + "another";
        DocumentModel emb = createEmbeddingsWithBlob(doc, name, null);


        // then it succeeds and keeps the provided name
        assertEquals(name, emb.getName());
        assertTrue(doc.hasMixin(SYS_HAS_EMBEDDINGS), "Parent should still have SysHasEmbeddings mixin");
    }

    // =======================================================================
    // ACCEPTANCE RULE 3: Name Validation & Auto-Prefixing
    // =======================================================================

    /**
     * Creating SysEmbeddings with a valid, prefixed name should succeed and
     * preserve the provided name.
     */
    @Test
    void testCreateSysEmbeddingsWithAValidNameShouldSucceed() {
        // given a doc with SysHasEmbeddings mixin
        DocumentModel doc = createDocWithSysHasEmbeddings();

        // when creating an embeddings child with a valid prefixed name
        String name = EMBEDDINGS_NAME_PREFIX + "foo";
        DocumentModel emb = createEmbeddingsWithBlob(doc, name, null);

        // then it succeeds with the exact name
        assertEquals(name, emb.getName());
    }

    /**
     * With ENFORCE_SYS_NAME=false (default), creating a SysEmbeddings with a
     * non-prefixed name is accepted and the name
     * is automatically prefixed with EMBEDDINGS_NAME_PREFIX.
     */
    @Test
    void testCreateSysEmbeddingsWithInvalidNameAndNoNamingEnforcementShouldSucceeds() {
        // given a doc with SysHasEmbeddings mixin
        DocumentModel doc = createDocWithSysHasEmbeddings();

        // when creating an embeddings child with an invalid prefixed name but no naming enforcement
        DocumentModel emb = createEmbeddingsWithBlob(doc, NON_PREFIXED_NAME, false);

        // then it succeeds with an auto-prefixed name
        assertEquals(EMBEDDINGS_NAME_PREFIX + NON_PREFIXED_NAME, emb.getName());
    }

    /**
     * With ENFORCE_SYS_NAME=true, creating a SysEmbeddings with a non-prefixed name
     * should throw validation exception.
     */
    @Test
    void testCreateSysEmbeddingsWithInvalidNameAndNamingEnforcementShouldThrow() {
        // given a doc with SysHasEmbeddings mixin
        DocumentModel doc = createDocWithSysHasEmbeddings();

        // when creating an embeddings child with an invalid prefixed name and naming enforcement
        Executable run = () -> createEmbeddingsWithBlob(doc, NON_PREFIXED_NAME, true);

        // then it fails with a validation exception
        assertThrows(DocumentValidationException.class, run);
    }

    // =======================================================================
    // ACCEPTANCE RULE 4: Rename Validation
    // =======================================================================

    /**
     * Renaming a SysEmbeddings to another valid, prefixed name should succeed.
     * Note: rename validates the target name
     * but does not auto-prefix.
     */
    @Test
    void testRenameSysEmbeddingsToValidNameWithNamingEnforcementShouldSucceed() {
        // given a doc with SysHasEmbeddings mixin
        DocumentModel doc = createDocWithSysHasEmbeddings();

        // given also an embeddings child
        DocumentModel emb = createEmbeddingsWithBlob(doc, EMBEDDINGS_NAME_PREFIX + "foo", null);

        // when we rename the child to a new valid prefixed name
        String name = EMBEDDINGS_NAME_PREFIX + "renamed";
        emb = session.move(emb.getRef(), null, name);

        // then it succeeds with the proper name
        assertEquals(name, emb.getName());
    }

    /**
     * Renaming a SysEmbeddings to a non-prefixed target name should be rejected
     * with a DocumentValidationException.
     */
    @Test
    void testRenameSysEmbeddingsToWrongNameShouldThrow() {
        // given a doc with SysHasEmbeddings mixin
        DocumentModel doc = createDocWithSysHasEmbeddings();

        // given also an embeddings child
        DocumentModel emb = createEmbeddingsWithBlob(doc, EMBEDDINGS_NAME_PREFIX + "foo", null);

        // when we rename the child to an invalid prefixed name
        Executable run = () -> session.move(emb.getRef(), null, "not-prefixed-target");

        // then it fails with a validation exception
        assertThrows(DocumentValidationException.class, run);
    }

    // =======================================================================
    // ACCEPTANCE RULE 5: Required Blob Validation
    // =======================================================================

    /**
     * Creating a SysEmbeddings without the required sysemb_embeddings Blob fails
     * validation at save time with a
     * DocumentValidationException.
     */
    @Test
    void testCreateSysEmbeddingsWithMissingBlobShouldFail() {
        // given a doc with SysHasEmbeddings mixin
        DocumentModel doc = createDocWithSysHasEmbeddings();

        // when creating an embeddings child without a blob
        DocumentModel emb = newEmbeddings(doc, EMBEDDINGS_NAME_PREFIX + "foo");
        emb.create();

        // then it fails at save time with a validation exception
        assertThrows(DocumentValidationException.class, () -> session.save());
    }

    // =======================================================================
    // ACCEPTANCE RULE 6: sysemb_chunks Blob
    // =======================================================================

    /**
     * Creating a SysEmbeddings with both {@code sysemb_embeddings} and {@code sysemb_chunks} blobs
     * should succeed with no validation error, and both blobs should be readable back.
     */
    @Test
    void testCreateSysEmbeddingsWithBothBlobsShouldSucceed() throws IOException {
        // given a doc with SysHasEmbeddings mixin
        DocumentModel doc = createDocWithSysHasEmbeddings();

        // when creating an embeddings child with both blobs set in the same write
        DocumentModel emb = newEmbeddings(doc, EMBEDDINGS_NAME_PREFIX + "both");
        emb.setPropertyValue(SYSEMB_EMBEDDINGS, Blobs.createBlob("hello embeddings"));
        emb.setPropertyValue(SYSEMB_CHUNKS, Blobs.createBlob("hello chunks"));
        emb.create();
        session.save();
        emb.refresh();

        // then both blobs are stored
        Blob embeddings = (Blob) emb.getPropertyValue(SYSEMB_EMBEDDINGS);
        Blob chunks = (Blob) emb.getPropertyValue(SYSEMB_CHUNKS);
        assertEquals("hello embeddings", embeddings.getString());
        assertEquals("hello chunks", chunks.getString());
    }

    /**
     * Clearing the {@code sysemb_chunks} blob on an existing SysEmbeddings should succeed with no
     * validation error, since only {@code sysemb_embeddings} is required.
     */
    @Test
    void testClearSysembChunksShouldSucceed() throws IOException {
        // given a doc with SysHasEmbeddings mixin and a SysEmbeddings child holding both blobs
        DocumentModel doc = createDocWithSysHasEmbeddings();
        DocumentModel emb = newEmbeddings(doc, EMBEDDINGS_NAME_PREFIX + "clearable");
        emb.setPropertyValue(SYSEMB_EMBEDDINGS, Blobs.createBlob("hello embeddings"));
        emb.setPropertyValue(SYSEMB_CHUNKS, Blobs.createBlob("hello chunks"));
        emb.create();
        session.save();

        // when clearing sysemb_chunks
        emb.setPropertyValue(SYSEMB_CHUNKS, null);
        session.save();
        emb.refresh();

        // then sysemb_chunks is cleared and sysemb_embeddings is preserved
        assertNull(emb.getPropertyValue(SYSEMB_CHUNKS));
        Blob embeddings = (Blob) emb.getPropertyValue(SYSEMB_EMBEDDINGS);
        assertEquals("hello embeddings", embeddings.getString());
    }

    // =======================================================================

    DocumentModel createDocWithSysHasEmbeddings() {
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.addMixin(SYS_HAS_EMBEDDINGS);
        doc.create();
        return doc;
    }

    void removeSysHasEmbeddings(DocumentModel doc) {
        var mixins = doc.getEffectiveMixins();
        mixins.remove(MixinNames.SYS_HAS_EMBEDDINGS);
        doc.setPrimaryTypeAndMixinTypes(doc.getType(), mixins);
    }

    DocumentModel createEmbeddingsWithBlob(DocumentModel parent, String name, Boolean enforce) {
        DocumentModel emb = newEmbeddings(parent, name);
        emb.setPropertyValue(SYSEMB_EMBEDDINGS, Blobs.createBlob("hello embeddings"));
        if (enforce != null) {
            emb.putContextData(ENFORCE_SYS_NAME, enforce);
        }
        emb.create();
        session.save();
        emb.refresh();
        return emb;
    }

    DocumentModel newEmbeddings(DocumentModel parent, String name) {
        return session.createDocumentModel(parent.getPathAsString(), name, SYS_EMBEDDINGS);
    }

}
