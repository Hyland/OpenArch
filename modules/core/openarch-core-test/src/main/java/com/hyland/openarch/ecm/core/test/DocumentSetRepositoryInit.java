/*
 * (C) Copyright 2018-2026 Hyland (https://www.hyland.com/).
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

import static com.hyland.openarch.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYTHING;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.WRITE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;

import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.ecm.core.api.security.ACE;
import com.hyland.openarch.ecm.core.api.security.ACL;
import com.hyland.openarch.ecm.core.api.security.impl.ACLImpl;

/**
 * @since 10.2
 */
public class DocumentSetRepositoryInit extends DefaultRepositoryInit {

    public static final String USERNAME = "bob";

    public static final String ROOT = "/default-domain/workspaces/test";

    public static final int SIZE = 3;

    public static final int DOC_BY_SIZE = 4; // 3 docs and 1 folder created at each level

    public static final int CREATED_TOTAL;

    static {
        // with SIZE = 3 AND DOC_BY_SIZE = 5, 195 documents are created
        int total = 0;
        for (int i = 1; i <= SIZE; i++) {
            total += DOC_BY_SIZE * Math.pow(SIZE, i);
        }
        CREATED_TOTAL = total;
    }

    @Override
    public void populate(CoreSession session) {
        super.populate(session);
        DocumentModel test = session.getDocument(new PathRef(ROOT));
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user(TEST_ADMIN_USER).permission(EVERYTHING).build());
        acl.add(ACE.builder().user(USERNAME).permission(WRITE).build());
        acl.add(ACE.builder().user(USERNAME).permission(READ).build());
        test.setACL(acl, false);
        createChildren(session, test, SIZE);
    }

    private void createChildren(CoreSession s, DocumentModel p, int depth) {
        if (depth > 0) {
            for (int i = 0; i < SIZE; i++) {
                s.createDocumentModel(p.getPathAsString(), p.getName() + "file" + i, SYS_FILE).create();
                s.createDocumentModel(p.getPathAsString(), p.getName() + "note" + i, "Note").create();
                DocumentModel child = s.createDocumentModel(p.getPathAsString(), p.getName() + "doc" + i, "ComplexDoc");
                child.create();
                DocumentModel folder = s.createDocumentModel(p.getPathAsString(), p.getName() + "folder" + i, SYS_FOLDER);
                folder.create();
                createChildren(s, folder, depth - 1);
            }
        }
    }
}
