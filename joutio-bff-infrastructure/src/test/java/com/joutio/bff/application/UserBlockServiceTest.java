package com.joutio.bff.application;

import com.joutio.bff.infrastructure.in.api.problem.BffProblems;
import com.joutio.social.domain.query.UserBlockQuery;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserBlockServiceTest {

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final UserBlockService service = new UserBlockService(queryGateway, mock(CommandGateway.class),
            mock(ProfileLookup.class), mock(IdentityLookup.class), mock(FollowLookup.class));

    @Test
    @SuppressWarnings("unchecked")
    void requireNotBlocked_refusesAPlayerInABlockRelation() {
        when(queryGateway.query(any(UserBlockQuery.GetBlockRelationsQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(List.of("other")));

        assertThatThrownBy(() -> service.requireNotBlocked("me", "other").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(BffProblems.PlayerUnavailableProblem.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void requireNotBlocked_letsOtherPlayersThrough() {
        when(queryGateway.query(any(UserBlockQuery.GetBlockRelationsQuery.class), any(ResponseType.class)))
                .thenReturn(CompletableFuture.completedFuture(List.of("someone-else")));

        assertThatCode(() -> service.requireNotBlocked("me", "other").join()).doesNotThrowAnyException();
    }
}
