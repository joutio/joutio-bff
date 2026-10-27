package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.PushSubscriptionService;
import com.joutio.bff.infrastructure.in.api.problem.BffProblemExceptionHandler;
import com.joutio.bff.infrastructure.out.push.WebPushProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PushControllerTest {

    private static final String USER_ID = "user-1";
    private static final String ENDPOINT = "https://push.example/endpoint-1";

    private final PushSubscriptionService subscriptionService = mock(PushSubscriptionService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TestSecurity.authenticate(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    private void setUpWith(WebPushProperties properties) {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PushController(subscriptionService, properties))
                .setControllerAdvice(new BffProblemExceptionHandler())
                .build();
    }

    @Test
    void vapid_public_key_exposed_when_configured() throws Exception {
        setUpWith(new WebPushProperties(new WebPushProperties.Vapid("public-key", "private-key", "mailto:x")));

        mockMvc.perform(get("/api/push/vapid-public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("public-key"));
    }

    @Test
    void vapid_public_key_returns_not_found_when_disabled() throws Exception {
        setUpWith(new WebPushProperties(null));

        mockMvc.perform(get("/api/push/vapid-public-key"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(subscriptionService);
    }

    @Test
    void subscribe_registers_subscription_for_authenticated_user() throws Exception {
        setUpWith(new WebPushProperties(new WebPushProperties.Vapid("public-key", "private-key", "mailto:x")));

        mockMvc.perform(put("/api/push/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Firefox")
                        .content("""
                                {"endpoint":"%s","keys":{"p256dh":"p256dh-value","auth":"auth-value"}}
                                """.formatted(ENDPOINT)))
                .andExpect(status().isNoContent());

        verify(subscriptionService).register(USER_ID, ENDPOINT, "p256dh-value", "auth-value", "Firefox");
    }

    @Test
    void unsubscribe_delegates_for_authenticated_user() throws Exception {
        setUpWith(new WebPushProperties(new WebPushProperties.Vapid("public-key", "private-key", "mailto:x")));

        mockMvc.perform(delete("/api/push/subscriptions").param("endpoint", ENDPOINT))
                .andExpect(status().isNoContent());

        verify(subscriptionService).unregister(USER_ID, ENDPOINT);
    }
}
