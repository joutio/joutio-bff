package com.joutio.bff.application;

import com.joutio.social.domain.model.UserFollowCounts;
import com.joutio.social.domain.query.UserFollowerQuery;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Compteurs de follows : cache Caffeine amorti, invalidé après écriture avec une fenêtre
 * no-cache de 3 s (la projection social peut être en retard de quelques millisecondes).
 */
class FollowLookupTest {

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final FollowLookup lookup = new FollowLookup(queryGateway);

    private void stubCounts(UserFollowCounts... responses) {
        var ongoing = when(queryGateway.query(
                ArgumentMatchers.<UserFollowerQuery.GetUserFollowCountsQuery>any(),
                ArgumentMatchers.<ResponseType<UserFollowCounts>>any()));
        for (UserFollowCounts response : responses) {
            ongoing = ongoing.thenReturn(CompletableFuture.completedFuture(response));
        }
    }

    @Test
    void userCountsCachesResultAndAvoidsSecondQuery() {
        stubCounts(new UserFollowCounts(3, 5));

        UserFollowCounts first = lookup.userCounts("u1").join();
        UserFollowCounts second = lookup.userCounts("u1").join();

        assertThat(first).isEqualTo(new UserFollowCounts(3, 5));
        assertThat(second).isSameAs(first);
        verify(queryGateway, times(1)).query(
                ArgumentMatchers.<UserFollowerQuery.GetUserFollowCountsQuery>any(),
                ArgumentMatchers.<ResponseType<UserFollowCounts>>any());
    }

    @Test
    void invalidateCountsBypassesCacheDuringGraceWindow() {
        stubCounts(new UserFollowCounts(3, 5), new UserFollowCounts(4, 6));

        lookup.userCounts("u1").join();
        lookup.invalidateCounts("u1");
        UserFollowCounts after = lookup.userCounts("u1").join();

        assertThat(after).isEqualTo(new UserFollowCounts(4, 6));
        verify(queryGateway, times(2)).query(
                ArgumentMatchers.<UserFollowerQuery.GetUserFollowCountsQuery>any(),
                ArgumentMatchers.<ResponseType<UserFollowCounts>>any());
    }
}
