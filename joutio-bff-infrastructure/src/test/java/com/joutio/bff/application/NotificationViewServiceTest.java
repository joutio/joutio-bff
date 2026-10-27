package com.joutio.bff.application;

import com.joutio.notification.domain.command.NotificationCommand;
import com.joutio.notification.domain.exception.NotificationExceptions;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Les transitions de lecture ne doivent pas dépendre de la projection : un toast cliqué juste
 * après sa réception doit marquer la notification lue même si le read model est encore en retard.
 */
class NotificationViewServiceTest {

    private static final String USER = "user-1";
    private static final String NOTIFICATION = "notification-1";

    private final QueryGateway queryGateway = mock(QueryGateway.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final IdentityLookup identityLookup = mock(IdentityLookup.class);
    private final NotificationViewService service =
            new NotificationViewService(queryGateway, commandGateway, identityLookup);

    @Test
    void markRead_sendsCommandWithoutReadingTheProjection() {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.markRead(USER, NOTIFICATION).join();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway).send(captor.capture());
        assertThat(captor.getValue())
                .isInstanceOf(NotificationCommand.MarkNotificationReadCommand.class);
        verifyNoInteractions(queryGateway);
    }

    @Test
    void markRead_mapsMissingAggregateToNotFound() {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.failedFuture(
                new AggregateNotFoundException(NOTIFICATION, "Notification")));

        assertThatThrownBy(() -> service.markRead(USER, NOTIFICATION).join())
                .hasCauseInstanceOf(NotificationExceptions.NotificationNotFoundProblem.class);
    }

    @Test
    void markRead_keepsOwnerProblemForOtherPlayers() {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.failedFuture(
                new NotificationExceptions.NotNotificationOwnerProblem(NOTIFICATION, USER)));

        assertThatThrownBy(() -> service.markRead(USER, NOTIFICATION).join())
                .hasCauseInstanceOf(NotificationExceptions.NotNotificationOwnerProblem.class);
    }
}
