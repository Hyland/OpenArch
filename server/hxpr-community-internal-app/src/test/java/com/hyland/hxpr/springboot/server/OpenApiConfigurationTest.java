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

import com.hyland.hxpr.springboot.server.config.OpenApiConfiguration;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
}
