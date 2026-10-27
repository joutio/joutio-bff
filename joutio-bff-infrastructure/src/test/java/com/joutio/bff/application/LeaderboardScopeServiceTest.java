package com.joutio.bff.application;

import com.joutio.profile.domain.model.Profile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeaderboardScopeServiceTest {

    private final FollowLookup followLookup = mock(FollowLookup.class);
    private final ProfileLookup profileLookup = mock(ProfileLookup.class);
    private final LeaderboardScopeService service = new LeaderboardScopeService(followLookup, profileLookup);

    @Test
    void world_hasNoFilter() {
        LeaderboardScopeService.ScopeFilter filter = service.resolve("WORLD", "me").join();

        assertThat(filter.memberIds()).isNull();
        assertThat(filter.country()).isNull();
    }

    @Test
    void following_includesTheRequester() {
        when(followLookup.followingIds("me", FollowLookup.MAX_LIST_SIZE))
                .thenReturn(CompletableFuture.completedFuture(List.of("friend-1")));

        LeaderboardScopeService.ScopeFilter filter = service.resolve("FOLLOWING", "me").join();

        assertThat(filter.memberIds()).containsExactly("friend-1", "me");
        assertThat(filter.country()).isNull();
    }

    @Test
    void country_filtersOnRequesterCountry() {
        when(profileLookup.get("me")).thenReturn(CompletableFuture.completedFuture(
                Profile.builder().userId("me").country("DE").build()));

        LeaderboardScopeService.ScopeFilter filter = service.resolve("COUNTRY", "me").join();

        assertThat(filter.memberIds()).isNull();
        assertThat(filter.country()).isEqualTo("DE");
    }

    @Test
    void country_withoutCountry_fallsBackToWorld() {
        when(profileLookup.get("me")).thenReturn(CompletableFuture.completedFuture(
                Profile.builder().userId("me").build()));

        LeaderboardScopeService.ScopeFilter filter = service.resolve("COUNTRY", "me").join();

        assertThat(filter.memberIds()).isNull();
        assertThat(filter.country()).isNull();
    }
}
