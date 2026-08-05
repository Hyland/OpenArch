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
package org.nuxeo.runtime.springboot.idp;

import java.util.List;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

public class IdpUtil {

    private static final String CLIENT_ID = "nuxeo-client";

    private static final String CLIENT_SECRET = "secret";

    private static final String GRANT_TYPE = "password";

    private static final String SCOPE = "openid profile email hxp";

    public static final String TEST_USER_NAME = "testuser";

    public static final String PASSWORD = "password";

    public static final String PASSWORD_FIELD = "password";

    private IdpUtil() {
        // utility class
    }

    public static String getAccessToken() {
        return getAccessToken(GRANT_TYPE, CLIENT_ID, CLIENT_SECRET, TEST_USER_NAME, PASSWORD, SCOPE);
    }

    static RestTemplate restTemplate = new RestTemplate();

    public static String getAccessToken(String grantType, String clientId, String clientSecret, String username,
        String password, String scope) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", grantType);
        map.add("client_id", clientId);
        map.add("client_secret", clientSecret);
        map.add("username", username);
        map.add(PASSWORD_FIELD, password);
        map.add("scope", scope);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
        ResponseEntity<AccessTokenResponse> accessTokenResult =
            restTemplate.postForEntity("http://127.0.0.1:5002/idp/connect/token", request, AccessTokenResponse.class);

        var body = accessTokenResult.getBody();
        if (body != null) {
            return body.accessToken();
        } else {
            return "";
        }
    }
}
