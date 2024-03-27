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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fixtures.core.model.MembershipFixtures;
import inmemory.MembershipQueryServiceInMemory;
import io.gravitee.apim.core.exception.TechnicalDomainException;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.TagRepository;
import io.gravitee.repository.management.model.Tag;
import io.gravitee.repository.management.model.TagReferenceType;
import io.gravitee.rest.api.idp.api.authentication.UserDetails;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

class TagQueryServiceImplTest {

    private static final String ORGANIZATION_ID = "organization-id";
    private static final String USER_ID = "user-id";

    TagRepository tagRepository;
    MembershipQueryServiceInMemory membershipQueryServiceInMemory = new MembershipQueryServiceInMemory();

    TagQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        tagRepository = mock(TagRepository.class);

        service = new TagQueryServiceImpl(tagRepository, membershipQueryServiceInMemory);
    }

    @AfterEach
    void tearDown() {
        membershipQueryServiceInMemory.reset();
        SecurityContextHolder.clearContext();
    }

    @Nested
    class FindByUser {

        @Test
        @SneakyThrows
        void should_return_all_tags_of_organization_when_user_is_environment_admin() {
            // Given
            givenAuthenticatedUserAsRole("ENVIRONMENT:ADMIN");
            when(tagRepository.findByReference(any(), eq(TagReferenceType.ORGANIZATION)))
                .thenAnswer(invocation -> {
                    String organizationId = invocation.getArgument(0);
                    return Set.of(
                        Tag.builder().id("tag1").referenceId(organizationId).build(),
                        Tag.builder().id("tag2").referenceId(organizationId).build()
                    );
                });

            // When
            var result = service.findByUser(USER_ID, ORGANIZATION_ID);

            // Then
            Assertions.assertThat(result).containsExactly("tag1", "tag2");
        }

        @Test
        @SneakyThrows
        void should_return_only_tags_without_restriction_or_tags_matching_groups_where_user_is_member() {
            // Given
            givenAuthenticatedUserAsRole("ENVIRONMENT:USER");
            membershipQueryServiceInMemory.initWith(List.of(MembershipFixtures.aGroupMembership("group1").withMemberId(USER_ID)));
            when(tagRepository.findByReference(any(), eq(TagReferenceType.ORGANIZATION)))
                .thenAnswer(invocation -> {
                    String organizationId = invocation.getArgument(0);
                    return Set.of(
                        Tag.builder().id("tag1").referenceId(organizationId).restrictedGroups(List.of("group1")).build(),
                        Tag.builder().id("tag2").referenceId(organizationId).restrictedGroups(List.of("group2")).build(),
                        Tag.builder().id("tag3").referenceId(organizationId).build()
                    );
                });

            // When
            var result = service.findByUser(USER_ID, ORGANIZATION_ID);

            // Then
            Assertions.assertThat(result).containsExactly("tag1", "tag3");
        }

        @Test
        void should_throw_when_technical_exception_occurs() throws TechnicalException {
            // Given
            when(tagRepository.findByReference(any(), any())).thenThrow(TechnicalException.class);

            // When
            Throwable throwable = catchThrowable(() -> service.findByUser(USER_ID, ORGANIZATION_ID));

            // Then
            assertThat(throwable)
                .isInstanceOf(TechnicalDomainException.class)
                .hasMessage("An error occurs while trying to find tags by user");
        }
    }

    private void givenAuthenticatedUserAsRole(String role) {
        SecurityContextHolder.setContext(
            new SecurityContext() {
                @Override
                public Authentication getAuthentication() {
                    UserDetails userDetails = new UserDetails("username", "", Collections.emptyList());
                    userDetails.setOrganizationId(ORGANIZATION_ID);

                    return UsernamePasswordAuthenticationToken.authenticated(userDetails, null, List.of((GrantedAuthority) () -> role));
                }

                @Override
                public void setAuthentication(Authentication authentication) {}
            }
        );
    }
}
