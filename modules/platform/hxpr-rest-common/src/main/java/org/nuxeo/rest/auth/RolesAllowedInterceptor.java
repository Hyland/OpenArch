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
package org.nuxeo.rest.auth;

import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.ADMINISTRATORS_GROUP;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import com.hyland.openarch.ecm.core.api.NuxeoException;
import com.hyland.openarch.ecm.core.api.NuxeoPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Interceptor class to check if the current user has the roles allowed.
 */
@Aspect
@Component
public class RolesAllowedInterceptor {

    /**
     * Pointcut to match any method annotated with @RolesAllowed.
     */
    @Pointcut("@annotation(RolesAllowed)")
    public void callAtRolesAllowedMethod() {
    }

    /**
     * Pointcut to match any class annotated with @RolesAllowed.
     */
    @Pointcut("@within(RolesAllowed)")
    public void callAtRolesAllowedDeclaringType() {
    }

    @Pointcut("callAtRolesAllowedMethod() || callAtRolesAllowedDeclaringType()")
    public void callAtRolesAllowed() {
    }

    /**
     * Pointcut to match any Method annotated with @AdministratorAccess.
     */
    @Pointcut("@annotation(AdministratorAccess)")
    public void callAtAdministratorMethod() {
    }

    /**
     * Pointcut to match any class annotated with @AdministratorAccess.
     */
    @Pointcut("@within(AdministratorAccess)")
    public void callAtAdministratorDeclaringType() {
    }

    @Pointcut("callAtAdministratorMethod() || callAtAdministratorDeclaringType()")
    public void callAtAdministratorAccess() {
    }

    /**
     * This method is invoked before any method or class that is annotated with either @RolesAllowed
     * or @AdministratorAccess. It retrieves the roles allowed for the given join point and checks if the current user
     * has any of these roles. If the user does not have any of the roles, an AccessDeniedException is thrown.
     *
     * @param joinPoint The join point at which the method is invoked.
     * @throws AccessDeniedException If the current user does not have any of the roles allowed for the given join
     *             point.
     */
    @Before("callAtRolesAllowed() || callAtAdministratorAccess()")
    public void checkRolesAllowed(JoinPoint joinPoint) {
        List<String> rolesAllowed = getRolesAllowed(joinPoint);
        checkRolesAllowed(rolesAllowed);
    }

    /**
     * This method is used to get the roles allowed for a given join point. It first creates an empty list to store the
     * roles. Then it retrieves the method from the join point target and collects the roles from its annotations. It
     * also retrieves the annotations from the join point target class and collects the roles from them. Finally, it
     * returns a list of distinct roles. If the method from the join point target cannot be found, it throws a
     * NuxeoException with an INTERNAL_SERVER_ERROR status.
     *
     * @param joinPoint The join point from which the roles will be collected.
     * @return A list of distinct roles allowed for the given join point.
     * @throws NuxeoException If the method from the join point target cannot be found.
     */
    protected List<String> getRolesAllowed(JoinPoint joinPoint) {
        List<String> rolesAllowed = new ArrayList<>();
        // get the annotations from the method
        Optional<Method> method = Arrays.stream(joinPoint.getTarget().getClass().getMethods())
                                        .filter(mtd -> mtd.getName().equals(joinPoint.getSignature().getName()))
                                        .findFirst();
        Annotation[] annotations = method.isPresent() ? method.get().getAnnotations() : new Annotation[0];
        collectRolesAllowed(rolesAllowed, annotations);

        // get the annotations from the class
        Annotation[] classAnnotations = joinPoint.getTarget().getClass().getAnnotations();
        collectRolesAllowed(rolesAllowed, classAnnotations);

        return rolesAllowed.stream().distinct().toList();
    }

    /**
     * This method is used to collect roles from the provided annotations. It delegates the collection process to a
     * private overloaded method, providing an empty list as the third argument. This list is used to keep track of
     * already searched annotations to avoid infinite recursion.
     *
     * @param rolesAllowed The list where the roles will be collected.
     * @param annotations The annotations from which the roles will be collected.
     */
    protected void collectRolesAllowed(List<String> rolesAllowed, Annotation[] annotations) {
        collectRolesAllowed(rolesAllowed, annotations, new ArrayList<>());
    }

    /**
     * This method is used to collect roles from the provided annotations. It iterates over each annotation and
     * delegates the collection process to another private method. The third argument is a list used to keep track of
     * already searched annotations to avoid infinite recursion.
     *
     * @param rolesAllowed The list where the roles will be collected.
     * @param annotations The annotations from which the roles will be collected.
     * @param searched The list of already searched annotations.
     */
    private void collectRolesAllowed(List<String> rolesAllowed, Annotation[] annotations, List<String> searched) {
        Arrays.stream(annotations).forEach(annotation -> collectRolesAllowed(rolesAllowed, annotation, searched));
    }

    /**
     * This method is used to collect roles from a single annotation. If the annotation is an instance of RolesAllowed,
     * it adds its roles to the rolesAllowed list. If the annotation is not an instance of RolesAllowed and it has not
     * been searched before, it adds its class name to the searched list and recursively collects roles from its own
     * annotations.
     *
     * @param rolesAllowed The list where the roles will be collected.
     * @param annotation The annotation from which the roles will be collected.
     * @param searched The list of already searched annotations.
     */
    private void collectRolesAllowed(List<String> rolesAllowed, Annotation annotation, List<String> searched) {
        if (annotation instanceof RolesAllowed rolesAllowedAnno) {
            rolesAllowed.addAll(Arrays.asList(rolesAllowedAnno.value()));
        } else if (!searched.contains(annotation.getClass().getName())) {
            searched.add(annotation.getClass().getName());
            collectRolesAllowed(rolesAllowed, annotation.annotationType().getAnnotations(), searched);
        }
    }

    /**
     * This method checks if the current user has the roles specified in the RolesAllowed annotation. If the user does
     * not have any of the specified roles, a NuxeoException is thrown with a FORBIDDEN status.
     *
     * @param rolesAllowed The RolesAllowed annotation which contains the roles that are allowed.
     * @throws AccessDeniedException If the current user does not have any of the roles specified in the RolesAllowed
     *             annotation.
     */
    protected void checkRolesAllowed(List<String> rolesAllowed) {
        // Check if the current user has the roles allowed
        if (!hasRolesAllowed(getCurrentUser(), rolesAllowed)) {
            throw new AccessDeniedException("User does not have the required roles");
        }
    }

    /**
     * Retrieves the current user.
     *
     * @return the current user
     */
    protected NuxeoPrincipal getCurrentUser() {
        return NuxeoPrincipal.getCurrent();
    }

    /**
     * Checks if the current user has any of the roles allowed.
     *
     * @param currentUser the current user
     * @param rolesAllowed the roles allowed
     * @return true if the user has any of the roles allowed, false otherwise
     */
    protected boolean hasRolesAllowed(NuxeoPrincipal currentUser, List<String> rolesAllowed) {
        return rolesAllowed.stream()
                           .anyMatch(role -> ADMINISTRATORS_GROUP.equals(role) ? currentUser.isAdministrator()
                                   : currentUser.isMemberOf(role));
    }
}
