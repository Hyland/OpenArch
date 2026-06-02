/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/) and others.
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
 *
 *  Contributors:
 *     Ananya Banerjee
 */
package org.nuxeo.ecm.core.event.impl.query;

import org.nuxeo.ecm.core.event.GenericEvent;

public class QueryEvent extends GenericEvent {

    public static final String GENERIC_TYPE_KEY_SUFFIX = "query";

    private final String queryName;
    private final long count;
    private final long elapsedTime;

    /**
     * Constructs a QueryEvent with a query name, result count, and elapsed time.
     *
     * @param queryName the name of the query (can be null)
     * @param count the number of results returned by the query
     * @param elapsedTime the time taken to execute the query, in milliseconds
     */
    public QueryEvent(String queryName, long count, long elapsedTime) {
        this.queryName = queryName;
        this.count = count;
        this.elapsedTime = elapsedTime;
    }

    /**
     * Constructs a QueryEvent without a query name.
     *
     * @param count the number of results returned by the query
     * @param elapsedTime the time taken to execute the query, in milliseconds
     */
    public QueryEvent(long count, long elapsedTime) {
        this(null, count, elapsedTime);
    }

    public String getQueryName() {
        return queryName;
    }

    public long getCount() {
        return count;
    }

    public long getElapsedTime() {
        return elapsedTime;
    }

    @Override
    public String getGenericType() {
        return GENERIC_TYPE_KEY_SUFFIX;
    }

}
