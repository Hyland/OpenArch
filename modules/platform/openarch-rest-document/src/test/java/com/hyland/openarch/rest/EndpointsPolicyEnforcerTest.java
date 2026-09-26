/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.rest;

import static com.hyland.openarch.rest.archtest.ArchtestHelpers.acceptJsonCompatMediaTypesForProduces;
import static com.hyland.openarch.rest.archtest.ArchtestHelpers.acceptOctetStreamCompatMediaTypesForProduces;
import static com.hyland.openarch.rest.archtest.ArchtestHelpers.containDescriptionResponseCodeConditions;
import static com.hyland.openarch.rest.archtest.ArchtestHelpers.containMandatoryTagElements;
import static com.hyland.openarch.rest.archtest.ArchtestHelpers.containParameterDescription;
import static com.hyland.openarch.rest.archtest.ArchtestHelpers.containsProduces;
import static com.hyland.openarch.rest.archtest.ArchtestHelpers.endpoints;
import static com.hyland.openarch.rest.archtest.ArchtestHelpers.ensureOldEndpointIsHidden;
import static com.hyland.openarch.rest.archtest.ArchtestHelpers.method_endpoints;
import static com.hyland.openarch.rest.archtest.ArchtestHelpers.operationContainsOperationId;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.hyland.openarch.rest")
public class EndpointsPolicyEnforcerTest {

    @ArchTest
    static final ArchRule be_annotated_produces = endpoints().should(containsProduces);

    @ArchTest
    static final ArchRule contain_compat_mediatypes_for_produces = endpoints().should(
            acceptJsonCompatMediaTypesForProduces).orShould(acceptOctetStreamCompatMediaTypesForProduces);

    @ArchTest
    static final ArchRule check_description_and_response_code = method_endpoints().should(
            containDescriptionResponseCodeConditions(ApiResponse.class));

    @ArchTest
    static final ArchRule check_operation_has_operation_id = method_endpoints().should(operationContainsOperationId);

    @ArchTest
    static final ArchRule ensure_old_endpoint_is_hidden = method_endpoints().should(ensureOldEndpointIsHidden);

    @ArchTest
    static final ArchRule check_Parameter_Description = method_endpoints().should(containParameterDescription);

    @ArchTest
    static final ArchRule contain_tag_elements = endpoints().should(containMandatoryTagElements);

}
