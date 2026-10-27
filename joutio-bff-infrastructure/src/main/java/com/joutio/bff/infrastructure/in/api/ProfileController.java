package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.FollowLookup;
import com.joutio.bff.application.ProfileUpdateService;
import com.joutio.bff.application.ProfileViewService;
import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.request.PageParams;
import com.joutio.bff.infrastructure.in.api.request.PeopleSort;
import com.joutio.bff.infrastructure.in.api.request.UpdateAvatarOptionsRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateBannerRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateBioRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateCountryRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateLanguageRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdatePseudonymRequest;
import com.joutio.bff.infrastructure.in.api.request.UpdateTitleRequest;
import com.joutio.bff.infrastructure.in.api.response.ActivityViewResponse;
import com.joutio.bff.infrastructure.in.api.response.GameHistoryItemView;
import com.joutio.bff.infrastructure.in.api.response.HeadToHeadView;
import com.joutio.bff.infrastructure.in.api.response.BadgeCollectionView;
import com.joutio.bff.infrastructure.in.api.response.PageResponse;
import com.joutio.bff.infrastructure.in.api.response.PlayerCardView;
import com.joutio.bff.infrastructure.in.api.response.PlayerProfileView;
import com.joutio.bff.infrastructure.in.api.response.PlayerRatingView;
import com.joutio.bff.infrastructure.in.api.response.TitlesView;
import com.joutio.bff.infrastructure.in.api.response.TopicStatView;
import com.joutio.game.domain.model.GameHistoryOutcome;
import com.joutio.game.domain.model.GameHistorySort;
import com.joutio.game.domain.model.GamePlayerType;
import com.joutio.microservice.security.SecurityHelper;
import com.joutio.social.domain.command.UserFollowerCommand;
import com.joutio.social.domain.model.FollowDirection;
import com.joutio.social.domain.model.FollowerIds;
import jakarta.validation.Valid;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /api/profiles} — fiche joueur, listes de personnes, historique, suivi, activité.
 */
