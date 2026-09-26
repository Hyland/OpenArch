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
package com.hyland.openarch.rest.document.test;

import com.hyland.openarch.rest.acl.ACEMapper;
import com.hyland.openarch.rest.acl.ACLMapper;
import com.hyland.openarch.rest.document.DocumentManager;
import com.hyland.openarch.rest.document.DocumentMapper;
import com.hyland.openarch.rest.document.ObjectMapperConfiguration;
import com.hyland.openarch.rest.document.utils.ACLChangesHandler;
import com.hyland.openarch.rest.upload.UploadHandlerCleaner;
import com.hyland.openarch.rest.upload.config.UploadServiceConfiguration;
import com.hyland.openarch.rest.upload.handler.UploadHandlerConfig;
import com.hyland.openarch.rest.vocabulary.config.VocabularyAPIConfig;
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
        VocabularyAPIConfig.class}) //
public class DocumentAPITestConfiguration {
}
