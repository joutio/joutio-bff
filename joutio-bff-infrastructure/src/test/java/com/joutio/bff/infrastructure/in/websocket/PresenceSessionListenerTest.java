package com.joutio.bff.infrastructure.in.websocket;

import com.joutio.profile.domain.command.PresenceCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresenceSessionListenerTest {

    private final CommandGateway commandGateway = mock(CommandGateway.class);

    PresenceSessionListenerTest() {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void renewWithoutLocalSessionsSendsNothing() {
        PresenceSessionListener listener = listener();

        listener.renewSessions();

        verify(commandGateway, never()).send(any());
        listener.shutdown();
    }

    @Test
    void renewSendsOneBatchCommandWithAllLocalSessions() {
        PresenceSessionListener listener = listener();
        listener.registerSession("s1", "u1");
        listener.registerSession("s2", "u2");
        listener.registerSession("s3", "u1");

        listener.renewSessions();

        ArgumentCaptor<Object> command = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway).send(command.capture());
        assertThat(command.getValue()).isInstanceOf(PresenceCommand.RenewPresenceSessionsCommand.class);
        PresenceCommand.RenewPresenceSessionsCommand renew =
                (PresenceCommand.RenewPresenceSessionsCommand) command.getValue();
        assertThat(renew.sessionIds()).containsExactlyInAnyOrder("s1", "s2", "s3");
        listener.shutdown();
    }

    @Test
    void shutdownDisconnectsLocalSessions() {
        PresenceSessionListener listener = listener();
        listener.registerSession("s1", "u1");
        listener.registerSession("s2", "u2");

        listener.shutdown();

        ArgumentCaptor<Object> commands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(2)).send(commands.capture());
        assertThat(commands.getAllValues())
                .allSatisfy(command -> assertThat(command)
                        .isInstanceOf(PresenceCommand.DisconnectPlayerCommand.class));
    }

    private PresenceSessionListener listener() {
        return new PresenceSessionListener(commandGateway, "joutio-bff", "host");
    }
}
