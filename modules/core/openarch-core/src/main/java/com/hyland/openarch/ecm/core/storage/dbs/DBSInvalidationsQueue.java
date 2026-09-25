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
package com.hyland.openarch.ecm.core.storage.dbs;

import com.hyland.openarch.ecm.core.storage.InvalidationsQueue;

/**
 * Queue of invalidations.
 * <p>
 * All invalidations added are accumulated (from multiple threads), then returned when asked for.
 *
 * @since 11.1
 */
public class DBSInvalidationsQueue extends InvalidationsQueue<DBSInvalidationsList> {

    public DBSInvalidationsQueue(String name) {
        super(name);
    }

    @Override
    public DBSInvalidationsList newInvalidations() {
        return new DBSInvalidationsList();
    }

}
