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
package org.nuxeo.rest.query;

import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Global configuration for queries management.
 * @param limit the maximum number of documents to retrieve, or 0 for all of them
 * @param maxResultWindow the maximum result window accepted: 10k matches OpenSearch default value
 * @param countUpTo If {@code -1}, count the total size without offset/limit.
 *                  If {@code 0}, don't count the total size.
 *                  If {@code n}, count the total number if there are less than n documents otherwise
 *                  set the size to {@code -1}.
 * @param trackTotalCount Determines the default action for returning totalCount with queries.
 * @param trackTotalCountUpTo If {@code -1}, not limit on totalCount to be returned.
 *                            If {@code n}, upper limit on the totalCount to be returned.
 */
@ConfigurationProperties(prefix = QueryConfig.PREFIX)
public record QueryConfig(
        Long limit,
        Long maxResultWindow,
        Long countUpTo,
        Boolean trackTotalCount,
        Integer trackTotalCountUpTo) {

    public static final String PREFIX = "nuxeo.query";

    public QueryConfig(Long limit, Long maxResultWindow, Long countUpTo, Boolean trackTotalCount, Integer trackTotalCountUpTo) {
        this.limit = Optional.ofNullable(limit).orElse(10000L);
        this.maxResultWindow = Optional.ofNullable(maxResultWindow).orElse(10000L);
        this.countUpTo = Optional.ofNullable(countUpTo).orElse(100000L);
        this.trackTotalCount = Optional.ofNullable(trackTotalCount).orElse(true);
        this.trackTotalCountUpTo = Optional.ofNullable(trackTotalCountUpTo).orElse(10000);
    }
}
