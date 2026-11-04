package dev.cerez.titan.connector.exception.exchange;

import dev.cerez.titan.connector.exception.DefaultApiException;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.net.http.HttpRequest;

@EqualsAndHashCode(callSuper = true)
@Data
public class ExchangeDefaultApiException extends DefaultApiException {

    private final int code;

    public ExchangeDefaultApiException(int code, String message, HttpRequest request) {
        super(message, request);
        this.code = code;
    }
}
