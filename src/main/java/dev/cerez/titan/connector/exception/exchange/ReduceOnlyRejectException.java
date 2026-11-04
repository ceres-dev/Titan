package dev.cerez.titan.connector.exception.exchange;

import java.net.http.HttpRequest;

public class ReduceOnlyRejectException extends ExchangeApiException {
    public ReduceOnlyRejectException(int code, String message, HttpRequest request) {
        super(code, message, request);
    }
}
