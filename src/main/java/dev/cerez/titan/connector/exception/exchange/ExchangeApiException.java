package dev.cerez.titan.connector.exception.exchange;

import dev.cerez.titan.connector.exception.ApiException;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.net.http.HttpRequest;

@EqualsAndHashCode(callSuper = true)
@Data
public abstract class ExchangeApiException extends ApiException {

    private final int code;

    public ExchangeApiException(int code, String message, HttpRequest request) {
        super(message, request);
        this.code = code;
    }
}
