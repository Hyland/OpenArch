/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.rest.query.conf;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import com.hyland.openarch.rest.query.spec.NamedQueryDefinition;
import com.hyland.openarch.rest.query.spec.NamedQueryRegistry;
import org.springframework.stereotype.Component;

@Component
public class DefaultNamedQueryRegistry implements NamedQueryRegistry {

    List<NamedQueryDefinition> queries;

    Map<String, NamedQueryDefinition> registry;

    public DefaultNamedQueryRegistry(List<NamedQueryDefinition> queries) {
        this.queries = queries;
    }

    @PostConstruct
    void init() {
        registry = new HashMap<>();
        if (queries == null) {
            return;
        }
        for (NamedQueryDefinition query : queries) {
            registry.put(query.name(), query);
            query.aliases().stream().filter(a -> !registry.containsKey(a)).forEach(a -> registry.put(a, query));
        }
    }

    @PreDestroy
    void destroy() {
        registry = null;
    }

    @Override
    public Map<String, NamedQueryDefinition> getNamedQueries() {
        return Collections.unmodifiableMap(registry);
    }

    @Override
    public NamedQueryDefinition getNamedQuery(String name) {
        return registry.get(name);
    }

}
