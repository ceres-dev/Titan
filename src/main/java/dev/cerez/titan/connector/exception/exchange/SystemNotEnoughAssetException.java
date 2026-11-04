package dev.cerez.titan.connector.exception.exchange;

import java.net.http.HttpRequest;

public class SystemNotEnoughAssetException extends ExchangeApiException {
    public SystemNotEnoughAssetException(int code, String message, HttpRequest request) {
        super(code, message, request);
    }
}
