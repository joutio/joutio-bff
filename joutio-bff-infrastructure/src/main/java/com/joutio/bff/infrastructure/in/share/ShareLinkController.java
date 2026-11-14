package com.joutio.bff.infrastructure.in.share;

import com.joutio.bff.application.SharePageService;
import com.joutio.bff.application.SharePageService.SharePage;
import com.joutio.bff.application.ShareProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Surface **publique** de {@code go.joutio.com} ({@code design/specs/screens/partage.md} § 2-3). L'ingress préfixe
 * les chemins par {@code /go} ; {@code /.well-known/*} est servi tel quel.
 *
 * <ul>
 *   <li>{@code /go/{p|t|j|i}/{id}} : page légère avec aperçu Open Graph, puis redirection selon l'appareil.</li>
 *   <li>{@code /.well-known/apple-app-site-association} et {@code assetlinks.json} : déclarations Universal Links et
 *       App Links (l'app installée ouvre ces liens sans passer par la page).</li>
 * </ul>
 */
@RestController
public class ShareLinkController {

    /** Identifiants acceptés dans un chemin : aucune balise ni guillemet ne peut atteindre la page. */
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final MediaType HTML = new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8);

    private final SharePageService pages;
    private final ShareProperties properties;
    private final SharePageRenderer renderer;

    public ShareLinkController(SharePageService pages, ShareProperties properties) {
        this.pages = pages;
        this.properties = properties;
        this.renderer = new SharePageRenderer(properties);
    }

    @GetMapping("/go/p/{userId}")
    public CompletableFuture<ResponseEntity<String>> profile(@PathVariable String userId) {
        return render(userId, "p", pages::profile);
    }

    @GetMapping("/go/t/{topicId}")
    public CompletableFuture<ResponseEntity<String>> topic(@PathVariable String topicId) {
        return render(topicId, "t", pages::topic);
    }

    @GetMapping("/go/j/{roomId}")
    public CompletableFuture<ResponseEntity<String>> room(@PathVariable String roomId) {
        return render(roomId, "j", pages::room);
    }

    @GetMapping(value = "/.well-known/apple-app-site-association", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> appleAppSiteAssociation() {
        List<Map<String, String>> components = List.of(
                Map.of("/", "/p/*"), Map.of("/", "/t/*"), Map.of("/", "/j/*"));
        Map<String, Object> details = Map.of("appIDs", properties.appleAppIds().stream().filter(id -> !id.isBlank()).toList(),
                "components", components);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .body(Map.of("applinks", Map.of("details", List.of(details))));
    }

    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> assetLinks() {
        Map<String, Object> target = Map.of(
                "namespace", "android_app",
                "package_name", properties.androidPackage(),
                "sha256_cert_fingerprints", properties.androidSha256().stream().filter(sha -> !sha.isBlank()).toList());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .body(List.of(Map.of("relation", List.of("delegate_permission/common.handle_all_urls"), "target", target)));
    }

    private CompletableFuture<ResponseEntity<String>> render(String id, String prefix,
                                                            java.util.function.Function<String, CompletableFuture<SharePage>> resolve) {
        if (!ID.matcher(id).matches()) {
            return CompletableFuture.completedFuture(ResponseEntity.notFound().build());
        }
        return resolve.apply(id).thenApply(page -> ResponseEntity.ok()
                .contentType(HTML)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(renderer.render(page, "/" + prefix + "/" + id)));
    }
}
