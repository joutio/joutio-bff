package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.SuggestionService;
import com.joutio.bff.infrastructure.in.api.response.SuggestionView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/suggestions} — palette ⌘K : sujets + joueurs.
 */
@RestController
@RequestMapping("/api/suggestions")
public class SuggestionController {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 20;

    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<List<SuggestionView>>> suggest(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "8") int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit doit être compris entre " + MIN_LIMIT + " et " + MAX_LIMIT);
        }
        return suggestionService.suggest(query, limit).thenApply(ResponseEntity::ok);
    }
}
