package com.joutio.bff.infrastructure.in.share;

import com.joutio.bff.application.SharePageService.Kind;
import com.joutio.bff.application.SharePageService.SharePage;
import com.joutio.bff.application.ShareProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SharePageRendererTest {

    private final ShareProperties properties = new ShareProperties(
            "https://app.joutio.com", "https://apps.apple.com/app/id1", "com.joutio.app",
            List.of("TEAM.com.joutio.app"), List.of("AB:CD"), "https://joutio.com/og.png");

    @Test
    void render_escapesContentAndBuildsLinks() {
        SharePage page = new SharePage(Kind.PROFILE, "<b>Sofia</b> te défie", "\"bio\"", "https://media/x.jpg", "/players/u1");
        String html = new SharePageRenderer(properties).render(page, "/p/u1");

        assertThat(html).contains("<meta property=\"og:title\" content=\"&lt;b&gt;Sofia&lt;/b&gt; te défie\">");
        assertThat(html).doesNotContain("<b>Sofia</b>");
        assertThat(html).contains("https://app.joutio.com/players/u1");
        assertThat(html).contains("intent://go.joutio.com/p/u1#Intent;scheme=https;package=com.joutio.app");
    }
}
