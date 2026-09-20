package dev.cerez.titan.connector.exception.exchange;

import dev.cerez.titan.connector.exception.BinanceApiException;

import java.net.http.HttpRequest;

public class MarginNotSufficienException extends BinanceApiException {
    public MarginNotSufficienException(int code, String message, HttpRequest request  ) {
        super(code, message, request);
    }
}
