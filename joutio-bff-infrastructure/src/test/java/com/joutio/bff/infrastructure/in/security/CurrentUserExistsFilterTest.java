package com.joutio.bff.infrastructure.in.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joutio.bff.application.IdentityLookup;
import com.joutio.identity.domain.exception.UserProblems;
import com.joutio.identity.domain.model.AccountStatus;
import com.joutio.identity.domain.model.UserAccountStatus;
import com.joutio.microservice.MicroserviceProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserExistsFilterTest {

    @Mock
    private IdentityLookup identityLookup;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CurrentUserExistsFilter filter() {
        return new CurrentUserExistsFilter(identityLookup, objectMapper, corsProperties());
    }

    private static MicroserviceProperties corsProperties() {
        return new MicroserviceProperties(
                new MicroserviceProperties.Cors(
                        true, List.of("*"),
                        List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"),
                        List.of("*"), List.of(), true, 3600L),
                null, null, null, null, null);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passesThroughWhenUnauthenticated() throws Exception {
        MockHttpServletRequest request = request("/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void passesThroughForActiveUser() throws Exception {
        authenticate("user-1");
        when(identityLookup.status("user-1")).thenReturn(CompletableFuture.completedFuture(active("user-1")));
        MockHttpServletRequest request = request("/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void rejectsWith401WhenUserNoLongerExists() throws Exception {
        authenticate("ghost");
        when(identityLookup.status("ghost")).thenReturn(
                CompletableFuture.failedFuture(new UserProblems.UserNotFoundProblem("ghost")));
        MockHttpServletRequest request = request("/api/matchmaking/tickets");
        request.addHeader(HttpHeaders.ORIGIN, "https://app.joutio.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/problem+json"));
        assertTrue(response.getContentAsString().contains("urn:joutio:authentification:unknownUser"));
        assertEquals("https://app.joutio.com",
                response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("true", response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void rejectsWith401WhenUserIsDeleted() throws Exception {
        authenticate("deleted");
        when(identityLookup.status("deleted")).thenReturn(CompletableFuture.completedFuture(
                new UserAccountStatus("deleted", AccountStatus.DELETED, null)));
        MockHttpServletRequest request = request("/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("urn:joutio:authentification:unknownUser"));
        assertNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void rejectsWith403WhenUserIsSuspended() throws Exception {
        authenticate("suspended");
        Instant until = Instant.parse("2030-02-01T00:00:00Z");
        when(identityLookup.status("suspended")).thenReturn(CompletableFuture.completedFuture(
                new UserAccountStatus("suspended", AccountStatus.SUSPENDED, until)));
        MockHttpServletRequest request = request("/api/me");
        request.addHeader(HttpHeaders.ORIGIN, "https://app.joutio.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("urn:joutio:authorization:accountSuspended"));
        assertTrue(response.getContentAsString().contains(until.toString()));
        assertNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void passesThroughWhenSuspensionExpired() throws Exception {
        authenticate("user-1");
        when(identityLookup.status("user-1")).thenReturn(CompletableFuture.completedFuture(
                new UserAccountStatus("user-1", AccountStatus.SUSPENDED,
                        Instant.parse("2020-01-01T00:00:00Z"))));
        MockHttpServletRequest request = request("/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsWithoutCorsHeadersWhenNotACorsRequest() throws Exception {
        authenticate("ghost");
        when(identityLookup.status("ghost")).thenReturn(
                CompletableFuture.failedFuture(new UserProblems.UserNotFoundProblem("ghost")));
        MockHttpServletRequest request = request("/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertNull(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void failsOpenWhenIdentityLookupFails() throws Exception {
        authenticate("user-1");
        when(identityLookup.status("user-1"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("identity down")));
        MockHttpServletRequest request = request("/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void skipsNonApiPaths() throws Exception {
        authenticate("ghost");
        MockHttpServletRequest request = request("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    private static UserAccountStatus active(String userId) {
        return new UserAccountStatus(userId, AccountStatus.ACTIVE, null);
    }

    private static MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private static void authenticate(String userId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("user_id", userId)
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
