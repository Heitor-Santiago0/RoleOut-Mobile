package br.com.roleout.service;

public interface PushGateway {
    record Result(String status, String ticket, String error) {}
    Result send(String token, int itinerary) throws Exception;
    Result receipt(String ticket) throws Exception;
}
