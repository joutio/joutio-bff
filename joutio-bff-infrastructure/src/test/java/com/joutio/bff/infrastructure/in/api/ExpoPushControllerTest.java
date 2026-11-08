package com.joutio.bff.infrastructure.in.api;

import com.joutio.bff.application.ExpoPushService;
import com.joutio.bff.application.ExpoPushStore.Platform;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExpoPushControllerTest {
    private final ExpoPushService service=mock(ExpoPushService.class);
    private final MockMvc mvc=MockMvcBuilders.standaloneSetup(new ExpoPushController(service)).build();
    @BeforeEach void auth() { TestSecurity.authenticate("owner"); }
    @AfterEach void clear() { TestSecurity.clear(); }
    @Test void subscription_owner_is_always_the_jwt_actor() throws Exception {
        mvc.perform(put("/api/push/expo/subscriptions").contentType(MediaType.APPLICATION_JSON).content("""
            {"installationId":"12345678-1234-1234-1234-123456789012","token":"ExpoPushToken[abc]","platform":"ANDROID","rematches":false,"userId":"attacker"}
            """)).andExpect(status().isNoContent());
        verify(service).register("owner","12345678-1234-1234-1234-123456789012","ExpoPushToken[abc]",Platform.ANDROID,false);
    }
    @Test void invalid_token_is_rejected() throws Exception {
        mvc.perform(put("/api/push/expo/subscriptions").contentType(MediaType.APPLICATION_JSON).content("""
            {"installationId":"12345678-1234-1234-1234-123456789012","token":"https://attacker","platform":"ANDROID","rematches":true}
            """)).andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void deletion_is_scoped_to_current_owner() throws Exception {
        mvc.perform(delete("/api/push/expo/subscriptions").param("installationId","installation")).andExpect(status().isNoContent());
        verify(service).unregister("owner","installation");
    }
}
