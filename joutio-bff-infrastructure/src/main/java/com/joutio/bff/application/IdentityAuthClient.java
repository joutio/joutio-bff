package com.joutio.bff.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Appels serveur-à-serveur vers la surface d'authentification identity, uniquement pour les
 * codes OTP de suppression de compte. Le navigateur ne voit jamais ces appels.
 */
@Service
public class IdentityAuthClient {

    private final RestClient client;

    public IdentityAuthClient(
            @Value("${joutio.identity.url:http://joutio-identity}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void requestAccountDeletionCode(String email) {
        post("/api/auth/web/request-account-deletion-code", Map.of("email", email));
    }

    public void verifyAccountDeletionCode(String email, String code) {
        try {
            post("/api/auth/web/verify-account-deletion-code",
                    Map.of("email", email, "code", code));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new AccountDeletionCodeRejected();
            }
            throw exception;
        }
    }

    private void post(String path, Map<String, String> body) {
        client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /** Code absent, expiré, déjà utilisé ou trop de tentatives. */
    public static class AccountDeletionCodeRejected extends RuntimeException {
        public AccountDeletionCodeRejected() {
            super("Invalid account deletion code");
        }
    }
}
