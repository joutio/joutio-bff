package com.joutio.bff.infrastructure.out.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joutio.bff.application.ExpoPushGateway.Status;
import com.joutio.bff.application.ExpoPushStore.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;

class ExpoPushSenderTest {
    private final HttpClient client=mock(HttpClient.class);
    private final ExpoPushSender sender=new ExpoPushSender(new ObjectMapper().findAndRegisterModules(),"secret",client,URI.create("https://example.test/"));
    private final Delivery d=new Delivery("d","i","ExpoPushToken[abc]",new Notice("n","u","WIZZ","room",null,Instant.now().plusSeconds(10)),null,1);
    @SuppressWarnings("unchecked")
    private void response(int code,String body) throws Exception {
        HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(code); when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }
    @Test void ticket_is_accepted_and_authorization_is_server_side() throws Exception {
        response(200,"{\"data\":{\"status\":\"ok\",\"id\":\"ticket\"}}");
        var result=sender.send(d); assertThat(result.status()).isEqualTo(Status.ACCEPTED); assertThat(result.ticket()).isEqualTo("ticket");
        var request=ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(),any());
        assertThat(request.getValue().headers().firstValue("Authorization")).contains("Bearer secret");
        assertThat(request.getValue().uri().toString()).isEqualTo("https://example.test/send");
    }
    @Test void invalid_token_receipt_ignores_unknown_message_field() throws Exception {
        response(200,"{\"data\":{\"ticket\":{\"status\":\"error\",\"message\":\"unregistered\",\"details\":{\"error\":\"DeviceNotRegistered\"}}}}");
        assertThat(sender.receipt("ticket").status()).isEqualTo(Status.INVALID_TOKEN);
    }
    @Test void missing_receipt_is_retried() throws Exception {
        response(200,"{\"data\":{}}"); assertThat(sender.receipt("ticket").status()).isEqualTo(Status.RETRY);
    }
    @Test void rate_limit_and_server_errors_retry_but_bad_credentials_do_not() throws Exception {
        response(429,""); assertThat(sender.send(d).status()).isEqualTo(Status.RETRY);
        response(503,""); assertThat(sender.send(d).status()).isEqualTo(Status.RETRY);
        response(401,""); assertThat(sender.send(d).status()).isEqualTo(Status.REJECTED);
    }
    @Test void payload_expiry_is_iso_and_ttl_respects_short_rematches() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules();
        var payload=new ExpoPushSender.Message(d.token(),"Joutio","Revanche","default","high","challenges",9,ExpoPushSender.PushData.of(d.notice()));
        var tree=mapper.readTree(mapper.writeValueAsString(payload));
        assertThat(tree.at("/data/expiresAt").asText()).isEqualTo(d.notice().expiresAt().toString());
        assertThat(tree.get("ttl").asInt()).isLessThan(10);
    }
}
