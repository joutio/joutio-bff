package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.bff.infrastructure.in.api.response.UserRefView;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.profile.domain.model.Profile;
import com.joutio.social.domain.command.UserBlockCommand;
import com.joutio.social.domain.model.UserBlock;
import com.joutio.social.domain.query.UserBlockQuery;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Blocage entre joueurs (social). L'effet est symétrique : ni l'un ni l'autre ne peut défier, suivre, rejoindre le
 * salon de l'autre ni lui être apparié. Les gardes lisent les relations dans les deux sens et refusent par un
 * problème générique ({@code player:unavailable}).
 */
@Service
public class UserBlockService {

    private final QueryGateway queryGateway;
    private final CommandGateway commandGateway;
    private final ProfileLookup profileLookup;
    private final IdentityLookup identityLookup;
    private final FollowLookup followLookup;

    public UserBlockService(QueryGateway queryGateway,
                              CommandGateway commandGateway,
                              ProfileLookup profileLookup,
                              IdentityLookup identityLookup,
                              FollowLookup followLookup) {
        this.queryGateway = queryGateway;
        this.commandGateway = commandGateway;
        this.profileLookup = profileLookup;
        this.identityLookup = identityLookup;
        this.followLookup = followLookup;
    }

    /** Joueurs en relation de blocage avec {@code userId}, dans un sens ou dans l'autre. */
    public CompletableFuture<Set<String>> relations(String userId) {
        return queryGateway.query(new UserBlockQuery.GetBlockRelationsQuery(userId),
                        QueryResponseTypes.multipleInstancesOf(String.class))
                .thenApply(Set::copyOf);
    }

    /** Refuse l'action si l'un des deux joueurs a bloqué l'autre. */
    public CompletableFuture<Void> requireNotBlocked(String userId, String otherId) {
        return relations(userId).thenAccept(relations -> {
            if (relations.contains(otherId)) {
                throw new BffProblems.PlayerUnavailableProblem(otherId);
            }
        });
    }

    /** Vrai si {@code viewerId} a bloqué {@code userId} (« Débloquer » sur la fiche). */
    public CompletableFuture<Boolean> hasBlocked(String viewerId, String userId) {
        return blocks(viewerId).thenApply(blocks -> blocks.stream().anyMatch(block -> block.blockedId().equals(userId)));
    }

    public CompletableFuture<Void> block(String viewerId, String userId) {
        return commandGateway.send(new UserBlockCommand.BlockUserCommand(UserBlock.id(viewerId, userId), viewerId, userId))
                .thenAccept(_ -> invalidateCounts(viewerId, userId));
    }

    public CompletableFuture<Void> unblock(String viewerId, String userId) {
        return commandGateway.send(new UserBlockCommand.UnblockUserCommand(UserBlock.id(viewerId, userId), viewerId))
                .thenAccept(_ -> {});
    }

    /** Joueurs bloqués par {@code viewerId}, plus récents d'abord ; un profil disparu est omis. */
    public CompletableFuture<List<UserRefView>> blockedPlayers(String viewerId) {
        return blocks(viewerId).thenCompose(blocks -> {
            List<String> ids = blocks.stream().map(UserBlock::blockedId).toList();
            List<CompletableFuture<Profile>> profiles = ids.stream()
                    .map(id -> profileLookup.get(id).exceptionally(_ -> null))
                    .toList();
            CompletableFuture<Map<String, UserAccountStatus>> statuses = identityLookup.statuses(ids);
            return CompletableFuture.allOf(profiles.toArray(CompletableFuture[]::new))
                    .thenCombine(statuses, (_, statusById) -> {
                        Instant now = Instant.now();
                        return profiles.stream()
                                .map(CompletableFuture::join)
                                .filter(Objects::nonNull)
                                .map(profile -> new UserRefView(
                                        profile.userId(),
                                        profile.pseudonym(),
                                        profile.avatarOptions(),
                                        IdentityLookup.suspended(statusById.get(profile.userId()), now),
                                        IdentityLookup.premium(statusById.get(profile.userId()), now)))
                                .toList();
                    });
        });
    }

    private CompletableFuture<List<UserBlock>> blocks(String viewerId) {
        return queryGateway.query(new UserBlockQuery.GetBlockedUsersQuery(viewerId),
                QueryResponseTypes.multipleInstancesOf(UserBlock.class));
    }

    /** Le blocage retire les abonnements (social, asynchrone) : les compteurs en cache sont purgés des deux côtés. */
    private void invalidateCounts(String viewerId, String userId) {
        followLookup.invalidateCounts(viewerId);
        followLookup.invalidateCounts(userId);
    }
}
