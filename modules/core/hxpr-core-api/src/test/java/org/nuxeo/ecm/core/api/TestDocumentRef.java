/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.ecm.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.impl.DocumentLocationImpl;

@SuppressWarnings({ "EqualsBetweenInconvertibleTypes" })
public class TestDocumentRef {

    protected PathRef pathref;

    protected IdRef idref;

    public static final String PATH_TO_DOC = "path/to/doc";

    public static final String SOME_UID = "some_uid";

    @BeforeEach
    public void setUp() throws Exception {
        pathref = new PathRef(PATH_TO_DOC);
        idref = new IdRef(SOME_UID);
    }

    @Test
    public void testHashCode() {
        assertEquals(PATH_TO_DOC.hashCode(), pathref.hashCode());
        assertEquals(SOME_UID.hashCode(), idref.hashCode());
    }

    @Test
    public void testType() {
        assertEquals(DocumentRef.PATH, pathref.type());
        assertEquals(DocumentRef.ID, idref.type());
    }

    @Test
    public void testReference() {
        assertEquals(PATH_TO_DOC, pathref.reference());
        assertEquals(SOME_UID, idref.reference());
    }

    @Test
    public void testEqualsObject() {
        assertEquals(idref, idref);
        assertEquals(pathref, pathref);

        IdRef idref2 = new IdRef(SOME_UID);
        PathRef pathref2 = new PathRef("path/to", "doc");

        assertEquals(idref, idref2);
        assertEquals(pathref, pathref2);

        assertEquals(idref, idref);
        assertEquals(pathref, pathref);

        assertNotEquals(idref, pathref);
        assertNotEquals(pathref, idref);

        IdRef idref3 = new IdRef("path/to/name");
        PathRef pathref3 = new PathRef(SOME_UID);

        // pathes of different types are not comparable even though the
        // values do match
        assertNotEquals(idref, pathref3);
        assertNotEquals(pathref, idref3);

        // check that we have a consistent behavior when wrapped into document locations
        DocumentLocation server1IdRefLocation = new DocumentLocationImpl("server1", idref);
        DocumentLocation server1IdRef2Location = new DocumentLocationImpl("server1", idref2);
        DocumentLocation server1IdRef3Location = new DocumentLocationImpl("server1", idref3);
        DocumentLocation server1PathRefLocation = new DocumentLocationImpl("server1", pathref);
        DocumentLocation server1PathRef2Location = new DocumentLocationImpl("server1", pathref2);
        DocumentLocation server1PathRef3Location = new DocumentLocationImpl("server1", pathref3);
        DocumentLocation server2IdRefLocation = new DocumentLocationImpl("server2", idref);
        DocumentLocation server2PathRefLocation = new DocumentLocationImpl("server2", pathref);

        assertEquals(server1IdRefLocation, server1IdRefLocation);
        assertEquals(server1IdRefLocation, server1IdRef2Location);
        assertNotEquals(server1IdRefLocation, server1IdRef3Location);
        assertNotEquals(server1IdRefLocation, server1PathRefLocation);
        assertNotEquals(server1IdRefLocation, server1PathRef3Location);
        assertNotEquals(server1IdRefLocation, server2IdRefLocation);

        assertEquals(server1PathRefLocation, server1PathRef2Location);
        assertNotEquals(server1PathRefLocation, server1PathRef3Location);
        assertNotEquals(server1PathRefLocation, server2PathRefLocation);

        // check hashCode consistency
        assertEquals(server1IdRefLocation.hashCode(), server1IdRef2Location.hashCode());
        assertNotEquals(server1IdRefLocation.hashCode(), server2IdRefLocation.hashCode());
        assertNotEquals(server1IdRefLocation.hashCode(), server1IdRef3Location.hashCode());
        assertNotEquals(server1IdRefLocation.hashCode(), server1PathRef2Location.hashCode());

        assertEquals(server1PathRefLocation.hashCode(), server1PathRef2Location.hashCode());
        assertNotEquals(server1PathRefLocation.hashCode(), server1PathRef3Location.hashCode());
        assertNotEquals(server1PathRefLocation.hashCode(), server2PathRefLocation.hashCode());
    }

    @Test
    public void testToString() {
        assertEquals(PATH_TO_DOC, pathref.toString());
        assertEquals(SOME_UID, idref.toString());
    }

}
