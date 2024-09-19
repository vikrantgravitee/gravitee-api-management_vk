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
package io.gravitee.apim.core.scoring.model;

import java.time.ZonedDateTime;
import java.util.List;
import lombok.Builder;
import lombok.With;

@Builder(toBuilder = true)
public record ScoringReport(
    String id,
    @With String apiId,
    String environmentId,
    ZonedDateTime createdAt,
    Summary summary,
    List<Asset> assets
) {
    public record Summary(Long errors, Long warnings, Long infos, Long hints) {}

    public record Asset(String pageId, ScoringAssetType type, List<Diagnostic> diagnostics) {}

    public record Diagnostic(Severity severity, Range range, String rule, String message, String path) {}

    public record Range(Position start, Position end) {}

    public record Position(int line, int character) {}

    public enum Severity {
        ERROR,
        HINT,
        INFO,
        WARN,
    }
}
