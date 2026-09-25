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
package com.hyland.openarch.rest.utils;

public class QueryParameters {

    private QueryParameters() {
        // Utility class
    }

    public static final String MODEL_VALIDATE_ONLY = "validateOnly";

    /**
     * Document creation parameter. When true, sys_name will be used as is and never renamed (if the name is invalid a
     * 400 error will be returned; if it is already in use a 409 error will be returned).
     */
    public static final String ENFORCE_SYS_NAME = "enforceSysName";

    /**
     * Document update/move parameter. When provided, it must be an HXQL predicate defining the condition under which
     * the update/move is done. Mutually exclusive with {@link #IF}.
     * <p>
     * If the condition is not true a 412 error will be returned and no change will be done.
     */
    public static final String CONDITION = "condition";

    /**
     * Document update/move parameter. When provided, it must be an expression defining the condition under which the
     * update/move is done. Mutually exclusive with {@link #CONDITION}.
     * <p>
     * If the condition is not true a 412 error will be returned and no change will be done.
     */
    public static final String IF = "if";

    /**
     * Create-or-update flag for PUT: when {@code true}, the document is created if it does not exist, or updated if it
     * does, in a single atomic operation.
     */
    public static final String UPSERT = "upsert";

}
