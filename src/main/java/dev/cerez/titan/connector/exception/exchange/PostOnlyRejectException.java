package dev.cerez.titan.connector.exception.exchange;

import java.net.http.HttpRequest;

public class PostOnlyRejectException extends ExchangeApiException {
    public PostOnlyRejectException(int code, String message, HttpRequest request  ) {
        super(code, message, request);
    }
}
