package io.gravitee.rest.api.model.settings.portalNext;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.gravitee.rest.api.model.annotations.ParameterKey;
import io.gravitee.rest.api.model.parameters.Key;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BannerButton {
    @ParameterKey(Key.PORTAL_NEXT_BANNER_BUTTON_TITLE)
    private String title;

    @ParameterKey(Key.PORTAL_NEXT_BANNER_BUTTON_ORDER)
    private int order;

    @ParameterKey(Key.PORTAL_NEXT_BANNER_BUTTON_TEXT)
    private String text;

    @ParameterKey(Key.PORTAL_NEXT_BANNER_BUTTON_LINK)
    private String link;
}
