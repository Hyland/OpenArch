/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;

import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryInit;

/**
 * Default repository initializer that create the default DM doc hierarchy.
 */
public class DefaultRepositoryInit implements RepositoryInit {

    @Override
    public void populate(CoreSession session) {
        DocumentModel doc = session.createDocumentModel("/", "default-domain", SYS_FOLDER);
        doc.setPropertyValue(SYS_TITLE, "Domain");
        doc.create();

        doc = session.createDocumentModel("/default-domain/", "workspaces", SYS_FOLDER);
        doc.setPropertyValue(SYS_TITLE, "Workspaces");
        doc.create();

        doc = session.createDocumentModel("/default-domain/", "sections", SYS_FOLDER);
        doc.setPropertyValue(SYS_TITLE, "Workspaces");
        doc.create();

        doc = session.createDocumentModel("/default-domain/", "templates", SYS_FOLDER);
        doc.setPropertyValue(SYS_TITLE, "Templates");
        doc.create();

        doc = session.createDocumentModel("/default-domain/workspaces", "test", SYS_FOLDER);
        doc.setPropertyValue(SYS_TITLE, "workspace");
        doc.create();
    }

}
