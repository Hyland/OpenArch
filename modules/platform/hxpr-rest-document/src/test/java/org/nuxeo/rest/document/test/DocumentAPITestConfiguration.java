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

import org.nuxeo.rest.acl.ACEMapper;
import org.nuxeo.rest.acl.ACLMapper;
import org.nuxeo.rest.document.DocumentManager;
import org.nuxeo.rest.document.DocumentMapper;
import org.nuxeo.rest.document.ObjectMapperConfiguration;
import org.nuxeo.rest.document.utils.ACLChangesHandler;
import org.nuxeo.rest.upload.UploadHandlerCleaner;
import org.nuxeo.rest.upload.UploadHandlerCleanerInterceptor;
import org.nuxeo.rest.upload.config.UploadServiceConfiguration;
import org.nuxeo.rest.upload.handler.UploadHandlerConfig;
import org.nuxeo.rest.vocabulary.config.VocabularyAPIConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import({ ACLChangesHandler.class, //
        ACEMapper.class, //
        ACLMapper.class, //
        DocumentMapper.class, //
        DocumentManager.class, //
        ObjectMapperConfiguration.class, //
        UploadHandlerConfig.class, //
        UploadHandlerCleaner.class, //
        UploadServiceConfiguration.class, //
        VocabularyAPIConfig.class, //
        UploadHandlerCleanerInterceptor.class}) //
public class DocumentAPITestConfiguration {
}
