/*
 * (C) Copyright 2024 Hyland (http://www.hyland.com/) and others.
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
 *     Abdul Mohammed
 */
package org.nuxeo.rest.query.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.rest.query.QueryConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties(QueryConfig.class)
@TestPropertySource(properties = {
        "nuxeo.query.limit=10000",
        "nuxeo.query.countUpTo=100000",
        "nuxeo.query.maxResultWindow=10000",
        "nuxeo.query.trackTotalCount=true",
        "nuxeo.query.trackTotalCountUpToo=10000"
})
public class QueryConfigBeanMethodsUnitTest {

    @Test
    public void testQueryConfigInfoBean(@Autowired QueryConfig queryConfig) {
        // Assert
        assertEquals(10000, queryConfig.limit());
        assertEquals(100000, queryConfig.countUpTo());
        assertEquals(10000, queryConfig.maxResultWindow());
        assertEquals(true, queryConfig.trackTotalCount());
        assertEquals(10000, queryConfig.trackTotalCountUpTo());
    }

    @Test
    public void testQueryConfigInfoBeanPropertyNamesWithDashes() {
        // A map of QueryConfig application properties with dashes in property names
        Map<String, String> properties = new HashMap<>();
        properties.put("nuxeo.query.count-up-to", "100");
        properties.put("nuxeo.query.max-result-window", "1000");
        properties.put("nuxeo.query.track-total-count", String.valueOf(true));
        properties.put("nuxeo.query.track-total-count-up-to", "10000");

        BindResult<QueryConfig> result = getQueryConfigBindResult(properties);

        assertQueryConfigBindResult(result);
    }


    @Test
    public void testQueryConfigInfoBeanPropertyNamesWithOtherCommonReferences() {
        // A map of QueryConfig application properties with assorted ways of referencing the properties
        Map<String, String> properties = new HashMap<>();
        properties.put("nuxeo.query.COUNT-UP-TO", "100");
        properties.put("nuxeo.query.MAX_RESULT_WINDOW", "1000");
        properties.put("nuxeo.query.TRACK-TOTAL_COUNT", String.valueOf(true));
        properties.put("nuxeo.query.track_total_count_up_to", "10000");

        BindResult<QueryConfig> result = getQueryConfigBindResult(properties);

        assertQueryConfigBindResult(result);
    }

    private BindResult<QueryConfig> getQueryConfigBindResult(Map<String, String> properties) {
        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);
        return binder.bind("nuxeo.query", QueryConfig.class);
    }

    private void assertQueryConfigBindResult(BindResult<QueryConfig> result) {
        // Should return true if bound successfully.
        assertTrue(result.isBound());

        // Asserts configuration values.
        QueryConfig config = result.get();
        assertEquals(100, config.countUpTo());
        assertEquals(1000, config.maxResultWindow());
        assertEquals(true, config.trackTotalCount());
        assertEquals(10000, config.trackTotalCountUpTo());
    }

}
