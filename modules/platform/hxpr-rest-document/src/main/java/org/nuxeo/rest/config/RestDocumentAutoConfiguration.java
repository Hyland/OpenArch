/*
 * (C) Copyright 2024 Hyland (https://hyland.com/) and others.
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
package org.nuxeo.rest.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;

import java.util.List;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.Order;

@AutoConfiguration
@ComponentScan(basePackages = { "org.nuxeo.rest" })
public class RestDocumentAutoConfiguration {

    /**
     * Customizer to remove the "_1" suffix from the operationId of all operations.
     * <p>
     * This is necessary because the operationId is used as the method name in the generated client code and the "_1"
     * suffix is added to the operationId when there are multiple operations with the same name. This can include
     * multiple "_1" suffixes, so we need to recursively remove them.
     */
    @Bean
    @Order(2)
    public OpenApiCustomizer restDocumentOperationNameCustomizer() {
        return openApi -> openApi.getPaths()
                                 .values()
                                 .forEach(pathItem -> trimSuffixOperationId(pathItem.readOperations(), "_1"));
    }

    /**
     * Adds missing requestBody content for multipart PUT requests for docPath.
     * <p>
     * There appears to be a bug that causes the response to be missing for multipart PUT requests for docPath. This
     * method retrieves the requestBody content from the docId operation and adds it to the docPath operation.
     * <p>
     * This works on the basis that docPath operation will contain the same content as docId operation.
     */
    @Bean
    @Order(3)
    public OpenApiCustomizer docPathMultipartPutCustomiser() {
        return openApi -> {
            Map<String, PathItem> paths = openApi.getPaths();
            String docIdPath = paths.keySet()
                                    .stream()
                                    .filter(path -> path.endsWith("{docId}"))
                                    .findFirst()
                                    .orElse(null);
            String docPath = paths.keySet()
                                  .stream()
                                  .filter(path -> path.endsWith("{docPath}"))
                                  .findFirst()
                                  .orElse(null);
            if (docIdPath == null || docPath == null) {
                return;
            }
            paths.get(docIdPath).readOperationsMap().forEach((method, operation) -> {
                Content docIdContent = operation.getRequestBody() != null ? operation.getRequestBody().getContent()
                        : null;
                Operation docPathOperation = paths.get(docPath).readOperationsMap().get(method);
                Content docPathContent = docPathOperation.getRequestBody() != null
                        ? docPathOperation.getRequestBody().getContent()
                        : null;
                if (docIdContent == null || docPathContent == null) {
                    return;
                }
                docIdContent.forEach(docPathContent::putIfAbsent);
            });
        };
    }

    private void trimSuffixOperationId(List<Operation> operations, String suffixToTrim) {
        operations.stream()
                  .filter(operation -> operation.getOperationId() != null
                          && operation.getOperationId().endsWith(suffixToTrim))
                  .forEach(operation -> {
                      String operationId = operation.getOperationId();
                      if (operationId != null && operationId.endsWith(suffixToTrim)) {
                          operation.setOperationId(
                                  operationId.substring(0, operationId.length() - suffixToTrim.length()));
                      }
                  });
        if (operations.stream()
                      .anyMatch(operation -> operation.getOperationId() != null
                              && operation.getOperationId().endsWith(suffixToTrim))) {
            trimSuffixOperationId(operations, suffixToTrim);
        }
    }
}
