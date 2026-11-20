package com.joutio.bff.application;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@link UserBlockService} sans aucun blocage, pour les tests qui ne portent pas sur le blocage. */
public final class TestUserBlocks {

    private TestUserBlocks() {
    }

    public static UserBlockService none() {
        UserBlockService service = mock(UserBlockService.class);
        when(service.relations(any())).thenReturn(CompletableFuture.completedFuture(Set.of()));
        when(service.requireNotBlocked(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(service.hasBlocked(any(), any())).thenReturn(CompletableFuture.completedFuture(false));
        when(service.blockedPlayers(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        return service;
    }
}
