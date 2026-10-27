package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.TopicViewService;
import com.joutio.bff.infrastructure.in.api.response.TopicCategoryView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code /api/topic-categories} — catégories du catalogue (nom + libellé FR).
 */
@RestController
@RequestMapping("/api/topic-categories")
public class TopicCategoryController {

    private final TopicViewService topicViewService;

    public TopicCategoryController(TopicViewService topicViewService) {
        this.topicViewService = topicViewService;
    }

    @GetMapping
    public ResponseEntity<List<TopicCategoryView>> categories() {
        return ResponseEntity.ok(topicViewService.categories());
    }
}
