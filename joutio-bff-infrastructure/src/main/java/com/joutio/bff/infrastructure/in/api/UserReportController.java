package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.UserReportService;
import com.joutio.bff.infrastructure.in.api.request.ReportUserRequest;
import com.joutio.bff.infrastructure.in.api.response.UserReportAckView;
import com.joutio.microservice.security.SecurityHelper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/profiles/{userId}/reports} — dépôt d'un signalement de joueur.
 */
@RestController
@RequestMapping("/api/profiles/{userId}/reports")
public class UserReportController {

    private final UserReportService userReportService;

    public UserReportController(UserReportService userReportService) {
        this.userReportService = userReportService;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<UserReportAckView>> report(
            @PathVariable String userId,
            @Valid @RequestBody ReportUserRequest request) {
        return userReportService.report(userId, SecurityHelper.getUserId(), request)
                .thenApply(ack -> ResponseEntity
                        .status(ack.created() ? HttpStatus.CREATED : HttpStatus.OK)
                        .body(ack));
    }
}
