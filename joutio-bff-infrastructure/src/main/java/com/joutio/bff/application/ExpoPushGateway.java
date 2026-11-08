package com.joutio.bff.application;

public interface ExpoPushGateway {
    enum Status { ACCEPTED, DELIVERED, RETRY, INVALID_TOKEN, REJECTED }
    record Result(Status status, String ticket) {}
    Result send(ExpoPushStore.Delivery delivery);
    Result receipt(String ticket);
}
