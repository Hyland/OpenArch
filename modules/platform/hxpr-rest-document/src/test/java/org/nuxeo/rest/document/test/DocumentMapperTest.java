/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.document.test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILES_BLOBS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.schema.TypeConstants.BLOB_TYPE;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_FILENAME;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_KEY;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_LENGTH;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_MIME_TYPE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;
import static org.nuxeo.rest.document.DocumentMapper.UPLOAD_ID_MARKER;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.validation.DocumentValidationException;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.platform.test.UserManagerMockFeature;
import org.nuxeo.ecm.platform.test.UserManagerMockTestConfiguration;
import org.nuxeo.rest.document.DocumentDTO;
import org.nuxeo.rest.document.DocumentMapper;
import org.nuxeo.rest.upload.UploadAttachStatus;
import org.nuxeo.rest.upload.Upload;
import org.nuxeo.rest.upload.UploadDTO;
import org.nuxeo.rest.upload.UploadService;
import org.nuxeo.rest.upload.handler.UploadHandler;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.mockito.RuntimeInject;
import org.nuxeo.runtime.springboot.junit.RuntimeSpringBootTest;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Features({ CoreFeature.class, UserManagerMockFeature.class, MockitoFeature.class })
@Deploy("org.nuxeo.springboot.rest.document")
@Import(UserManagerMockTestConfiguration.class)
@SpringBootTest(classes = { DocumentMapper.class, DocumentAPITestConfiguration.class })
public class DocumentMapperTest extends RuntimeSpringBootTest {

    @RuntimeInject
    CoreSession session;

    @Autowired
    DocumentMapper mapper;

    @Autowired
    UploadHandler handler;

    @Autowired
    UploadService uploadService;

