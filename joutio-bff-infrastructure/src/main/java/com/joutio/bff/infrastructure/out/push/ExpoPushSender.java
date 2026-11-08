package com.joutio.bff.infrastructure.out.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joutio.bff.application.ExpoPushGateway;
import com.joutio.bff.application.ExpoPushStore.Delivery;
import com.joutio.bff.application.ExpoPushStore.Notice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Le token d'accès Expo reste exclusivement côté serveur. */
@Component
public class ExpoPushSender implements ExpoPushGateway {
    private static final Logger log = LoggerFactory.getLogger(ExpoPushSender.class);
    private final ObjectMapper mapper;
    private final String accessToken;
    private final HttpClient client;
    private final URI base;
    @org.springframework.beans.factory.annotation.Autowired
    public ExpoPushSender(ObjectMapper mapper, @Value("${joutio.expo-push.access-token:}") String accessToken) {
        this(mapper, accessToken, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                URI.create("https://exp.host/--/api/v2/push/"));
    }
    ExpoPushSender(ObjectMapper mapper, String accessToken, HttpClient client, URI base) {
        this.mapper=mapper; this.accessToken=accessToken; this.client=client; this.base=base;
    }
    record PushData(String notificationId, String userId, String type, String sourceId, String gameId, String expiresAt) {
        static PushData of(Notice n) { return new PushData(n.notificationId(), n.userId(), n.type(), n.sourceId(), n.gameId(), n.expiresAt().toString()); }
    }
    record Message(String to, String title, String body, String sound, String priority, String channelId,
                   long ttl, PushData data) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record Details(String error) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record Ticket(String status, String id, Details details) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record SendResponse(Ticket data) {}
    record ReceiptRequest(List<String> ids) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record ReceiptResponse(Map<String, Ticket> data) {}

    @Override public Result send(Delivery delivery) {
        Notice n = delivery.notice();
        long ttl = Duration.between(Instant.now(), n.expiresAt()).getSeconds();
        if (ttl <= 0) return new Result(Status.REJECTED, null);
        String body = n.type().equals("WIZZ") ? "Ton adversaire t'attend dans le salon."
                : n.gameId() == null ? "Tu as reçu un nouveau défi." : "Ton adversaire te propose une revanche.";
        try {
            HttpResponse<String> response = post("send", new Message(delivery.token(), "Joutio", body,
                    "default", "high", "challenges", Math.min(ttl, 3600), PushData.of(n)));
            if (response.statusCode() != 200) return httpFailure(response.statusCode());
            Ticket ticket = mapper.readValue(response.body(), SendResponse.class).data();
            if (ticket == null) return new Result(Status.RETRY, null);
            if ("ok".equals(ticket.status()) && ticket.id() != null) return new Result(Status.ACCEPTED, ticket.id());
            return failure(ticket);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new Result(Status.RETRY, null);
        } catch (Exception error) { return new Result(Status.RETRY, null); }
    }
    @Override public Result receipt(String id) {
        try {
            var response = post("getReceipts", new ReceiptRequest(List.of(id)));
            if (response.statusCode() != 200) return httpFailure(response.statusCode());
            var data = mapper.readValue(response.body(), ReceiptResponse.class).data();
            Ticket receipt = data == null ? null : data.get(id);
            if (receipt == null) return new Result(Status.RETRY, null);
            if ("ok".equals(receipt.status())) return new Result(Status.DELIVERED, null);
            return failure(receipt);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new Result(Status.RETRY, null);
        } catch (Exception error) { return new Result(Status.RETRY, null); }
    }
    private HttpResponse<String> post(String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json").header("Accept", "application/json");
        if (!accessToken.isBlank()) request.header("Authorization", "Bearer " + accessToken);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private static Result httpFailure(int code) {
        log.warn("Expo HTTP status {}", code);
        return new Result(code == 429 || code >= 500 ? Status.RETRY : Status.REJECTED, null);
    }
    private static Result failure(Ticket ticket) {
        String error = ticket.details() == null ? "unknown" : ticket.details().error();
        Status status = "DeviceNotRegistered".equals(error) ? Status.INVALID_TOKEN
                : "MessageRateExceeded".equals(error) ? Status.RETRY : Status.REJECTED;
        String reason = java.util.Set.of("DeviceNotRegistered", "MessageRateExceeded", "MessageTooBig", "InvalidCredentials", "MismatchSenderId")
                .contains(error == null ? "" : error) ? error : "unknown";
        log.warn("Expo push failure {} ({})", status, reason);
        return new Result(status, null);
    }
}
