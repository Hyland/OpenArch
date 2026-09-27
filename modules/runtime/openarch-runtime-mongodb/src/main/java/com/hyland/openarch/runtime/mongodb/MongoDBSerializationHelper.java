/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.runtime.mongodb;

import java.lang.reflect.Array;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;

/**
 * Helper for serialization/deserialization of BSON objects
 *
 * @since 9.1
 */
public class MongoDBSerializationHelper {

    public static final String MONGODB_ID = "_id";

    private MongoDBSerializationHelper() {
        // empty
    }

    /**
     * Create a map from a BSON object
     *
     * @param doc the BSON object to parse
     * @return the new map
     */
    public static Map<String, Object> bsonToFieldMap(Document doc) {
        Map<String, Object> fieldMap = new HashMap<>();
        for (String key : doc.keySet()) {
            if (MONGODB_ID.equals(key)) {
                // skip native id
                continue;
            }
            fieldMap.put(key, bsonToValue(doc.get(key)));
        }
        return fieldMap;
    }

    protected static Object bsonToValue(Object value) {
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            if (list.isEmpty()) {
                return null;
            } else {
                Class<?> klass = Object.class;
                for (Object o : list) {
                    if (o != null) {
                        klass = scalarToSerializableClass(o.getClass());
                        break;
                    }
                }
                if (Document.class.isAssignableFrom(klass)) {
                    List<Object> l = new ArrayList<>(list.size());
                    for (Object o : list) {
                        l.add(bsonToFieldMap((Document) o));
                    }
                    return l;
                } else {
                    // turn the list into a properly-typed array
                    Object[] array = (Object[]) Array.newInstance(klass, list.size());
                    int i = 0;
                    for (Object o : list) {
                        array[i++] = scalarToObject(o);
                    }
                    return array;
                }
            }
        } else if (value instanceof Document doc) {
            return bsonToFieldMap(doc);
        } else {
            return scalarToObject(value);
        }
    }

    protected static Class<?> scalarToSerializableClass(Class<?> klass) {
        if (Date.class.isAssignableFrom(klass)) {
            return Instant.class;
        }
        return klass;
    }

    protected static Object scalarToObject(Object value) {
        if (value instanceof Date date) {
            return date.toInstant();
        }
        return value;
    }

}
