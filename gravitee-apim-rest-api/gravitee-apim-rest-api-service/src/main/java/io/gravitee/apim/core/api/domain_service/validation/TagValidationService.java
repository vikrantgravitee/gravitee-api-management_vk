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

import static java.util.stream.Collectors.toSet;

import io.gravitee.apim.core.DomainService;
import io.gravitee.apim.core.tag.query_service.TagQueryService;
import io.gravitee.rest.api.service.exceptions.TagNotAllowedException;
import java.util.HashSet;
import java.util.Set;

@DomainService
public class TagValidationService {

    private final TagQueryService tagQueryService;

    public TagValidationService(TagQueryService tagQueryService) {
        this.tagQueryService = tagQueryService;
    }

    /**
     * Validate API tags ensuring that the user has the right to use them.
     *
     * @param oldValue Existing tags before update
     * @param newValue New list of tags
     * @param userId The initiator of the action (create or update)
     * @param organizationId The organization id where the action is performed
     *
     * @return The list of tags to apply
     */
    public Set<String> validateAndSanitize(final Set<String> oldValue, final Set<String> newValue, String userId, String organizationId) {
        final Set<String> existingTags = oldValue == null ? new HashSet<>() : oldValue;
        final Set<String> tagsToUpdate = newValue == null ? new HashSet<>() : newValue;

        Set<String> tags;
        if (existingTags.isEmpty()) {
            tags = tagsToUpdate;
        } else {
            // Filter to keep only those newed or removed
            tags = existingTags.stream().filter(tag -> !tagsToUpdate.contains(tag)).collect(toSet());
            tags.addAll(tagsToUpdate.stream().filter(tag -> !existingTags.contains(tag)).collect(toSet()));
        }

        if (!tags.isEmpty()) {
            final Set<String> allowedTags = tagQueryService.findByUser(userId, organizationId);

            final String[] notAllowedTags = tags.stream().filter(tag -> !allowedTags.contains(tag)).toArray(String[]::new);
            if (notAllowedTags.length > 0) {
                throw new TagNotAllowedException(notAllowedTags);
            }
        }

        return newValue;
    }
}
