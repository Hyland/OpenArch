/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.document.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import com.hyland.openarch.ecm.core.api.security.ACE;
import org.nuxeo.rest.acl.ACEDTO;
import org.nuxeo.rest.acl.ACEMapper;
import org.nuxeo.rest.document.DocumentDTO;
import org.nuxeo.rest.user.UserDTO;
import com.hyland.openarch.runtime.mockito.MockitoFeature;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features({ MockitoFeature.class })
public class ACLChangesHandlerTest {

    @Mock
    private DocumentDTO incomingDoc;
    @Mock
    private DocumentDTO currentDoc;
    @Mock
    private ACEMapper mapper;

    private final ACEDTO aclDto1 = new ACEDTO("Adam", "admin", false, new UserDTO("Adam"), null,
            Instant.ofEpochSecond(1L), Instant.ofEpochSecond(1L), ACE.Status.EFFECTIVE);
    private final ACEDTO aclDto2 = new ACEDTO("Abdul", "admin", false, new UserDTO("Abdul"), null,
            Instant.ofEpochSecond(1L), Instant.ofEpochSecond(1L), ACE.Status.EFFECTIVE);
    private final List<ACEDTO> acls1 = List.of(aclDto1);
    private final List<ACEDTO> acls2 = List.of(aclDto2);
    private final List<ACEDTO> emptyAcls = List.of();

    @Test
    public void should_return_false_when_acls_same() {
        when(incomingDoc.getAcl()).thenReturn(acls1);
        when(currentDoc.getAcl()).thenReturn(acls1);

        assertFalse(new ACLChangesHandler(mapper).isChangeInACL(currentDoc, incomingDoc));
    }

    @Test
    public void should_return_false_when_both_acls_empty() {
        when(incomingDoc.getAcl()).thenReturn(emptyAcls);
        when(currentDoc.getAcl()).thenReturn(emptyAcls);

        assertFalse(new ACLChangesHandler(mapper).isChangeInACL(currentDoc, incomingDoc));
    }

    @Test
    public void should_return_false_when_both_acls_null() {
        when(incomingDoc.getAcl()).thenReturn(null);
        when(currentDoc.getAcl()).thenReturn(null);

        assertFalse(new ACLChangesHandler(mapper).isChangeInACL(currentDoc, incomingDoc));
    }

    @Test
    public void should_return_true_when_curent_acls_empty() {
        when(incomingDoc.getAcl()).thenReturn(acls1);
        when(currentDoc.getAcl()).thenReturn(emptyAcls);

        assertTrue(new ACLChangesHandler(mapper).isChangeInACL(currentDoc, incomingDoc));
    }

    @Test
    public void should_return_true_when_curent_acls_null() {
        when(incomingDoc.getAcl()).thenReturn(acls1);
        when(currentDoc.getAcl()).thenReturn(null);

        assertTrue(new ACLChangesHandler(mapper).isChangeInACL(currentDoc, incomingDoc));
    }

    @Test
    public void should_return_true_when_different_acls() {
        when(incomingDoc.getAcl()).thenReturn(acls1);
        when(currentDoc.getAcl()).thenReturn(acls2);

        assertTrue(new ACLChangesHandler(mapper).isChangeInACL(currentDoc, incomingDoc));
    }

    @Test
    public void should_return_true_when_current_acls_not_empty_and_incoming_empty() {
        when(incomingDoc.getAcl()).thenReturn(emptyAcls);
        when(currentDoc.getAcl()).thenReturn(acls2);

        assertTrue(new ACLChangesHandler(mapper).isChangeInACL(currentDoc, incomingDoc));
    }

    @Test
    public void should_return_false_when_current_acls_not_empty_and_incoming_null() {
        when(incomingDoc.getAcl()).thenReturn(null);
        when(currentDoc.getAcl()).thenReturn(acls2);

        // incoming null means that no update should be done
        assertFalse(new ACLChangesHandler(mapper).isChangeInACL(currentDoc, incomingDoc));
    }

}
