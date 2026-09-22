/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.hxpr.springboot.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.hyland.hxpr.springboot.server.config.OpenApiConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class OpenApiConfigurationTest {
    @Test
    public void testGetSortedParameterList()
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Operation operation = new Operation().parameters(List.of(
            new HeaderParameter().name("headerParam1"),
            new HeaderParameter().name("headerParam2"),
            new PathParameter().name("pathParam1"),
            new PathParameter().name("pathParam2"),
            new QueryParameter().name("queryParam1"),
            new QueryParameter().name("queryParam2")));
        OpenApiConfiguration openApiConfiguration = new OpenApiConfiguration();
        Method getSortedParameterListMethod = OpenApiConfiguration.class.getDeclaredMethod("getSortedParameterList", Operation.class);
        getSortedParameterListMethod.setAccessible(true);
        List<Parameter> sortedParameterList = (List<Parameter>)getSortedParameterListMethod.invoke(openApiConfiguration, operation);
        assertThat(sortedParameterList.stream().map(Parameter::getName).toList())
            .isEqualTo(List.of("pathParam1", "pathParam2", "queryParam1", "queryParam2", "headerParam1", "headerParam2"));
    }

    @Test
    public void testKeepOnlyTheTagAnOperationDeclares() {
        // what springdoc emits for a controller holding both versions: the class-level tag is added to every operation,
        // after the one the v2 operation declares itself
        Operation v1 = new Operation().operationId("getFoo").tags(List.of("Foo"));
        Operation v2 = new Operation().operationId("getFooV2").tags(List.of("Foo v2", "Foo"));
        OpenAPI openApi = new OpenAPI().paths(new Paths()
            .addPathItem("/api/foo/get", new PathItem().get(v1))
            .addPathItem("/api/v2/foo/get", new PathItem().get(v2)));

        new OpenApiConfiguration().keepOnlyTheTagAnOperationDeclares().customise(openApi);

        // the v2 operation keeps only the tag it declares, so the SDK generator puts it in that API class alone
        assertThat(v2.getTags()).isEqualTo(List.of("Foo v2"));
        // an operation that only inherits its controller's tag is left alone
        assertThat(v1.getTags()).isEqualTo(List.of("Foo"));
    }

    @Test
    public void testDeclareVersionedTags() {
        // the tag a controller declares at class level is the only one listed; the ones its versioned operations name,
        // and a tag no controller declares at all, are not
        OpenAPI openApi = new OpenAPI()
            .tags(new ArrayList<>(List.of(new Tag().name("Foo").description("The Foo API"))))
            .paths(new Paths()
                .addPathItem("/api/foo", new PathItem().get(new Operation().tags(List.of("Foo"))))
                .addPathItem("/api/v2/foo", new PathItem().get(new Operation().tags(List.of("Foo v2"))))
                .addPathItem("/api/v10/foo", new PathItem().get(new Operation().tags(List.of("Foo v10"))))
                .addPathItem("/api/bar", new PathItem().get(new Operation().tags(List.of("Bar")))));

        new OpenApiConfiguration().declareVersionedTags().customise(openApi);

        // each version is now listed too, described as the API it is a version of, at that version. Bar is not a
        // version of a described API, so there is nothing to say about it and it is left undeclared, as it was.
        assertThat(openApi.getTags()).extracting(Tag::getName, Tag::getDescription)
            .containsExactlyInAnyOrder(tuple("Foo", "The Foo API"), tuple("Foo v2", "The Foo API, v2"),
                tuple("Foo v10", "The Foo API, v10"));
    }
}
