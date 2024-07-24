package io.gravitee.rest.api.model.settings.portalNext;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.gravitee.rest.api.model.annotations.ParameterKey;
import io.gravitee.rest.api.model.parameters.Key;
import io.gravitee.rest.api.model.settings.Enabled;
import lombok.Data;
import java.util.Collection;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Banner {
    @ParameterKey(Key.PORTAL_NEXT_BANNER_TITLE)
    private String title;

    @ParameterKey(Key.PORTAL_NEXT_BANNER_SUBTITLE)
    private String subtitle;

    @ParameterKey(Key.PORTAL_NEXT_BANNER_ENABLED)
    private Enabled banner;

    @ParameterKey(Key.PORTAL_NEXT_BANNER_BUTTON)
    private Collection<BannerButton> bannerButtons;
}
