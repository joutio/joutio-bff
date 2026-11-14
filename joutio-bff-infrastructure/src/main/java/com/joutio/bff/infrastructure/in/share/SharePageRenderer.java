package com.joutio.bff.infrastructure.in.share;

import com.joutio.bff.application.SharePageService.SharePage;
import com.joutio.bff.application.ShareProperties;
import org.springframework.web.util.HtmlUtils;

/**
 * Page HTML d'un lien {@code go.joutio.com} : méta Open Graph et Twitter, puis redirection selon l'appareil.
 *
 * <ul>
 *   <li>Android : {@code intent://} vers l'app, repli Play Store.</li>
 *   <li>iOS : l'app installée a déjà intercepté le lien (Universal Link) ; sinon App Store.</li>
 *   <li>Ordinateur : pas de redirection, boutons « Continuer sur le web » et stores.</li>
 * </ul>
 * Les robots des messageries ne suivent pas le script : ils lisent l'aperçu.
 */
final class SharePageRenderer {

    private final ShareProperties properties;

    SharePageRenderer(ShareProperties properties) {
        this.properties = properties;
    }

    String render(SharePage page, String linkPath) {
        String webUrl = properties.webUrl() + page.appPath();
        String playUrl = properties.playStoreUrl();
        String intentUrl = "intent://go.joutio.com" + linkPath
                + "#Intent;scheme=https;package=" + properties.androidPackage()
                + ";S.browser_fallback_url=" + java.net.URLEncoder.encode(playUrl, java.nio.charset.StandardCharsets.UTF_8) + ";end";
        String title = escape(page.title());
        String description = escape(page.description());
        String image = escape(page.image());
        String url = escape("https://go.joutio.com" + linkPath);
        return """
                <!doctype html>
                <html lang="fr">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%1$s</title>
                <meta name="description" content="%2$s">
                <meta name="robots" content="noindex">
                <meta property="og:type" content="website">
                <meta property="og:site_name" content="Joutio">
                <meta property="og:title" content="%1$s">
                <meta property="og:description" content="%2$s">
                <meta property="og:image" content="%3$s">
                <meta property="og:url" content="%4$s">
                <meta name="twitter:card" content="summary_large_image">
                <meta name="twitter:title" content="%1$s">
                <meta name="twitter:description" content="%2$s">
                <meta name="twitter:image" content="%3$s">
                <style>
                :root{color-scheme:light dark;--bg:#0f0e0c;--surface:#1b1916;--text:#f5f1ea;--muted:#a59e93;--accent:#f2a93b;--on-accent:#1a1205}
                @media (prefers-color-scheme:light){:root{--bg:#faf7f2;--surface:#fff;--text:#1a1714;--muted:#6b645b}}
                body{margin:0;min-height:100dvh;display:grid;place-items:center;background:var(--bg);color:var(--text);font:16px/1.5 system-ui,-apple-system,sans-serif}
                main{width:min(420px,100%% - 32px);display:grid;gap:16px;text-align:center}
                img{width:100%%;aspect-ratio:1.91;object-fit:cover;border-radius:16px;background:var(--surface)}
                h1{margin:0;font-size:24px;line-height:1.25}p{margin:0;color:var(--muted)}
                a{display:block;padding:14px 16px;border-radius:10px;font-weight:600;text-decoration:none;color:var(--text);background:var(--surface)}
                a.primary{background:var(--accent);color:var(--on-accent)}
                </style>
                </head>
                <body>
                <main>
                <img src="%3$s" alt="">
                <h1>%1$s</h1>
                <p>%2$s</p>
                <a class="primary" id="store" href="%5$s">Télécharger Joutio</a>
                <a href="%6$s">Continuer sur le web</a>
                </main>
                <script>
                (function(){var ua=navigator.userAgent||"",s=document.getElementById("store");
                if(/android/i.test(ua)){s.href=%7$s;location.replace(%7$s);}
                else if(/iphone|ipad|ipod/i.test(ua)||(/macintosh/i.test(ua)&&"ontouchend" in document)){s.href=%8$s;location.replace(%8$s);}
                else{s.textContent="Télécharger l’app";}})();
                </script>
                </body>
                </html>
                """.formatted(title, description, image, url,
                escape(properties.appStoreUrl()),
                escape(webUrl),
                jsString(intentUrl),
                jsString(properties.appStoreUrl()));
    }

    /** Échappe les seuls caractères spéciaux HTML (les accents restent tels quels, la page est en UTF-8). */
    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private static String jsString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c") + "\"";
    }
}
