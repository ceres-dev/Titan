package dev.cerez.titan.connector.connectors.exception.binance;

import dev.cerez.titan.connector.connectors.exception.BinanceApiException;

import java.net.http.HttpRequest;

public class SystemNotEnoughAssetException extends BinanceApiException {
    public SystemNotEnoughAssetException(int code, String message, HttpRequest request) {
        super(code, message, request);
    }
}
