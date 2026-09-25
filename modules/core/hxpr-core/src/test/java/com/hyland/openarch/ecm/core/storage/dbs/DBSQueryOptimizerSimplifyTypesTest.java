/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.query.sql.HXQLParser;
import com.hyland.openarch.ecm.core.query.sql.model.Statement;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;

/**
 * Exercises {@link com.hyland.openarch.ecm.core.storage.QueryOptimizer#simplifyTypes} for {@code sys_primaryType}: the FROM types
 * and a WHERE primary-type constraint are intersected and re-added in simplified form.
 */
@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
public class DBSQueryOptimizerSimplifyTypesTest {

    /** Builds an optimizer whose FROM-type expansion is driven by the given map (avoids a SchemaManager). */
    private static DBSQueryOptimizer optimizer(Map<String, Set<String>> extending) {
        return new DBSQueryOptimizer(null) {
            @Override
            protected Set<String> getDocumentTypeNamesExtending(String typeName) {
                return extending.getOrDefault(typeName, Collections.singleton(typeName));
            }

            @Override
            protected Set<String> getDocumentTypeNamesForMixin(String mixin) {
                return Collections.emptySet();
            }
        };
    }

    private static String optimizedWhere(DBSQueryOptimizer optimizer, String nxql) {
        Statement query = optimizer.optimize(HXQLParser.parse(nxql));
        return query.getWhereClause().predicate().toString();
    }

    @Test
    public void whereEqIntersectsFromTypes() {
        var opt = optimizer(Map.of("Document", Set.of("Document", "SysFile", "SysFolder")));

        String where = optimizedWhere(opt, "SELECT p FROM Document WHERE sys_primaryType = 'SysFile'");

        assertTrue(where.contains("sys_primaryType = 'SysFile'"), where);
        assertFalse(where.contains("SysFolder"), where); // simplified away
    }

    @Test
    public void whereInIntersectsFromTypes() {
        var opt = optimizer(Map.of("Document", Set.of("Document", "SysFile", "SysFolder")));

        String where = optimizedWhere(opt, "SELECT p FROM Document WHERE sys_primaryType IN ('SysFile', 'SysFolder')");

        assertTrue(where.contains("sys_primaryType IN"), where);
        assertTrue(where.contains("SysFile"), where);
        assertTrue(where.contains("SysFolder"), where);
        assertFalse(where.contains("'Document'"), where); // Document dropped by the intersection
    }

    @Test
    public void contradictoryTypesYieldNoSuchType() {
        var opt = optimizer(Map.of("SysFile", Set.of("SysFile")));

        String where = optimizedWhere(opt, "SELECT p FROM SysFile WHERE sys_primaryType = 'SysFolder'");

        assertTrue(where.contains("__NOSUCHTYPE__"), where);
    }

    @Test
    public void multipleFromTypesReaddedAsIn() {
        var opt = optimizer(Map.of("Document", Set.of("SysFile", "SysFolder")));

        String where = optimizedWhere(opt, "SELECT p FROM Document");

        assertTrue(where.contains("sys_primaryType IN"), where);
        assertTrue(where.contains("SysFile"), where);
        assertTrue(where.contains("SysFolder"), where);
    }
}
