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

import java.util.HashMap;
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

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class XSDTypes {

    private static final Map<String, SimpleType> xsdTypes = new HashMap<>();

    private static final Map<String, SimpleType> builtinTypes = new HashMap<>();

    // Utility class.
    private XSDTypes() {
    }

    private static void register(String name, SimpleType type) {
        xsdTypes.put(name, type);
        builtinTypes.put(type.getName(), type);
    }

    static {
        register("string", StringType.INSTANCE);
        register("normalizedString", StringType.INSTANCE);
        register("integer", LongType.INSTANCE);
        register("long", LongType.INSTANCE);
        register("int", LongType.INSTANCE);
        register("short", LongType.INSTANCE);
        register("unsignedShort", LongType.INSTANCE);
        register("positiveInteger", LongType.INSTANCE);
        register("nonPositiveInteger", LongType.INSTANCE);
        register("nonNegativeInteger", LongType.INSTANCE);
        register("unsignedInt", LongType.INSTANCE);
        register("unsignedLong", LongType.INSTANCE);
        register("decimal", DecimalType.INSTANCE);
        register("double", DoubleType.INSTANCE);
        register("float", DoubleType.INSTANCE);
        register("date", DateType.INSTANCE);
        register("dateTime", DateType.INSTANCE);
        register("time", DateType.INSTANCE);
        register("boolean", BooleanType.INSTANCE);
        register("base64Binary", BinaryType.INSTANCE);
        register("hexBinary", BinaryType.INSTANCE);
        register("duration", StringType.INSTANCE);
        register("anyType", AnyType.INSTANCE);
        register("anySimpleType", StringType.INSTANCE);
        register("anyURI", StringType.INSTANCE);
    }

    public static SimpleType getXSDType(String name) {
        return xsdTypes.get(name);
    }

    public static SimpleType getBuiltinType(String name) {
        return builtinTypes.get(name);
    }

}
