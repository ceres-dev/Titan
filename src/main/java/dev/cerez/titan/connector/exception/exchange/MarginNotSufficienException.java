package dev.cerez.titan.connector.exception.exchange;

import java.net.http.HttpRequest;

public class MarginNotSufficienException extends ExchangeApiException {
    public MarginNotSufficienException(int code, String message, HttpRequest request  ) {
        super(code, message, request);
    }
}