@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProfileViewService profileViewService;
    private final ProfileUpdateService profileUpdateService;
    private final FollowLookup followLookup;
    private final CommandGateway commandGateway;

    public ProfileController(ProfileViewService profileViewService,
                             ProfileUpdateService profileUpdateService,
                             FollowLookup followLookup,
                             CommandGateway commandGateway) {
        this.profileViewService = profileViewService;
        this.profileUpdateService = profileUpdateService;
        this.followLookup = followLookup;
        this.commandGateway = commandGateway;
    }

    @GetMapping("/ratings")
    public CompletableFuture<ResponseEntity<List<PlayerRatingView>>> ratings(
            @RequestParam List<String> ids) {
        return profileViewService.ratings(ids).thenApply(ResponseEntity::ok);
    }

    @GetMapping("/{userId}")
    public CompletableFuture<ResponseEntity<PlayerProfileView>> profile(@PathVariable String userId) {
        return profileViewService
                .profileView(SecurityHelper.getUserId(), userId)
                .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/{userId}/pseudonym")
    public CompletableFuture<ResponseEntity<Void>> updatePseudonym(
            @PathVariable String userId,
            @Valid @RequestBody UpdatePseudonymRequest request) {
        return profileUpdateService
                .updatePseudonym(userId, SecurityHelper.getUserId(), request.pseudonym())
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{userId}/bio")
    public CompletableFuture<ResponseEntity<Void>> updateBio(
            @PathVariable String userId,
            @Valid @RequestBody UpdateBioRequest request) {
        return profileUpdateService
                .updateBio(userId, SecurityHelper.getUserId(), request.bio())
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{userId}/country")
    public CompletableFuture<ResponseEntity<Void>> updateCountry(
            @PathVariable String userId,
            @Valid @RequestBody UpdateCountryRequest request) {
        return profileUpdateService
                .updateCountry(userId, SecurityHelper.getUserId(), request.country())
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{userId}/avatar-options")
    public CompletableFuture<ResponseEntity<Void>> updateAvatarOptions(
            @PathVariable String userId,
            @Valid @RequestBody UpdateAvatarOptionsRequest request) {
        return profileUpdateService
                .updateAvatarOptions(userId, SecurityHelper.getUserId(), request.avatarOptions())
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{userId}/banner")
    public CompletableFuture<ResponseEntity<Void>> updateBanner(
            @PathVariable String userId,
            @Valid @RequestBody UpdateBannerRequest request) {
        return profileUpdateService
                .updateBanner(userId, SecurityHelper.getUserId(), request.bannerUrl())
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{userId}/language")
    public CompletableFuture<ResponseEntity<Void>> updateLanguage(
            @PathVariable String userId,
            @Valid @RequestBody UpdateLanguageRequest request) {
        return profileUpdateService
                .updateLanguage(userId, SecurityHelper.getUserId(), request.language())
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    /**
     * Collection de badges d'un joueur : tout le catalogue (paliers verrouillés compris) avec la
     * progression de chaque compteur.
     */
    @GetMapping("/{userId}/badges")
    public CompletableFuture<ResponseEntity<BadgeCollectionView>> badges(@PathVariable String userId) {
        return profileViewService.badges(userId).thenApply(ResponseEntity::ok);
    }

    /**
     * Activité journalière d'un joueur (calendrier de série) : 365 jours par défaut, fenêtre
     * explicite possible. La série courante est recalculée à la date de la requête.
     */
    @GetMapping("/{userId}/activity")
    public CompletableFuture<ResponseEntity<ActivityViewResponse>> activity(
            @PathVariable String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(364) : from;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) > 366) {
            throw new BffProblems.InvalidActivityRangeProblem();
        }
        return profileViewService.activity(userId, start, end).thenApply(ResponseEntity::ok);
    }

    /**
     * Titres thématiques d'un joueur : le **titre porté** et les titres débloqués (un par sujet
     * où il a atteint au moins le premier palier). Le libellé est composé par le client (palier
     * traduit + nom du sujet dans sa langue).
     */
    @GetMapping("/{userId}/titles")
    public CompletableFuture<ResponseEntity<TitlesView>> titles(@PathVariable String userId) {
        return profileViewService.titles(userId).thenApply(ResponseEntity::ok);
    }

    /**
     * Titre thématique **porté** (affiché sur le profil). {@code null} efface le titre.
     * Propriétaire uniquement (invariant de l'agrégat) et titre **réellement débloqué**
     * (vérifié ici, avant l'envoi de la commande).
     */
    @PutMapping("/{userId}/title")
    public CompletableFuture<ResponseEntity<Void>> updateTitle(
            @PathVariable String userId,
            @Valid @RequestBody UpdateTitleRequest request) {
        return profileUpdateService
                .updateEquippedTitle(userId, SecurityHelper.getUserId(), request.titleKey())
                .thenApply(_ -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{userId}/following")
    public CompletableFuture<ResponseEntity<PageResponse<PlayerCardView>>> following(
            @PathVariable String userId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "RECENT") PeopleSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageParams.validate(page, size, MAX_PAGE_SIZE);
        return people(userId, FollowDirection.FOLLOWING, q, sort, page, size);
    }

    @GetMapping("/{userId}/followers")
    public CompletableFuture<ResponseEntity<PageResponse<PlayerCardView>>> followers(
            @PathVariable String userId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "RECENT") PeopleSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageParams.validate(page, size, MAX_PAGE_SIZE);
        return people(userId, FollowDirection.FOLLOWERS, q, sort, page, size);
    }

    @PutMapping("/{userId}/follow")
    public CompletableFuture<ResponseEntity<Void>> follow(@PathVariable String userId) {
        String viewerId = SecurityHelper.getUserId();
        return commandGateway
                .send(new UserFollowerCommand.FollowUserCommand(FollowerIds.user(viewerId, userId), viewerId, userId))
                .thenApply(_ -> {
                    // Compteurs en cache (30 s) : on purge les deux côtés après l'écriture.
                    followLookup.invalidateCounts(viewerId);
                    followLookup.invalidateCounts(userId);
                    return ResponseEntity.noContent().build();
                });
    }

    @DeleteMapping("/{userId}/follow")
    public CompletableFuture<ResponseEntity<Void>> unfollow(@PathVariable String userId) {
        String viewerId = SecurityHelper.getUserId();
        return commandGateway
                .send(new UserFollowerCommand.UnfollowUserCommand(FollowerIds.user(viewerId, userId), viewerId))
                .thenApply(_ -> {
                    followLookup.invalidateCounts(viewerId);
                    followLookup.invalidateCounts(userId);
                    return ResponseEntity.noContent().build();
                });
    }

    /**
     * Historique des parties d'un joueur : filtres optionnels (sujet, adversaire, type
     * d'adversaire, issue) et tri, tous appliqués **côté service game** — la pagination reste
     * exacte quelle que soit la combinaison.
     */
    @GetMapping("/{userId}/games")
    public CompletableFuture<ResponseEntity<PageResponse<GameHistoryItemView>>> games(
            @PathVariable String userId,
            @RequestParam(required = false) String topicId,
            @RequestParam(required = false) String opponentId,
            @RequestParam(required = false) GamePlayerType opponentType,
            @RequestParam(required = false) GameHistoryOutcome outcome,
            @RequestParam(defaultValue = "RECENT") GameHistorySort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageParams.validate(page, size, MAX_PAGE_SIZE);
        return profileViewService
                .games(userId, topicId, opponentId, opponentType, outcome, sort, page, size)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/{userId}/topics")
    public CompletableFuture<ResponseEntity<List<TopicStatView>>> topics(@PathVariable String userId) {
        return profileViewService.topics(userId).thenApply(ResponseEntity::ok);
    }

    @GetMapping("/{userId}/head-to-head")
    public CompletableFuture<ResponseEntity<HeadToHeadView>> headToHead(
            @PathVariable String userId,
            @RequestParam("against") String against) {
        return profileViewService.headToHead(userId, against).thenApply(ResponseEntity::ok);
    }

    private CompletableFuture<ResponseEntity<PageResponse<PlayerCardView>>> people(String userId,
                                                                                   FollowDirection direction,
                                                                                   String q,
                                                                                   PeopleSort sort,
                                                                                   int page,
                                                                                   int size) {
        return profileViewService
                .people(SecurityHelper.getUserId(), userId, direction, q, sort, page, size)
                .thenApply(ResponseEntity::ok);
    }
}
