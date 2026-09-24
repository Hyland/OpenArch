/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.api.repository;

import com.hyland.openarch.ecm.core.api.CoreSession;

import java.util.Collection;
import java.util.List;

/**
 * High-level service to get to a {@link Repository Repository} and from there to
 * {@link CoreSession CoreSession} objects.
 */
public interface RepositoryManager {

    /** @since 11.5 */
    public static final String REPOSITORY_COMPONENT_NAME = "org.nuxeo.ecm.core.repository.RepositoryServiceComponent";

    /**
     * Gets all registered repositories.
     *
     * @return a read-only collection of repositories or an empty one if no repositories was defined
     */
    Collection<Repository> getRepositories();

    /**
     * Gets the names of registered repositories.
     *
     * @since 5.9.3
     * @return a list of repository names
     */
    List<String> getRepositoryNames();

    /**
     * Gets a repository by its name.
     *
     * @param name the repository name
     * @return the repository or null if not found
     */
    Repository getRepository(String name);

    /**
     * Registers a new repository.
     * <p>
     * Repository should be registered before the runtime start.
     *
     * @param repository the repository to register
     */
    void addRepository(Repository repository);

    /**
     * Gets the default repository.
     * <p>
     * If there is not a default repository returns the first registered. repository
     * <p>
     * This is a convenient method to get the repository for application having a single repository.
     *
     * @return the default repository
     */
    Repository getDefaultRepository();

    /**
     * Gets the name of the default repository.
     * <p>
     * If there is not a default repository, returns the name of the first registered repository.
     *
     * @return the default repository name
     */
    String getDefaultRepositoryName();

}
