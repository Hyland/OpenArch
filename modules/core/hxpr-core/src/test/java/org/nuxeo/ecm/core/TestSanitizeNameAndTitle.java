/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_CONTENT;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.exceptions.base.MockitoException;
import org.nuxeo.ecm.core.api.SystemPrincipal;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.storage.dbs.DBSDocument;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
public class TestSanitizeNameAndTitle {

    private static final Object NUMERIC = new Object();

    @Test
    public void test() {
        // random numeric if no name nor title defined
        checkNameAndTitle(NUMERIC, null, NUMERIC, null);
        checkNameAndTitle(NUMERIC, "", NUMERIC, null);
        checkNameAndTitle(NUMERIC, " ", NUMERIC, null);
        checkNameAndTitle(NUMERIC, null, NUMERIC, "");
        checkNameAndTitle(NUMERIC, "", NUMERIC, "");
        checkNameAndTitle(NUMERIC, " ", NUMERIC, "");
        checkNameAndTitle(NUMERIC, null, NUMERIC, " ");
        checkNameAndTitle(NUMERIC, "", NUMERIC, " ");
        checkNameAndTitle(NUMERIC, " ", NUMERIC, " ");

        // name from title if name not defined
        checkNameAndTitle("foo", null, "  foo  ", "  foo  ");
        checkNameAndTitle("foo", "", "  foo  ", "  foo  ");
        checkNameAndTitle("foo", " ", "  foo  ", "  foo  ");

        // title from original name if title not defined
        checkNameAndTitle("foo", "  foo  ", "  foo  ", null);
        checkNameAndTitle("foo", "  foo  ", "  foo  ", "");
        checkNameAndTitle("foo", "  foo  ", "  foo  ", " ");

        // keeps normal names
        String s = "My Document.pdf";
        checkName(s, s);
        // keeps non-ascii chars and uppercase
        s = "C'est l'\u00E9t\u00E9   !!";
        checkName(s, s);

        // trims spaces
        checkName("Foo  bar", "  Foo  bar  ");
        // converts slashes
        checkName("foo-bar-baz", "foo/bar\\baz");

        // length is limited to 100 (DocumentImpl.NAME_MAX_SIZE)
        checkName("1234567890".repeat(10), "1234567890".repeat(15));

        // stupid values -> random numeric
        for (String bad : List.of("-", "./", ".", "..", " . ", " .. ", "\"", "'", "/", "//")) {
            checkName(NUMERIC, bad);
        }
    }

    private void checkName(Object expectedName, String name) {
        checkNameAndTitle(expectedName, name, "xyz", "xyz");
    }

    private void checkNameAndTitle(Object expectedName, String name, Object expectedTitle, String title) {
        DBSDocument dbsdoc = mock(DBSDocument.class);

        when(dbsdoc.getName()).thenReturn(name);
        ArgumentCaptor<String> nameCapture = ArgumentCaptor.forClass(String.class);
        doNothing().when(dbsdoc).setName(nameCapture.capture());

        when(dbsdoc.getPropertyValue(SYS_TITLE)).thenReturn(title);
        ArgumentCaptor<String> titleCapture = ArgumentCaptor.forClass(String.class);
        doNothing().when(dbsdoc).setPropertyValue(eq(SYS_TITLE), titleCapture.capture());

        // By default these tests validate generic sanitization rules (non-embeddings).
        // SysEmbeddingPolicies.ensureEmbeddingsName queries doc.getType().getName(), so stub to a non-embeddings type
        // and avoid NPEs from the mock.
        DocumentType nonEmbType = mock(DocumentType.class);
        when(nonEmbType.getName()).thenReturn(SYS_CONTENT);
        when(dbsdoc.getType()).thenReturn(nonEmbType);

        DocumentImpl doc = new DocumentImpl(dbsdoc, new SystemPrincipal());
        doc.sanitizeNameAndTitle();

        String actualName = nameCapture.getValue();
        if (expectedName == NUMERIC) {
            assertTrue(StringUtils.isNumeric(actualName), actualName);
        } else {
            assertEquals(expectedName, actualName);
        }
        String actualTitle;
        try {
            actualTitle = titleCapture.getValue();
        } catch (MockitoException e) {
            actualTitle = title;
        }
        if (expectedTitle == NUMERIC) {
            assertTrue(StringUtils.isNumeric(actualTitle), actualTitle);
        } else {
            assertEquals(expectedTitle, actualTitle);
        }
    }

}
