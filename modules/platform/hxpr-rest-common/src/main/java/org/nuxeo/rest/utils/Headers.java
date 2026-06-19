/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.rest.utils;

/**
 * Util class with constants for request header keys.
 *
 * @since TODO
 */
public class Headers {

    private Headers() {
        // utility class
    }

    public static final String ACCEPT_RANGES = "Accept-Ranges";

    public static final String CONTENT_DISPOSITION = "Content-Disposition";

    public static final String CONTENT_LENGTH = "Content-Length";

    public static final String CONTENT_RANGE = "Content-Range";

    public static final String DIGEST = "Digest";

    public static final String ETAG = "ETag";

    public static final String IF_NONE_MATCH = "If-None-Match";

    public static final String IF_MODIFIED_SINCE = "If-Modified-Since";

    public static final String LAST_MODIFIED = "Last-Modified";

    public static final String RANGE = "Range";

    public static final String USER_AGENT = "User-Agent";

    public static final String WANT_DIGEST = "Want-Digest";

    public static final String HXCS_REPOSITORY = "HXCS-REPOSITORY";

}
