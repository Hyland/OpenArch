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

package com.hyland.openarch.ecm.core.schema;

import java.util.Map;

import com.hyland.openarch.ecm.core.schema.types.SimpleType;
import com.hyland.openarch.ecm.core.schema.types.primitives.AnyType;
import com.hyland.openarch.ecm.core.schema.types.primitives.BinaryType;
import com.hyland.openarch.ecm.core.schema.types.primitives.BooleanType;
import com.hyland.openarch.ecm.core.schema.types.primitives.DateType;
import com.hyland.openarch.ecm.core.schema.types.primitives.DecimalType;
import com.hyland.openarch.ecm.core.schema.types.primitives.DoubleType;
import com.hyland.openarch.ecm.core.schema.types.primitives.LongType;
import com.hyland.openarch.ecm.core.schema.types.primitives.StringType;

/** Primitive type names shared by bootstrap, persisted models and the REST API. */
public class BuiltinTypes {

    private static final Map<String, SimpleType> TYPES = Map.of( //
            "string", StringType.INSTANCE, //
            "long", LongType.INSTANCE, //
            "decimal", DecimalType.INSTANCE, //
            "double", DoubleType.INSTANCE, //
            "date", DateType.INSTANCE, //
            "boolean", BooleanType.INSTANCE, //
            "binary", BinaryType.INSTANCE, //
            "any", AnyType.INSTANCE);

    // Utility class.
    private BuiltinTypes() {
    }

    public static SimpleType getBuiltinType(String name) {
        return name == null ? null : TYPES.get(name);
    }
}
