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

package org.nuxeo.ecm.core.api.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.validation.ConstraintViolation.PathNode;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.NotNullConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.NumericIntervalConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.PatternConstraint;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-validation-service-contrib.xml")
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestDocumentValidationService {

    // it comes from the message bundle messages_en.properties
    private static final String MESSAGE_FOR_USERS_FIRSTNAME = "message_for_users_firstname";

    private static final String MESSAGE_FOR_GROUP_CODE = "message_for_groupCode";

    private static final String SINGLE_ERROR_MESSAGE = "Constraint violation thrown on property vs_users/0/firstname: '"
            + MESSAGE_FOR_USERS_FIRSTNAME + "'";

    private static final String SINGLE_ERROR_MESSAGE_EMPTY_XPATH = "Constraint violation thrown: '"
            + MESSAGE_FOR_USERS_FIRSTNAME + "'";

    private static final String MULTIPLE_ERROR_MESSAGE = "2 constraint violation(s) thrown. First one thrown on property vs_groupCode: '"
            + MESSAGE_FOR_GROUP_CODE + "', call DocumentValidationException.getViolations() to get the others";

    private static final String MULTIPLE_ERROR_MESSAGE_EMPTY_XPATH = "2 constraint violation(s) thrown. First one thrown: '"
            + MESSAGE_FOR_GROUP_CODE + "', call DocumentValidationException.getViolations() to get the others";

    public static final String SIMPLE_FIELD = "vs_groupCode";

    private static final String COMPLEX_FIELD = "vs_manager";

    private static final String LIST_FIELD = "vs_roles";

    private static final String COMPLEX_LIST_FIELD = "vs_users";

    public static final String STRING_LIST_PROPS_FIELD = "vs_simpleStringList";

    public static final String STRING_LIST_ARRAY_FIELD = "vs_anotherSimpleList";

    public static final String SIMPLE_DEFAULT_MANDATORY = "vs_required_simpleType";

    public static final String COMPLEX_MANDATORY = "vs_required_complexType";

    public static final String COMPLEX_DUMMY_RESOLVER_1 = "vs_dummyComplex1";

    public static final String COMPLEX_DUMMY_RESOLVER_2 = "vs_dummyComplex2";

    @Inject
    protected CoreSession session;

    @Inject
    protected DocumentValidationService validator;

    protected SchemaManager schemaManager;

    private DocumentModel doc;

    @BeforeEach
    public void setUp() {
        schemaManager = session.getSchemaManager();
        doc = session.createDocumentModel("/", "doc1", "ValidatedUserGroup");
        doc.setPropertyValue(STRING_LIST_PROPS_FIELD, new String[] { "aStr" }); // set mandatory list
        doc.setPropertyValue(STRING_LIST_ARRAY_FIELD, new String[] { "anotherStr" }); // set mandatory list
        doc.setPropertyValue(SIMPLE_DEFAULT_MANDATORY, "simplenotnull"); // XXX has a default!
        Map<String, String> complex = new HashMap<>();
        complex.put("a_string", "not_null");
        doc.setPropertyValue(COMPLEX_MANDATORY, complex);
        doc.create();
    }

    @Test
    public void testDocumentWithoutViolation() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        checkOk();
    }

    @Test
    public void testDocumentWithViolation1() {
        checkNotNullOnField(SIMPLE_FIELD);
    }

    @Test
    public void testDocumentWithViolation2() {
        doc.setPropertyValue(SIMPLE_FIELD, -12345);
        checkNumericIntervalOnGroupCode();
    }

    @Test
    public void testDocumentDirtyWithViolation() {
        doc.setPropertyValue(SIMPLE_FIELD, 10);
        doc.setPropertyValue(SIMPLE_FIELD, null);
        checkNotNullOnField(SIMPLE_FIELD);
    }

    /**
     * @since 10.3
     */
    @Test
    public void testMandatorySimpleTypeDefaultValueWithoutViolation() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        // don't set SIMPLE_DEFAULT_MANDATORY property, use case is: property has a default value and user didn't set it
        // in this case validation shouldn't report any violation
        checkOk();
        // check there's still a violation if user explicitly set the property to null
        doc.setPropertyValue(SIMPLE_DEFAULT_MANDATORY, null);
        checkNotNullOnField(SIMPLE_DEFAULT_MANDATORY);
    }

    @Test
    public void testMandatoryList() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);

        doc.setPropertyValue(STRING_LIST_PROPS_FIELD, new String[] { "nowValid" });

        doc.setPropertyValue(STRING_LIST_ARRAY_FIELD, null);
        checkNotNullOnField(STRING_LIST_ARRAY_FIELD);

        doc.setPropertyValue(STRING_LIST_ARRAY_FIELD, new String[] { "validValue" });
        doc.setPropertyValue(STRING_LIST_ARRAY_FIELD, List.of());
        checkNotNullOnField(STRING_LIST_ARRAY_FIELD);

        doc.setPropertyValue(STRING_LIST_ARRAY_FIELD, new String[] { "validValue" });
        doc.setPropertyValue(STRING_LIST_ARRAY_FIELD, new String[] {});
        checkNotNullOnField(STRING_LIST_ARRAY_FIELD);

        doc.setPropertyValue(STRING_LIST_ARRAY_FIELD, new String[] { "nowValid" });

        doc.setPropertyValue(STRING_LIST_PROPS_FIELD, null);
        checkNotNullOnField(STRING_LIST_PROPS_FIELD);
    }

    @Test
    public void testComplexFieldWithoutViolation() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        doc.setPropertyValue(COMPLEX_FIELD, createUser("Bob", "Sponge"));
        checkOk();
    }

    @Test
    public void testComplexFieldWithSubFieldViolation1() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        doc.setPropertyValue(COMPLEX_FIELD, createUser(null, "Sponge"));
        checkNotNullOnManagerFirstname();
    }

    @Test
    public void testComplexFieldWithSubFieldViolation2() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        doc.setPropertyValue(COMPLEX_FIELD, createUser("   ", "Sponge"));
        checkPatternOnManagerFirstname();
    }

    @Test
    public void testListFieldWithoutViolation() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        ArrayList<String> value = createRoles("role1", "role2", "role3");
        doc.setPropertyValue(LIST_FIELD, value);
        checkOk();
    }

    @Test
    public void testListFieldWithSubFieldNullViolation1() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        ArrayList<String> value = createRoles("role1", null, "role3");
        doc.setPropertyValue(LIST_FIELD, value);
        checkNotNullOnRoles();
    }

    @Test
    public void testListFieldWithSubFieldPatternViolation2() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        ArrayList<String> value = createRoles("role1", "role2", "invalid role3");
        doc.setPropertyValue(LIST_FIELD, value);
        checkPatternOnRoles();
    }

    @Test
    public void testComplexListFieldWithoutViolation() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        ArrayList<Map<String, String>> value = new ArrayList<>();
        value.add(createUser("Bob", "Sponge"));
        value.add(createUser("Patrick", "Star"));
        value.add(createUser("Sandy", "Cheeks"));
        doc.setPropertyValue(COMPLEX_LIST_FIELD, value);
        checkOk();
    }

    @Test
    public void testComplexListFieldWithItemSubFieldNullViolation1() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        ArrayList<Map<String, String>> value = new ArrayList<>();
        value.add(createUser("Bob", "Sponge"));
        value.add(createUser("Patrick", "Star"));
        value.add(createUser(null, "Sandy"));
        doc.setPropertyValue(COMPLEX_LIST_FIELD, value);
        checkNotNullOnUsersFirstname();
    }

    @Test
    public void testComplexListFieldWithSubFieldPatternViolation2() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        ArrayList<Map<String, String>> value = new ArrayList<>();
        value.add(createUser("Bob", "Sponge"));
        value.add(createUser("Patrick", "Star"));
        value.add(createUser("   ", "Cheeks"));
        doc.setPropertyValue(COMPLEX_LIST_FIELD, value);
        checkPatternOnUsersFirstname();
    }

    @Test
    public void testComplexListFieldWithMultipleSubFieldViolations() {
        doc.setPropertyValue(SIMPLE_FIELD, 12345);
        ArrayList<Map<String, String>> value = new ArrayList<>();
        value.add(createUser("   ", "Sponge"));
        value.add(createUser("Patrick", "Star"));
        value.add(createUser("   ", "Cheeks"));
        value.add(createUser("Bob", null));
        value.add(createUser("   ", null));
        doc.setPropertyValue(COMPLEX_LIST_FIELD, value);
        List<ValidationViolation> violations = validator.validate(doc).asList();
        assertEquals(5, violations.size());
        checkPatternOnUsersFirstname(violations.get(0), 0);
        checkPatternOnUsersFirstname(violations.get(1), 2);
        checkNotNullOnUsersLastname(violations.get(2), 3);
        checkPatternOnUsersFirstname(violations.get(3), 4);
        checkNotNullOnUsersLastname(violations.get(4), 4);
    }

    @Test
    public void testValidateArrayPropertyWithConstraint() {
        DocumentValidationReport violations;
        doc.setPropertyValue("vs_groupCode", 123);
        doc.setPropertyValue("vs_simpleList", new String[] {});
        violations = validator.validate(doc);
        assertFalse(violations.hasViolation());
        doc.setPropertyValue("vs_simpleList", new String[] { "", "123" });
        violations = validator.validate(doc);
        assertFalse(violations.hasViolation());
        doc.setPropertyValue("vs_simpleList", new String[] { "", "123", "ABC" });
        violations = validator.validate(doc);
        assertTrue(violations.hasViolation());
        assertEquals(1, violations.numberOfViolations());
        assertTrue(((ConstraintViolation) violations.asList().get(0)).getConstraint() instanceof PatternConstraint);
    }

    @Test
    public void testValidateDocumentPropertyViolationMessage() {
        DocumentValidationReport violations;
        doc.setPropertyValue("vs_groupCode", 123);
        HashMap<String, String> user = new HashMap<>();
        user.put("lastname", "The kid");
        doc.setPropertyValue("vs_users", List.of(user));
        violations = validator.validate(doc);
        assertTrue(violations.hasViolation());
        List<ValidationViolation> violationList = violations.asList();
        assertEquals(1, violationList.size());
        assertTrue(violationList.get(0) instanceof ConstraintViolation);
        ConstraintViolation violation = (ConstraintViolation) violationList.get(0);
        assertEquals(MESSAGE_FOR_USERS_FIRSTNAME, violation.getMessage(Locale.ENGLISH));
    }

    // NXP-29680 + NXP-29983
    @Test
    public void testDocumentValidationReportMessage() {
        // multiple errors
        doc.setPropertyValue("vs_users", List.of(Map.of("lastname", "The kid")));
        DocumentValidationReport report = validator.validate(doc);
        assertTrue(report.hasViolation());
        String expectedMessage = String.join("\n", MESSAGE_FOR_GROUP_CODE, MESSAGE_FOR_USERS_FIRSTNAME);
        assertEquals(expectedMessage, report.toString());
        DocumentValidationException e = new DocumentValidationException(report);
        expectedMessage = MULTIPLE_ERROR_MESSAGE;
        assertEquals(expectedMessage, e.getMessage());

        // one error
        doc.setPropertyValue("vs_groupCode", 123);
        report = validator.validate(doc);
        assertTrue(report.hasViolation());
        assertEquals(MESSAGE_FOR_USERS_FIRSTNAME, report.toString());
        e = new DocumentValidationException(report);
        assertEquals(SINGLE_ERROR_MESSAGE, e.getMessage());
    }

    // NXP-29983
    @Test
    public void testErrorMessageWithEmptyXPath() {
        // multiple errors
        List<ValidationViolation> violations = new ArrayList<>();
        violations.add(new GlobalViolation(MESSAGE_FOR_GROUP_CODE));
        violations.add(new GlobalViolation(MESSAGE_FOR_USERS_FIRSTNAME));
        DocumentValidationReport report = new DocumentValidationReport(violations);
        assertTrue(report.hasViolation());
        String expectedMessage = String.join("\n", MESSAGE_FOR_GROUP_CODE, MESSAGE_FOR_USERS_FIRSTNAME);
        assertEquals(expectedMessage, report.toString());
        DocumentValidationException e = new DocumentValidationException(report);
        expectedMessage = MULTIPLE_ERROR_MESSAGE_EMPTY_XPATH;
        assertEquals(expectedMessage, e.getMessage());

        // one error
        violations = new ArrayList<>();
        violations.add(new GlobalViolation(MESSAGE_FOR_USERS_FIRSTNAME));
        report = new DocumentValidationReport(violations);
        assertTrue(report.hasViolation());
        assertEquals(MESSAGE_FOR_USERS_FIRSTNAME, report.toString());
        e = new DocumentValidationException(report);
        expectedMessage = SINGLE_ERROR_MESSAGE_EMPTY_XPATH;

        assertEquals(expectedMessage, e.getMessage());
    }

    // //////////////////////////////////////
    // End of the tests : Usefull methods //

    private ArrayList<String> createRoles(String... roles) {
        return new ArrayList<>(Arrays.asList(roles));
    }

    private HashMap<String, String> createUser(String firstname, String lastname) {
        HashMap<String, String> user = new HashMap<>();
        user.put("firstname", firstname);
        user.put("lastname", lastname);
        return user;
    }

    private DocumentValidationReport getReport() {
        return validator.validate(doc);
    }

    private void checkOk() {
        DocumentValidationReport report = getReport();
        List<ValidationViolation> violations = report.asList();
        assertEquals(0, violations.size());
    }

    private void checkNotNullOnField(String field) {
        DocumentValidationReport report = getReport();
        List<ValidationViolation> violations = report.asList();
        assertEquals(1, violations.size());
        assertTrue(violations.get(0) instanceof ConstraintViolation);
        ConstraintViolation cv = (ConstraintViolation) violations.get(0);
        Constraint constraint = cv.getConstraint();
        assertTrue(constraint instanceof NotNullConstraint);
        assertNull(cv.getInvalidValue());
        assertEquals(field, cv.getPath());

        assertEquals(1, cv.getPathList().size());
        assertEquals(field, cv.getPathList().get(0).getField().getName());
    }

    private void checkNumericIntervalOnGroupCode() {
        DocumentValidationReport report = getReport();
        List<ValidationViolation> violations = report.asList();
        assertEquals(1, violations.size());
        assertTrue(violations.get(0) instanceof ConstraintViolation);
        ConstraintViolation cv = (ConstraintViolation) violations.get(0);
        Constraint constraint = cv.getConstraint();
        assertTrue(constraint instanceof NumericIntervalConstraint);
        assertEquals(-12345L, cv.getInvalidValue());
        assertEquals(SIMPLE_FIELD, cv.getPath());

        assertEquals(1, cv.getPathList().size());
        assertEquals(SIMPLE_FIELD, cv.getPathList().get(0).getField().getName());
        assertEquals(-12345L, cv.getInvalidValue());
    }

    private void checkNotNullOnManagerFirstname() {
        DocumentValidationReport report = getReport();
        List<ValidationViolation> violations = report.asList();
        assertEquals(1, violations.size());
        assertTrue(violations.get(0) instanceof ConstraintViolation);
        ConstraintViolation cv = (ConstraintViolation) violations.get(0);
        assertEquals(NotNullConstraint.get(), cv.getConstraint());
        assertTrue(cv.getConstraint() instanceof NotNullConstraint);
        assertNull(cv.getInvalidValue());
        assertEquals("vs_manager/firstname", cv.getPath());

        assertEquals(2, cv.getPathList().size());
        assertEquals("vs_manager", cv.getPathList().get(0).getField().getName());
        assertEquals("firstname", cv.getPathList().get(1).getField().getName());
    }

    private void checkPatternOnManagerFirstname() {
        DocumentValidationReport report = getReport();
        List<ValidationViolation> violations = report.asList();
        assertEquals(1, violations.size());
        assertTrue(violations.get(0) instanceof ConstraintViolation);
        ConstraintViolation cv = (ConstraintViolation) violations.get(0);
        assertTrue(cv.getConstraint() instanceof PatternConstraint);
        assertEquals("   ", cv.getInvalidValue());
        assertEquals("vs_manager/firstname", cv.getPath());

        assertEquals(2, cv.getPathList().size());
        assertEquals("vs_manager", cv.getPathList().get(0).getField().getName());
        assertEquals("firstname", cv.getPathList().get(1).getField().getName());
    }

    private void checkNotNullOnRoles() {
        DocumentValidationReport report = getReport();
        List<ValidationViolation> violations = report.asList();
        assertEquals(1, violations.size());
        assertTrue(violations.get(0) instanceof ConstraintViolation);
        ConstraintViolation cv = (ConstraintViolation) violations.get(0);
        assertTrue(cv.getConstraint() instanceof NotNullConstraint);
        assertNull(cv.getInvalidValue());
        assertEquals("vs_roles/1", cv.getPath());

        assertEquals(1, cv.getPathList().size());
        PathNode node1 = cv.getPathList().get(0);
        assertEquals("vs_roles/1", node1.toString());
        assertEquals("vs_roles", node1.getField().getName());
    }

    private void checkPatternOnRoles() {
        DocumentValidationReport report = getReport();
        List<ValidationViolation> violations = report.asList();
        assertEquals(1, violations.size());
        assertTrue(violations.get(0) instanceof ConstraintViolation);
        ConstraintViolation cv = (ConstraintViolation) violations.get(0);
        assertTrue(cv.getConstraint() instanceof PatternConstraint);
        assertEquals("invalid role3", cv.getInvalidValue());
        assertEquals("vs_roles/2", cv.getPath());

        assertEquals(1, cv.getPathList().size());
        PathNode node1 = cv.getPathList().get(0);
        assertEquals("vs_roles/2", node1.toString());
        assertEquals("vs_roles", node1.getField().getName());
    }

    private void checkNotNullOnUsersFirstname() {
        DocumentValidationReport report = getReport();
        List<ValidationViolation> violations = report.asList();
        assertEquals(1, violations.size());
        assertTrue(violations.get(0) instanceof ConstraintViolation);
        ConstraintViolation cv = (ConstraintViolation) violations.get(0);
        assertTrue(cv.getConstraint() instanceof NotNullConstraint);
        assertNull(cv.getInvalidValue());
        assertEquals(2, cv.getPathList().size());
        assertEquals("vs_users/2/firstname", cv.getPath());
    }

    private void checkPatternOnUsersFirstname() {
        DocumentValidationReport report = getReport();
        List<ValidationViolation> violations = report.asList();
        assertEquals(1, violations.size());
        checkPatternOnUsersFirstname(violations.get(0), 2);
    }

    private void checkNotNullOnUsersLastname(ValidationViolation v, int expectedIndex) {
        assertTrue(v instanceof ConstraintViolation);
        ConstraintViolation cv = (ConstraintViolation) v;
        assertTrue(cv.getConstraint() instanceof NotNullConstraint);
        assertNull(cv.getInvalidValue());
        assertEquals(2, cv.getPathList().size());
        assertEquals("vs_users/%s/lastname".formatted(expectedIndex), cv.getPath());
    }

    private void checkPatternOnUsersFirstname(ValidationViolation v, int expectedIndex) {
        assertTrue(v instanceof ConstraintViolation);
        ConstraintViolation cv = (ConstraintViolation) v;
        assertTrue(cv.getConstraint() instanceof PatternConstraint);
        assertEquals("   ", cv.getInvalidValue());
        assertEquals(2, cv.getPathList().size());
        assertEquals("vs_users/%s/firstname".formatted(expectedIndex), cv.getPath());
    }

}
