package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.HomeService;
import com.joutio.bff.infrastructure.in.api.response.HomeView;
import com.joutio.microservice.security.SecurityHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/home} — accueil : sujets suivis + sujets les plus joués.
 */
@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<HomeView>> home() {
        return homeService.home(SecurityHelper.getUserId()).thenApply(ResponseEntity::ok);
    }
}
