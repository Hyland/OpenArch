/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.resolver;

import static java.lang.Boolean.TRUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.schema.types.resolver.AbstractObjectResolver.VALIDATION_PARAMETER_KEY;
import static com.hyland.openarch.ecm.core.schema.types.resolver.TestingColorResolver.COLOR_MODE;

import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.SimpleType;
import com.hyland.openarch.ecm.core.schema.types.constraints.Constraint;
import com.hyland.openarch.ecm.core.schema.types.constraints.Constraint.Description;
import com.hyland.openarch.ecm.core.schema.types.constraints.ConstraintUtils;
import com.hyland.openarch.ecm.core.schema.types.constraints.ObjectResolverConstraint;
import com.hyland.openarch.ecm.core.schema.types.resolver.ObjectResolver;
import com.hyland.openarch.ecm.core.schema.types.resolver.ObjectResolverService;
import com.hyland.openarch.ecm.core.schema.types.resolver.TestingColorResolver;
import com.hyland.openarch.ecm.core.schema.types.resolver.TestingColorResolver.MODE;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.core.test.annotations.Granularity;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-resolver-service-contrib.xml")
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestObjectResolverService {

    @Inject
    protected CoreSession session;

    @Inject
    protected ObjectResolverService referenceService;

    protected SchemaManager schemaManager;

    @BeforeEach
    public void setUp() {
        schemaManager = session.getSchemaManager();
    }

    @Test
    public void testServiceFetching() {
        assertNotNull(referenceService);
    }

    @Test
    public void testConfigurationOnRestrictionWorks() {
        Field field = schemaManager.getField("res_isReference1");
        checkResolver(field);
    }

    @Test
    public void testConfigurationOnRestrictionAndSimpleTypeWorks() {
        Field field = schemaManager.getField("res_isReference2");
        checkResolver(field);
    }

    @Test
    public void testConfigurationOnSimpleTypeWorks() {
        Field field = schemaManager.getField("res_isReference3");
        checkResolver(field);
    }

    @Test
    public void testSimpleTypeIsNotReference() {
        Field field = schemaManager.getField("res_isNotReference1");
        checkNoResolver(field);
    }

    @Test
    public void testSimpleTypeWithRestrictionIsNotReference() {
        Field field = schemaManager.getField("res_isNotReference2");
        checkNoResolver(field);
    }

    @Test
    public void testFieldSimpleTypeWithRestrictionAndParamButNoResolverIsNotReference() {
        Field field = schemaManager.getField("res_isNotReference3");
        checkNoResolver(field);
    }

    @Test
    public void testFieldFieldWithMissingParamIsNotReference() {
        Field field = schemaManager.getField("res_isReferenceButParamMissingFailed1");
        checkNoResolver(field);
    }

    @Test
    public void testFieldFieldWithWrongParamIsNotReference() {
        Field field = schemaManager.getField("res_isReferenceButWrongParamFailed1");
        checkNoResolver(field);
    }

    private void checkNoResolver(Field field) {
        assertNull(field.getType().getObjectResolver());
        Set<Constraint> constraints = field.getType().getConstraints();
        assertNull(ConstraintUtils.getConstraint(constraints, ObjectResolverConstraint.class));
    }

    private void checkResolver(Field field) {
        assertNotNull(field.getType().getObjectResolver());
        SimpleType simpleType = (SimpleType) field.getType();
        ObjectResolver resolver = simpleType.getObjectResolver();
        assertNotNull(resolver);
        assertTrue(resolver instanceof TestingColorResolver);
        Map<String, Object> parameters = resolver.getParameters();
        assertEquals(MODE.PRIMARY.name(), parameters.get(COLOR_MODE));
        assertEquals(TRUE, parameters.get(VALIDATION_PARAMETER_KEY));
        Set<Constraint> constraints = simpleType.getConstraints();
        ObjectResolverConstraint constraint = ConstraintUtils.getConstraint(constraints,
                ObjectResolverConstraint.class);
        assertNotNull(constraint);
        Description description = constraint.getDescription();
        assertEquals("colorResolver", description.getName());
        Map<String, Object> constraintParameters = description.getParameters();
        assertEquals(MODE.PRIMARY.name(), constraintParameters.get(COLOR_MODE));
        assertEquals(TRUE, constraintParameters.get(VALIDATION_PARAMETER_KEY));
    }

}
