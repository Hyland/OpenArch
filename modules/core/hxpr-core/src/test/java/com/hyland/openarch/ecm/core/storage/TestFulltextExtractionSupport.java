/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_FULLTEXT_BINARY;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.NuxeoException;
import com.hyland.openarch.ecm.core.api.impl.blob.StringBlob;

public class TestFulltextExtractionSupport {

    private final FulltextExtractionSupport support = new FulltextExtractionSupport();

    // -------------------------------------------------------------------------
    // appendText
    // -------------------------------------------------------------------------

    @Test
    public void appendText_emptyCurrentAndEmptyNew() {
        assertEquals("", support.appendText("", "", 20));
    }

    @Test
    public void appendText_emptyCurrentWithNew() {
        assertEquals("new", support.appendText("", "new", 20));
    }

    @Test
    public void appendText_nullCurrentWithNew() {
        assertEquals("new", support.appendText(null, "new", 20));
    }

    @Test
    public void appendText_existingWithEmptyNew() {
        assertEquals("original", support.appendText("original", "", 20));
    }

    @Test
    public void appendText_existingWithBlankNew() {
        assertEquals("original", support.appendText("original", "   ", 20));
    }

    @Test
    public void appendText_addsSpaceBetweenExistingAndNew() {
        assertEquals("original new", support.appendText("original", "new", 20));
    }

    @Test
    public void appendText_sizeLimitAtExactCurrentLength_noAppend() {
        // sizeLimit == len(current) — already at limit, nothing appended
        assertEquals("original", support.appendText("original", "new", 8));
    }

    @Test
    public void appendText_sizeLimitBelowCurrentLength_noAppend() {
        assertEquals("original", support.appendText("original", "new", 7));
    }

    @Test
    public void appendText_sizeLimitAllowsSpaceOnly_noTextAppended() {
        // len=8, sizeLimit=9 → room for 1 char (space), but limit for text = 9-8-1=0 which is < initialSpace(1)
        assertEquals("original", support.appendText("original", "new", 9));
    }

    @Test
    public void appendText_sizeLimitAllowsOneCharOfNew() {
        // len=8, sizeLimit=10 → " n"
        assertEquals("original n", support.appendText("original", "new", 10));
    }

    @Test
    public void appendText_sizeLimitTruncatesNew() {
        assertEquals("original ne", support.appendText("original", "new", 11));
    }

    @Test
    public void appendText_sizeLimitExact() {
        assertEquals("original new", support.appendText("original", "new", 12));
    }

    @Test
    public void appendText_sizeLimitLargerThanNeeded() {
        assertEquals("original new", support.appendText("original", "new", 13));
    }

    @Test
    public void appendText_fromEmptyTruncatedToOneChar() {
        assertEquals("n", support.appendText("", "new", 1));
    }

    @Test
    public void appendText_fromEmptyNewFitsExactly() {
        assertEquals("new", support.appendText("", "new", 3));
    }

    @Test
    public void appendText_nullNewThrows() {
        assertThrows(NullPointerException.class, () -> support.appendText("original", null, 20));
    }

    @Test
    public void appendText_nullCurrentNullNewThrows() {
        assertThrows(NullPointerException.class, () -> support.appendText(null, null, 20));
    }

    // -------------------------------------------------------------------------
    // replace0chars
    // -------------------------------------------------------------------------

    @Test
    public void replace0chars_noNullBytes_unchanged() {
        assertEquals("abc", support.replace0chars("abc"));
    }

    @Test
    public void replace0chars_nullBytesReplacedWithSpaces() {
        assertEquals("abc qaz ", support.replace0chars("abc\0qaz\0"));
    }

    @Test
    public void replace0chars_onlyNullBytes() {
        assertEquals("   ", support.replace0chars("\0\0\0"));
    }

    @Test
    public void replace0chars_emptyString() {
        assertEquals("", support.replace0chars(""));
    }

    // -------------------------------------------------------------------------
    // textFromUrl
    // -------------------------------------------------------------------------

    @Test
    public void textFromUrl_readsTextUpToSizeLimit() throws Exception {
        String content = "Hello World";
        InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        URL url = new URI("http://example.com").toURL();
        String result = support.textFromUrl(is, url, content.length());
        assertEquals(content, result);
    }

    @Test
    public void textFromUrl_truncatesAtSizeLimit() throws Exception {
        String content = "Hello World";
        InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        URL url = new URI("http://example.com").toURL();
        String result = support.textFromUrl(is, url, 5);
        assertEquals("Hello", result);
    }

    @Test
    public void textFromUrl_emptyStream_returnsEmpty() throws Exception {
        InputStream is = new ByteArrayInputStream(new byte[0]);
        URL url = new URI("http://example.com").toURL();
        assertEquals("", support.textFromUrl(is, url, 100));
    }

