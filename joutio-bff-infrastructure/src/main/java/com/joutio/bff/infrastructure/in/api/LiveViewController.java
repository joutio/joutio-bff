package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.live.LiveUpdate;
import com.joutio.bff.application.live.LiveViews;
import com.joutio.microservice.security.SecurityHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/live} — valeur courante d'une vue vivante versionnée, par le même chemin que son abonnement STOMP
 * {@code /topic/live/{view}/{key}} (même requête, même règle d'accès, aucun cache du BFF).
 */
@RestController
@RequestMapping("/api/live")
public class LiveViewController {

    private final LiveViews liveViews;

    public LiveViewController(LiveViews liveViews) {
        this.liveViews = liveViews;
    }

    @GetMapping("/{view}/{key}")
    public CompletableFuture<LiveUpdate<?>> read(@PathVariable String view, @PathVariable String key) {
        return liveViews.read(view, key, SecurityHelper.getUserId());
    }
}
