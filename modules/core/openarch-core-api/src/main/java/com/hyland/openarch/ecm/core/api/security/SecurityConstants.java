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
package com.hyland.openarch.ecm.core.api.security;

import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.runtime.api.login.LoginService;

/**
 * @author Bogdan Stefanescu
 */
// TODO: make it a constant utility class instead of an interface.
public interface SecurityConstants {

    String SYSTEM_USERNAME = LoginService.SYSTEM_USERNAME;

    /**
     * Name of the user group for which security checks will be bypassed on the repository.
     * <p>
     * Setup in default user manager configuration.
     */
    String ADMINISTRATORS_GROUP = "administrators";

    /**
     * Name of the user group allowed to remove records if retention mode is not in compliance mode.
     *
     * @since 11.5
     */
    String RECORDS_CLEANER_GROUP = "RepositoryRecordCleaners";

    /**
     * Name of the user group allowed to edit directories in addition to administrators.
     */
    String POWER_USERS_GROUP = "powerusers";

    String EVERYONE = "__Everyone__";

    String EVERYTHING = "Everything";

    String CREATE_CHILD = "CreateChild";

    String CREATE_VERSION = "CreateVersion";

    String DELETE = "Delete";

    String DELETE_CHILD = "DeleteChild";

    String MANAGE_LOCK = "ManageLock";

    /**
     * Permission needed to manage the retention or legal hold of a record.
     *
     * @see CoreSession#setRecord
     * @see CoreSession#setRetainUntil
     * @see CoreSession#setLegalHold
     * @since 11.1
     */
    String MANAGE_RETENTION = "ManageRetention";

    String READ = "Read";

    String READ_VERSION = "ReadVersion";

    String READ_WRITE = "ReadWrite";

    String WRITE = "Write";

    String WRITE_VERSION = "WriteVersion";

    String MANAGE_SECURITY = "ManageSecurity";

    /**
     * Flag that can be used as principal to mark an unsupported ACL.
     */
    String UNSUPPORTED_ACL = "_UNSUPPORTED_ACL_";

    String GENERIC_DOCUMENT_SECURITY_ERROR = "Privilege '%s' is not granted to '%s' on document '%s'";

}
