package com.joutio.bff.application;

import com.joutio.profile.domain.model.Profile;
import com.joutio.profile.domain.query.ProfileQuery;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfileLookupTest {

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final ProfileLookup lookup = new ProfileLookup(queryGateway);

    private static Profile profile(String userId) {
        return Profile.builder().userId(userId).pseudonym("P-" + userId).build();
    }

    @Test
    void getCachesResultAndAvoidsSecondQuery() {
        when(queryGateway.query(
                ArgumentMatchers.<ProfileQuery.GetProfileQuery>any(),
                ArgumentMatchers.<ResponseType<Profile>>any()))
                .thenReturn(CompletableFuture.completedFuture(profile("u1")));

        Profile first = lookup.get("u1").join();
        Profile second = lookup.get("u1").join();

        assertThat(first.pseudonym()).isEqualTo("P-u1");
        assertThat(second).isSameAs(first);
        verify(queryGateway, times(1)).query(
                ArgumentMatchers.<ProfileQuery.GetProfileQuery>any(),
                ArgumentMatchers.<ResponseType<Profile>>any());
    }

    @Test
    void invalidateClearsCachedEntryAndTriggersNewQuery() {
        when(queryGateway.query(
                ArgumentMatchers.<ProfileQuery.GetProfileQuery>any(),
                ArgumentMatchers.<ResponseType<Profile>>any()))
                .thenReturn(
                        CompletableFuture.completedFuture(profile("u1")),
                        CompletableFuture.completedFuture(profile("u1")));

        lookup.get("u1").join();
        lookup.invalidate("u1");
        lookup.get("u1").join();

        verify(queryGateway, times(2)).query(
                ArgumentMatchers.<ProfileQuery.GetProfileQuery>any(),
                ArgumentMatchers.<ResponseType<Profile>>any());
    }

    @Test
    void getDoesNotCacheWhileRefreshGraceIsActive() {
        when(queryGateway.query(
                ArgumentMatchers.<ProfileQuery.GetProfileQuery>any(),
                ArgumentMatchers.<ResponseType<Profile>>any()))
                .thenReturn(
                        CompletableFuture.completedFuture(profile("u1")),
                        CompletableFuture.completedFuture(profile("u1")));

        lookup.invalidate("u1");
        lookup.get("u1").join();
        lookup.get("u1").join();

        // Lecture post-écriture potentiellement périmée : re-query au lieu de figer le cache 30 s.
        verify(queryGateway, times(2)).query(
                ArgumentMatchers.<ProfileQuery.GetProfileQuery>any(),
                ArgumentMatchers.<ResponseType<Profile>>any());
    }

    @Test
    void getAllUsesOneBatchQueryForMissingIds() {
        when(queryGateway.query(
                ArgumentMatchers.<ProfileQuery.GetProfilesByIdsQuery>any(),
                ArgumentMatchers.<ResponseType<List<Profile>>>any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(profile("u1"), profile("u2"))));

        List<Profile> profiles = lookup.getAll(List.of("u1", "u2", "u1")).join();

        assertThat(profiles).extracting(Profile::userId).containsExactly("u1", "u2", "u1");
        verify(queryGateway, times(1)).query(
                ArgumentMatchers.<ProfileQuery.GetProfilesByIdsQuery>any(),
                ArgumentMatchers.<ResponseType<List<Profile>>>any());
    }

    @Test
    void getAllWithEmptyListReturnsEmptyWithoutQuery() {
        assertThat(lookup.getAll(List.of()).join()).isEmpty();
        verifyNoInteractions(queryGateway);
    }
}
