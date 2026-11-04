package dev.cerez.titan.connector.exception.exchange;

import java.net.http.HttpRequest;

public class UnknownOrderException extends ExchangeApiException {
    public UnknownOrderException(int code, String message, HttpRequest request) {
        super(code, message, request);
    }
}