    @Test
    public void textFromUrl_ioExceptionWrappedAsNuxeoException() throws Exception {
        URL targetUrl = new URI("http://rubbish").toURL();
        String message;
        try (InputStream targetInputStream = mock(InputStream.class)) {
            when(targetInputStream.read()).thenThrow(IOException.class);
            message = assertThrows(NuxeoException.class,
                    () -> support.textFromUrl(targetInputStream, targetUrl, 1234)).getMessage();
        }
        assertEquals("Could not read text from transform result " + targetUrl, message);
    }

    // -------------------------------------------------------------------------
    // handlePlainText
    // -------------------------------------------------------------------------

    @Test
    public void handlePlainText_setsTextOnEachDocId() {
        CoreSession session = mock(CoreSession.class);
        DocumentModel doc1 = mock(DocumentModel.class);
        DocumentModel doc2 = mock(DocumentModel.class);
        when(session.getDocument(new IdRef("doc1"))).thenReturn(doc1);
        when(session.getDocument(new IdRef("doc2"))).thenReturn(doc2);
        when(doc1.getPropertyValue(SYS_FULLTEXT_BINARY)).thenReturn("");
        when(doc2.getPropertyValue(SYS_FULLTEXT_BINARY)).thenReturn("");

        support.handlePlainText("hello", 100, session, List.of("doc1", "doc2"));

        verify(doc1).setPropertyValue(SYS_FULLTEXT_BINARY, "hello");
        verify(doc2).setPropertyValue(SYS_FULLTEXT_BINARY, "hello");
    }

    @Test
    public void handlePlainText_stripsNullBytes() {
        CoreSession session = mock(CoreSession.class);
        DocumentModel doc = mock(DocumentModel.class);
        when(session.getDocument(new IdRef("doc1"))).thenReturn(doc);
        when(doc.getPropertyValue(SYS_FULLTEXT_BINARY)).thenReturn("");

        support.handlePlainText("hel\0lo", 100, session, List.of("doc1"));

        verify(doc).setPropertyValue(SYS_FULLTEXT_BINARY, "hel lo");
    }

    @Test
    public void handlePlainText_appendsToExistingText() {
        CoreSession session = mock(CoreSession.class);
        DocumentModel doc = mock(DocumentModel.class);
        when(session.getDocument(new IdRef("doc1"))).thenReturn(doc);
        when(doc.getPropertyValue(SYS_FULLTEXT_BINARY)).thenReturn("existing");

        support.handlePlainText("new text", 100, session, List.of("doc1"));

        verify(doc).setPropertyValue(SYS_FULLTEXT_BINARY, "existing new text");
    }

    @Test
    public void handlePlainText_emptyDocIds_doesNothing() {
        CoreSession session = mock(CoreSession.class);
        support.handlePlainText("hello", 100, session, List.of());
        // no interactions with session
        verifyNoInteractions(session);
    }

    // -------------------------------------------------------------------------
    // getDocIdsToUpdate / getDocIdsToUpdateAsString
    // -------------------------------------------------------------------------

    @Test
    public void getDocIdsToUpdate_singleId() {
        assertEquals(List.of("abc"), support.getDocIdsToUpdate("abc"));
    }

    @Test
    public void getDocIdsToUpdate_multipleIds() {
        assertEquals(List.of("abc", "def", "ghi"), support.getDocIdsToUpdate("abc,def,ghi"));
    }

    @Test
    public void getDocIdsToUpdateAsString_singleId() {
        assertEquals("abc", support.getDocIdsToUpdateAsString(List.of("abc")));
    }

    @Test
    public void getDocIdsToUpdateAsString_multipleIds() {
        assertEquals("abc,def,ghi", support.getDocIdsToUpdateAsString(List.of("abc", "def", "ghi")));
    }

    @Test
    public void getDocIdsToUpdate_roundtrip() {
        List<String> ids = List.of("id1", "id2", "id3");
        assertEquals(ids, support.getDocIdsToUpdate(support.getDocIdsToUpdateAsString(ids)));
    }

    // -------------------------------------------------------------------------
    // getBlobInfo
    // -------------------------------------------------------------------------

    @Test
    public void getBlobInfo_withFilename() {
        Blob blob = new StringBlob("content", "text/plain", "UTF-8", "report.pdf");
        assertEquals("'report.pdf'", support.getBlobInfo(blob));
    }

    @Test
    public void getBlobInfo_nullFilename_usesClassAndDigest() {
        Blob blob = new StringBlob("content");
        // No filename set, digest is null too on StringBlob — check format: ClassName(null)
        String info = support.getBlobInfo(blob);
        // should match pattern: SimpleClassName(digest)
        assertEquals("StringBlob(" + blob.getDigest() + ")", info);
    }

    @Test
    public void getBlobInfo_anonymousBlob_usesParentClassName() {
        // Anonymous class — getSimpleName() returns "", so our method falls back to parent name
        Blob blob = new StringBlob("x") {
        };
        String info = support.getBlobInfo(blob);
        String name = blob.getClass().getName();
        int i = name.lastIndexOf('.');
        String expectedName = i > -1 ? name.substring(i + 1) : name;
        assertEquals(expectedName + "(" + blob.getDigest() + ")", info);
    }
}
