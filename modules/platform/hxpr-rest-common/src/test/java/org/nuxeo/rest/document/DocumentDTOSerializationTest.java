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
package org.nuxeo.rest.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.rest.acl.ACEDTO;
import org.nuxeo.rest.user.GroupDTO;
import org.nuxeo.rest.user.UserDTO;

/**
 * Guards {@link DocumentDTO}'s {@code Serializable} contract (HXCS-8625): the idempotency
 * framework caches operation results via Java serialization, so a {@code DocumentDTO} -- returned
 * by {@code RenditionsAPI.createRendition} -- must round-trip cleanly, including its nested
 * {@code UserDTO}/{@code GroupDTO}/{@code ACEDTO} fields and its dynamic {@code properties} map.
 */
class DocumentDTOSerializationTest {

    @Test
    @DisplayName("a fully populated DocumentDTO round-trips through Java serialization")
    void documentDTO_roundTripsThroughSerialization() throws Exception {
        UserDTO user = new UserDTO("user-1", "Ada", "Lovelace", "alovelace", "ada@example.com",
                Map.of("department", "engineering"));
        GroupDTO group = new GroupDTO("group-1", "Everyone");
        ACEDTO ace = new ACEDTO("admin", "Read", true, user, group, Instant.now(), Instant.now(),
                ACE.Status.EFFECTIVE);

        DocumentDTO dto = new DocumentDTO();
        dto.setId("doc-1");
        dto.setRepository("default");
        dto.setPrimaryType("File");
        dto.setTitle("A document");
        dto.setCreator(user);
        dto.setAcl(List.of(ace));
        dto.setProperties(Map.of("dc:title", "A document", "dc:count", 3L, "dc:tags", List.of("a", "b")));

        DocumentDTO roundTripped = serializeAndDeserialize(dto);

        assertNotNull(roundTripped);
        assertEquals(dto.getId(), roundTripped.getId());
        assertEquals(dto.getRepository(), roundTripped.getRepository());
        assertEquals(dto.getCreator().username(), roundTripped.getCreator().username());
        assertEquals(dto.getAcl().get(0).permission(), roundTripped.getAcl().get(0).permission());
        assertEquals(dto.getProperties(), roundTripped.getProperties());
    }

    private static DocumentDTO serializeAndDeserialize(DocumentDTO dto) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(dto);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (DocumentDTO) in.readObject();
        }
    }

}
