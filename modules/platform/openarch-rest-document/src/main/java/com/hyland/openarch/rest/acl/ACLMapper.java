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
package com.hyland.openarch.rest.acl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hyland.openarch.ecm.core.api.security.ACE;
import com.hyland.openarch.ecm.core.api.security.ACL;
import org.springframework.stereotype.Component;

/**
 * Helper methods to translate {@link ACL} from/to {@link ACEDTO}.
 */
@Component
public class ACLMapper {

    private final ACEMapper aceMapper;

    public ACLMapper(ACEMapper aceMapper) {
        this.aceMapper = aceMapper;
    }

    public List<ACEDTO> acesToAceDTOs(List<ACE> aces) {
        return aceMapper.acesToAceDTOs(aces);
    }

    public ListEffectiveACLDTO fullEffectivePermissionsToDTO(String docId, Map<String, List<ACE>> fullListACL) {
        List<ListEffectiveACLPerUserDTO> aclPerUser = new ArrayList<>();
        fullListACL.forEach((userId, ACL) -> {
            List<ACEDTO> aceDTOs = acesToAceDTOs(ACL);
            aclPerUser.add(new ListEffectiveACLPerUserDTO(userId, aceDTOs));
        });
        return new ListEffectiveACLDTO(docId, aclPerUser);
    }
}
