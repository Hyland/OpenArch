/*
 * (C) Copyright 2019-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.common.function;

import static java.util.stream.Collectors.toMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Helper class to handle {@link java.util.function} classes.
 *
 * @since 11.1
 */
public class FunctionUtils {

    private FunctionUtils() {
        // utility class
    }

    /**
     * Method allowing to throw a checked exception as an unchecked one.
     *
     * @param <T> type of exception to throw
     * @param <R> type of returned object to hide
     */
    @SuppressWarnings("unchecked")
    protected static <T extends Exception, R> R sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

    /**
     * Returns a linked hash map sorted by keys, and whose values have been mapped.
     *
     * @param K the key type
     * @param V the original value type
     * @param W the mapped value type
     */
    public static <K extends Comparable<? super K>, V, W> Map<K, W> sortByKeyAndMapValue(Map<K, V> map,
            Function<Entry<K, V>, W> valueMapper) {
        return map.entrySet()
                  .stream()
                  .sorted(Entry.comparingByKey())
                  .collect(toMap(Entry::getKey, valueMapper, (u, v) -> u, LinkedHashMap::new));
    }

}
