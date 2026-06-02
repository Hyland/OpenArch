/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
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
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.storage.dbs;

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ACL;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.storage.QueryOptimizer;

/**
 * DBS-specific query optimizer.
 * <p>
 * Knows how reference prefixes are computed, especially for the ACL case which has a storage structure different than
 * what the NXQL syntax suggests.
 *
 * @since 9.3
 */
public class DBSQueryOptimizer extends QueryOptimizer {

    protected static final Pattern CORRELATED_WILDCARD_SPLIT = Pattern.compile("(([^*]+)/(\\*\\d+))/(.*)");

    public DBSQueryOptimizer(SchemaManager schemaManager) {
        super(schemaManager);
    }

    @Override
    public String getCorrelatedWildcardPrefix(String name) {
        Matcher m = CORRELATED_WILDCARD_SPLIT.matcher(name);
        if (!m.matches()) {
            return "";
        }
        String start = m.group(2);
        String wildcard = m.group(3);
        if (start.equals(SYS_ACL)) {
           return SYS_ACL + '/' + wildcard;
        } else {
            return m.group(1);
        }
    }

}
