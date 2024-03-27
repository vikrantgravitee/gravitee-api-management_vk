/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
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
package io.gravitee.apim.infra.query_service;

import static java.util.stream.Collectors.toSet;

import io.gravitee.apim.core.exception.TechnicalDomainException;
import io.gravitee.apim.core.membership.model.Membership;
import io.gravitee.apim.core.membership.query_service.MembershipQueryService;
import io.gravitee.apim.core.tag.query_service.TagQueryService;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.TagRepository;
import io.gravitee.repository.management.model.Tag;
import io.gravitee.repository.management.model.TagReferenceType;
import io.gravitee.rest.api.model.permissions.RoleScope;
import io.gravitee.rest.api.model.permissions.SystemRole;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class TagQueryServiceImpl implements TagQueryService {

    private final TagRepository tagRepository;
    private final MembershipQueryService membershipQueryService;

    public TagQueryServiceImpl(@Lazy TagRepository tagRepository, MembershipQueryService membershipQueryService) {
        this.tagRepository = tagRepository;
        this.membershipQueryService = membershipQueryService;
    }

    @Override
    public Set<String> findByUser(String userId, String organizationId) {
        try {
            var organizationTags = tagRepository.findByReference(organizationId, TagReferenceType.ORGANIZATION);

            if (isCurrentBelongEnvironmentAdmin()) {
                return organizationTags.stream().map(Tag::getId).collect(Collectors.toSet());
            }

            var restrictedTags = organizationTags
                .stream()
                .filter(t -> t.getRestrictedGroups() != null && !t.getRestrictedGroups().isEmpty())
                .map(Tag::getId)
                .collect(toSet());
            var groups = membershipQueryService.findGroupsThatUserBelongsTo(userId).stream().map(Membership::getReferenceId).toList();

            return organizationTags
                .stream()
                .filter(tag ->
                    !restrictedTags.contains(tag.getId()) ||
                    (tag.getRestrictedGroups() != null && anyMatch(tag.getRestrictedGroups(), groups))
                )
                .map(Tag::getId)
                .collect(Collectors.toSet());
        } catch (TechnicalException e) {
            throw new TechnicalDomainException("An error occurs while trying to find tags by user", e);
        }
    }

    private boolean anyMatch(final List<String> restrictedGroups, final Collection<String> groups) {
        for (final String restrictedGroup : restrictedGroups) {
            if (groups.contains(restrictedGroup)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCurrentBelongEnvironmentAdmin() {
        var role = RoleScope.ENVIRONMENT.name() + ':' + SystemRole.ADMIN.name();
        return SecurityContextHolder
            .getContext()
            .getAuthentication()
            .getAuthorities()
            .stream()
            .anyMatch(auth -> role.equals(auth.getAuthority()));
    }
}
