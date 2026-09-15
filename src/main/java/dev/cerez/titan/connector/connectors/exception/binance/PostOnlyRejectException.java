package dev.cerez.titan.connector.connectors.exception.binance;

import dev.cerez.titan.connector.connectors.exception.BinanceApiException;

import java.net.http.HttpRequest;

public class PostOnlyRejectException extends BinanceApiException {
    public PostOnlyRejectException(int code, String message, HttpRequest request  ) {
        super(code, message, request);
    }
}
