/*
 * (C) Copyright 2020-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.storage.mongodb;

import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.storage.mongodb.MongoDBConverter.DEFAULT_CONVERTER;

import org.bson.Document;
import com.hyland.openarch.ecm.core.api.CursorService;

import com.mongodb.client.MongoCursor;

/**
 * MongoDB implementation of the {@link CursorService}.
 * <p>
 * (Extracted as a separate class to spare the developer from the complex generics.)
 *
 * @since 11.1
 */
public class MongoDBCursorService extends CursorService<MongoCursor<Document>, Document, String> {

    public MongoDBCursorService() {
        // the cursor service only extracts ids, which needs no schema-dependent conversion,
        // so the default converter can be used
        super(ob -> (String) DEFAULT_CONVERTER.getFromBson(ob, DEFAULT_CONVERTER.keyToBson(SYS_ID), SYS_ID));
    }

}
