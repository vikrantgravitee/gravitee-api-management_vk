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
package io.gravitee.apim.core.api.domain_service.validation;

import assertions.CoreAssertions;
import inmemory.TagQueryServiceInMemory;
import io.gravitee.rest.api.service.exceptions.TagNotAllowedException;
import java.util.Arrays;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TagValidationServiceTest {

    private static final String ORGANIZATION_ID = "organization-id";
    private static final String USER_ID = "user-id";

    private final TagQueryServiceInMemory tagQueryService = new TagQueryServiceInMemory();

    private TagValidationService service;

    @BeforeEach
    public void setUp() {
        service = new TagValidationService(tagQueryService);
    }

    @AfterEach
    void tearDown() {
        tagQueryService.reset();
    }

    @Test
    void should_return_new_tags_when_no_change() {
        var oldTags = Set.of("public");
        var newTags = Set.of("public");

        var result = service.validateAndSanitize(oldTags, newTags, USER_ID, ORGANIZATION_ID);

        CoreAssertions.assertThat(result).containsExactlyElementsOf(newTags);
    }

    @Test
    void should_return_new_tags_when_new_tags_are_allowed() {
        givenAllowedTags("public", "private");

        var oldTags = Set.of("public");
        var newTags = Set.of("private");

        var result = service.validateAndSanitize(oldTags, newTags, USER_ID, ORGANIZATION_ID);

        CoreAssertions.assertThat(result).containsExactlyElementsOf(newTags);
    }

    @Test
    void should_throw_when_removal_of_not_allowed_tag() {
        givenAllowedTags("private");

        var oldTags = Set.of("public");
        var newTags = Set.of("private");

        var throwable = Assertions.catchThrowable(() -> service.validateAndSanitize(oldTags, newTags, USER_ID, ORGANIZATION_ID));

        Assertions.assertThat(throwable).isInstanceOf(TagNotAllowedException.class).hasMessageContaining("public");
    }

    @Test
    void should_throw_when_adding_a_not_allowed_tag() {
        givenAllowedTags("public");

        var oldTags = Set.of("public");
        var newTags = Set.of("private");

        var throwable = Assertions.catchThrowable(() -> service.validateAndSanitize(oldTags, newTags, USER_ID, ORGANIZATION_ID));

        Assertions.assertThat(throwable).isInstanceOf(TagNotAllowedException.class).hasMessageContaining("private");
    }

    private void givenAllowedTags(String... tags) {
        tagQueryService.initWith(Arrays.asList(tags));
    }
}
