package com.joutio.bff.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joutio.identity.domain.model.User;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.microservice.core.infrastructure.in.api.response.SearchResponse;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchResponseTypeSerializationTest {

    private final JacksonSerializer serializer = JacksonSerializer.builder()
            .objectMapper(new ObjectMapper().findAndRegisterModules())
            .build();

    @Test
    void searchResponseType_survives_the_distributed_query_wire_format() {
        ResponseType<SearchResponse<User>> responseType =
                QueryResponseTypes.searchResponseOf(User.class);

        SerializedObject<byte[]> serialized = serializer.serialize(responseType, byte[].class);
        Object roundTrip = serializer.deserialize(new SimpleSerializedObject<>(
                serialized.getData(),
                byte[].class,
                serialized.getType().getName(),
                serialized.getType().getRevision()));

        assertThat(roundTrip).isInstanceOf(ResponseType.class);
        assertThat(((ResponseType<?>) roundTrip).getExpectedResponseType()).isEqualTo(User.class);
    }
}