    @Test
    public void mapperCanCreateDocumentWithBinary() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "test", SYS_FILE);
        doc.create();

        String body = "test content";
        String fileName = "test.txt";
        String mimeType = "text/plain";
        UploadDTO uploadDTO = new UploadDTO(null, fileName, mimeType, null, null, false, 0, 0);
        var response = uploadService.handleUpload(handler, mimeType, uploadDTO,
                new ByteArrayInputStream(body.getBytes(UTF_8)));
        Upload upload = response.getBody();
        var docDTO = new DocumentDTO();
        docDTO.setProperties(Map.of(SYSFILE_BLOB, Map.of(UPLOAD_ID_MARKER, upload.getId())));
        mapper.updateDocumentModel(doc, docDTO);

        // Check that the mapping has been done correctly
        Blob blob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertThat(blob).isNotNull();
        assertThat(blob.getFilename()).isEqualTo(fileName);
        assertThat(blob.getMimeType()).isEqualTo(mimeType);
    }

    @Test
    public void mapperCanAddBinariesToCreatedDocument() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "test", SYS_FILE);
        doc.create();

        String body = "test content";
        String fileName01 = "test01.txt";
        String fileName02 = "test02.txt";
        String mimeType = "text/plain";
        UploadDTO uploadDTO01 = new UploadDTO(null, fileName01, mimeType, null, null, false, 0, 0);
        UploadDTO uploadDTO02 = new UploadDTO(null, fileName02, mimeType, null, null, false, 0, 0);
        var response01 = uploadService.handleUpload(handler, mimeType, uploadDTO01,
                new ByteArrayInputStream(body.getBytes(UTF_8)));
        var response02 = uploadService.handleUpload(handler, mimeType, uploadDTO02,
                new ByteArrayInputStream(body.getBytes(UTF_8)));
        Upload upload01 = response01.getBody();
        Upload upload02 = response02.getBody();
        var docDTO = new DocumentDTO();
        docDTO.setProperties(Map.of(SYSFILES_BLOBS,
                Arrays.asList(Collections.singletonMap(BLOB_TYPE, Map.of(UPLOAD_ID_MARKER, upload01.getId())),
                        Collections.singletonMap(BLOB_TYPE, Map.of(UPLOAD_ID_MARKER, upload02.getId())))));
        mapper.updateDocumentModel(doc, docDTO);

        // Check that the mapping has been done correctly
        List sysfiles = (List) doc.getPropertyValue(SYSFILES_BLOBS);
        assertThat(sysfiles).hasSize(2);
    }

    @Test
    public void mapperCanUpdateBlobFilenameOnly() {
        DocumentModel doc = session.createDocumentModel("/", "test", SYS_FILE);
        Blob textBlob = Blobs.createBlob("Test text blob", "text/plain", "UTF-8", "test.txt");
        doc.setPropertyValue(SYSFILE_BLOB, textBlob);
        doc.create();
        session.save();
        doc.refresh();

        // Simulate a PATCH because that's the only way to update a sub-property of a complex property
        // We fetch the whole DTO of the document and only update the sub-fields we want to test
        var docDTO = mapper.documentModelToDocumentDTO(doc);
        var mapBlob = (Map<String, Object>) docDTO.getProperties().get(SYSFILE_BLOB);
        mapBlob.put(KEY_BLOB_FILENAME, "updatedTitle.txt");
        mapBlob.put(KEY_BLOB_MIME_TYPE, "image/png");
        mapBlob.put(KEY_BLOB_LENGTH, 9999L);
        docDTO.setProperties(Map.of(SYSFILE_BLOB, mapBlob));
        mapper.updateDocumentModel(doc, docDTO);

        // Check that only the filename has been updated
        var updatedBlob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertThat(updatedBlob.getFilename()).isEqualTo("updatedTitle.txt");
        assertThat(updatedBlob.getEncoding()).isEqualTo("UTF-8");
        assertThat(updatedBlob.getMimeType()).isEqualTo("text/plain");
        assertThat(updatedBlob.getLength()).isEqualTo(textBlob.getLength());
    }

    @Test
    public void mapperCanUpdateBlobsFilenameOnly() {
        DocumentModel doc = session.createDocumentModel("/", "test", SYS_FILE);
        Blob textBlob1 = Blobs.createBlob("Test text blob 1", "text/plain", "UTF-8", "test1.txt");
        Blob textBlob2 = Blobs.createBlob("Test text blob 2", "text/plain", "UTF-8", "test2.txt");
        doc.setPropertyValue(SYSFILES_BLOBS, List.of(Map.of("blob", textBlob1), Map.of("blob", textBlob2)));
        doc.create();
        session.save();

        textBlob2.setFilename("ChangedToto.txt");
        doc.setPropertyValue(SYSFILES_BLOBS + "/1/blob", textBlob2);
        session.save();
        doc.refresh();

        // Simulate a PATCH because that's the only way to update a sub-property of a complex property
        // We fetch the whole DTO of the document and only update the sub-fields we want to test
        var docDTO = mapper.documentModelToDocumentDTO(doc);
        var listBlobs = (List<Map<String, Object>>) docDTO.getProperties().get(SYSFILES_BLOBS);
        var mapBlob = (Map<String, Object>) listBlobs.get(1).get("blob");
        mapBlob.put(KEY_BLOB_FILENAME, "updatedTitle.txt");
        mapBlob.put(KEY_BLOB_MIME_TYPE, "image/png");
        mapBlob.put(KEY_BLOB_LENGTH, 9999L);
        listBlobs.get(1).put("blob", mapBlob);

        docDTO.getProperties().put(SYSFILES_BLOBS, listBlobs);
        mapper.updateDocumentModel(doc, docDTO);

        // Check that only the filename of the second file has been updated
        var updatedBlob = (Blob) doc.getPropertyValue(SYSFILES_BLOBS + "/1/blob");
        assertThat(updatedBlob.getFilename()).isEqualTo("updatedTitle.txt");
        assertThat(updatedBlob.getEncoding()).isEqualTo("UTF-8");
        assertThat(updatedBlob.getMimeType()).isEqualTo("text/plain");
        assertThat(updatedBlob.getLength()).isEqualTo(textBlob2.getLength());
        var unchangedBlob = (Blob) doc.getPropertyValue(SYSFILES_BLOBS + "/0/blob");
        assertThat(unchangedBlob.getFilename()).isEqualTo("test1.txt");
        assertThat(unchangedBlob.getEncoding()).isEqualTo("UTF-8");
        assertThat(unchangedBlob.getMimeType()).isEqualTo("text/plain");
        assertThat(unchangedBlob.getLength()).isEqualTo(textBlob2.getLength());
    }

    @Test
    public void mapperCanUpdateBlobWithoutUploadIdOrBlobKey() {
        DocumentModel doc = session.createDocumentModel("/", "test", SYS_FILE);
        Blob textBlob = Blobs.createBlob("Test text blob", "text/plain", "UTF-8", "test.txt");
        doc.setPropertyValue(SYSFILE_BLOB, textBlob);
        doc.create();
        session.save();
        doc.refresh();

        var docDTO = new DocumentDTO();
        docDTO.setProperties(Map.of(SYSFILE_BLOB, Map.of("filename", "new-file-name.txt")));
        mapper.updateDocumentModel(doc, docDTO);
        var updatedBlob = (Blob) doc.getPropertyValue(SYSFILE_BLOB);
        assertThat(updatedBlob.getFilename()).isEqualTo("new-file-name.txt");
        assertThat(updatedBlob.getEncoding()).isEqualTo("UTF-8");
        assertThat(updatedBlob.getMimeType()).isEqualTo("text/plain");
    }

    @Test
    public void mapperCanUpdateBlobsWithoutUploadIdOrBlobKey() {
        DocumentModel doc = session.createDocumentModel("/", "test", SYS_FILE);
        Blob textBlob1 = Blobs.createBlob("Test text blob one", "text/plain", "UTF-8", "file-test1.txt");
        Blob textBlob2 = Blobs.createBlob("Test text blob two", "text/plain", "UTF-8", "file-test2.txt");
        doc.setPropertyValue(SYSFILES_BLOBS, List.of(Map.of("blob", textBlob1), Map.of("blob", textBlob2)));
        doc.create();
        session.save();

        var docDTO = new DocumentDTO();
        docDTO.setProperties(Map.of(SYSFILES_BLOBS,
                List.of(Map.of("blob", Map.of()), Map.of("blob", Map.of("filename", "new-file-name-2.txt")))));
        mapper.updateDocumentModel(doc, docDTO);

        // Check that only the filename of the second file has been updated
        var updatedBlob = (Blob) doc.getPropertyValue(SYSFILES_BLOBS + "/1/blob");
        assertThat(updatedBlob.getFilename()).isEqualTo("new-file-name-2.txt");
        assertThat(updatedBlob.getEncoding()).isEqualTo("UTF-8");
        assertThat(updatedBlob.getMimeType()).isEqualTo("text/plain");
        assertThat(updatedBlob.getLength()).isEqualTo(textBlob2.getLength());
        var unchangedBlob = (Blob) doc.getPropertyValue(SYSFILES_BLOBS + "/0/blob");
        assertThat(unchangedBlob.getFilename()).isEqualTo("file-test1.txt");
        assertThat(unchangedBlob.getEncoding()).isEqualTo("UTF-8");
        assertThat(unchangedBlob.getMimeType()).isEqualTo("text/plain");
        assertThat(unchangedBlob.getLength()).isEqualTo(textBlob2.getLength());
    }

    @Test
    public void mapperThrowsExceptionWhenBlobDoesNotExist() {
        DocumentModel doc = session.createDocumentModel("/", "test", SYS_FILE);
        doc.create();
        var docDTO = new DocumentDTO();
        docDTO.setProperties(
                Map.of(SYSFILE_BLOB, Map.of(KEY_BLOB_FILENAME, "filename.txt", KEY_BLOB_KEY, "unknown-key")));
        var exception = assertThrows(DocumentValidationException.class,
                () -> mapper.updateDocumentModel(doc, docDTO));
        assertThat(exception.getMessage()).contains("The blob key unknown-key does not exist in the original document");

        docDTO.setProperties(Map.of(SYSFILES_BLOBS, Arrays.asList(Collections.singletonMap(BLOB_TYPE,
                Map.of(KEY_BLOB_FILENAME, "filename.txt", KEY_BLOB_KEY, "unknown-key")))));
        assertThrows(DocumentValidationException.class,
                () -> mapper.updateDocumentModel(doc, docDTO));
    }

    @Test
    public void mapperThrowsExceptionWhenBlobKeyIsUpdated() {
        DocumentModel doc = session.createDocumentModel("/", "test", SYS_FILE);
        Blob textBlob = Blobs.createBlob("Test text blob", "text/plain", "UTF-8", "test.txt");
        doc.setPropertyValue(SYSFILE_BLOB, textBlob);
        doc.create();
        session.save();
        doc.refresh();

        // Simulate a PATCH because that's the only way to update a sub-property of a complex property
        // We fetch the whole DTO of the document and only update the sub-fields we want to test
        var docDTO = mapper.documentModelToDocumentDTO(doc);
        var mapBlob = (Map<String, Object>) docDTO.getProperties().get(SYSFILE_BLOB);
        mapBlob.put(KEY_BLOB_KEY, "unknown-key");
        docDTO.setProperties(Map.of(SYSFILE_BLOB, mapBlob));
        assertThrows(DocumentValidationException.class,
                () -> mapper.updateDocumentModel(doc, docDTO));
    }

    @Test
    public void mapperThrowsExceptionWhenUploadBlobIsReleased() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "test", SYS_FILE);
        doc.create();

        UploadDTO uploadDTO = new UploadDTO(null, "test.txt", "text/plain", null, null, false, 0, 0);
        Upload upload = uploadService.handleUpload(handler, uploadDTO.queryMimeType(), uploadDTO,
                new ByteArrayInputStream("test content".getBytes(UTF_8))).getBody();

        // simulate an already-attached upload: its blob storage has been released, so getBlob() returns null
        upload.releaseBlobs();

        var docDTO = new DocumentDTO();
        docDTO.setProperties(Map.of(SYSFILE_BLOB, Map.of(UPLOAD_ID_MARKER, upload.getId())));
        var exception = assertThrows(DocumentValidationException.class,
                () -> mapper.updateDocumentModel(doc, docDTO));
        assertThat(exception.getMessage()).contains("No blob available for upload id");
    }

    @Test
    public void attachStatusIsReadyAfterMapping() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "test", SYS_FILE);
        doc.create();

        String body = "test content";
        String fileName = "test.txt";
        String mimeType = "text/plain";
        UploadDTO uploadDTO = new UploadDTO(null, fileName, mimeType, null, null, false, 0, 0);
        var response = uploadService.handleUpload(handler, mimeType, uploadDTO,
                new ByteArrayInputStream(body.getBytes(UTF_8)));
        Upload upload = response.getBody();

        // before mapping: no attachStatus set, considered pending
        assertThat(upload.getAttachStatus()).isEqualTo(UploadAttachStatus.PENDING);

        var docDTO = new DocumentDTO();
        docDTO.setProperties(Map.of(SYSFILE_BLOB, Map.of(UPLOAD_ID_MARKER, upload.getId())));
        mapper.updateDocumentModel(doc, docDTO);

        // after mapping: attachStatus should be READY (setPropertyValue succeeded)
        assertThat(upload.getAttachStatus()).isEqualTo(UploadAttachStatus.READY);
        assertThat(upload.getAttachDocId()).isEqualTo(doc.getId());
    }

    @Test
    public void attachStatusIsReadyForMultipleUploads() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "test", SYS_FILE);
        doc.create();

        String body = "test content";
        String mimeType = "text/plain";
        UploadDTO uploadDTO01 = new UploadDTO(null, "test01.txt", mimeType, null, null, false, 0, 0);
        UploadDTO uploadDTO02 = new UploadDTO(null, "test02.txt", mimeType, null, null, false, 0, 0);
        Upload upload01 = uploadService.handleUpload(handler, mimeType, uploadDTO01,
                new ByteArrayInputStream(body.getBytes(UTF_8))).getBody();
        Upload upload02 = uploadService.handleUpload(handler, mimeType, uploadDTO02,
                new ByteArrayInputStream(body.getBytes(UTF_8))).getBody();

        var docDTO = new DocumentDTO();
        docDTO.setProperties(Map.of(SYSFILES_BLOBS,
                Arrays.asList(Collections.singletonMap(BLOB_TYPE, Map.of(UPLOAD_ID_MARKER, upload01.getId())),
                        Collections.singletonMap(BLOB_TYPE, Map.of(UPLOAD_ID_MARKER, upload02.getId())))));
        mapper.updateDocumentModel(doc, docDTO);

        assertThat(upload01.getAttachStatus()).isEqualTo(UploadAttachStatus.READY);
        assertThat(upload02.getAttachStatus()).isEqualTo(UploadAttachStatus.READY);
        assertThat(upload01.getAttachDocId()).isEqualTo(doc.getId());
        assertThat(upload02.getAttachDocId()).isEqualTo(doc.getId());
    }

    @SuppressWarnings("resource") // don't close common pool!
    @Test
    public void attachStatusIsProcessingBeforeReady() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "test", SYS_FILE);
        doc.create();

        String body = "test content";
        String fileName = "test.txt";
        String mimeType = "text/plain";
        UploadDTO uploadDTO = new UploadDTO(null, fileName, mimeType, null, null, false, 0, 0);
        Upload upload = uploadService.handleUpload(handler, mimeType, uploadDTO,
                new ByteArrayInputStream(body.getBytes(UTF_8))).getBody();

        // before mapping: no attachStatus set, considered pending
        assertThat(upload.getAttachStatus()).isEqualTo(UploadAttachStatus.PENDING);

        Future<UploadAttachStatus> futureStatus;
        DocumentMapper._testAttachStatusBarrier = new CyclicBarrier(2);
        try {
            // observe attach status in a separate thread
            futureStatus = ForkJoinPool.commonPool().submit(() -> {
                try {
                    // wait for getBlobFromPropertyOrUpload to have set PROCESSING
                    DocumentMapper._testAttachStatusBarrier.await(10, SECONDS);
                    // read the attach status
                    UploadAttachStatus status = upload.getAttachStatus();
                    // let getBlobFromPropertyOrUpload continue
                    DocumentMapper._testAttachStatusBarrier.await(10, SECONDS);
                    return status;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            });
            // trigger the mapping (will hit the barrier mid-flight)
            var docDTO = new DocumentDTO();
            docDTO.setProperties(Map.of(SYSFILE_BLOB, Map.of(UPLOAD_ID_MARKER, upload.getId())));
            mapper.updateDocumentModel(doc, docDTO);
        } finally {
            DocumentMapper._testAttachStatusBarrier = null;
        }

        // the status observed mid-update must have been PROCESSING
        assertThat(futureStatus.get(10, SECONDS)).isEqualTo(UploadAttachStatus.PROCESSING);

        // after update: attachStatus should be READY
        assertThat(upload.getAttachStatus()).isEqualTo(UploadAttachStatus.READY);
    }

    @Test
    public void attachStatusIsErrorWhenPropertyDoesNotExist() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "test", SYS_FILE);
        doc.create();

        String body = "test content";
        String fileName = "test.txt";
        String mimeType = "text/plain";
        UploadDTO uploadDTO = new UploadDTO(null, fileName, mimeType, null, null, false, 0, 0);
        Upload upload = uploadService.handleUpload(handler, mimeType, uploadDTO,
                new ByteArrayInputStream(body.getBytes(UTF_8))).getBody();

        // before mapping: no attachStatus set, considered pending
        assertThat(upload.getAttachStatus()).isEqualTo(UploadAttachStatus.PENDING);

        // dc_foo does not exist: setPropertyValue will throw, triggering ERROR status
        var docDTO = new DocumentDTO();
        docDTO.setProperties(Map.of("dc_foo", Map.of(UPLOAD_ID_MARKER, upload.getId())));
        assertThrows(Exception.class, () -> mapper.updateDocumentModel(doc, docDTO));

        // after the failed mapping: attachStatus should be ERROR
        assertThat(upload.getAttachStatus()).isEqualTo(UploadAttachStatus.ERROR);
    }

    @Test
    public void createDocumentModelSetsAttachStatusAndId() throws Exception {
        String body = "test content";
        String fileName = "test.txt";
        String mimeType = "text/plain";
        UploadDTO uploadDTO = new UploadDTO(null, fileName, mimeType, null, null, false, 0, 0);
        Upload upload = uploadService.handleUpload(handler, mimeType, uploadDTO,
                new ByteArrayInputStream(body.getBytes(UTF_8))).getBody();

        var docDTO = new DocumentDTO();
        docDTO.setProperties(Map.of(SYSFILE_BLOB, Map.of(UPLOAD_ID_MARKER, upload.getId())));
        docDTO.setPrimaryType(SYS_FILE);
        DocumentModel doc = mapper.createDocumentModel(session, new PathRef("/"), docDTO, false);

        assertThat(upload.getAttachStatus()).isEqualTo(UploadAttachStatus.READY);
        assertThat(upload.getAttachDocId()).isEqualTo(doc.getId());
    }

}
