package dev.cerez.titan.infrastructure.websocket;

import com.google.gson.Gson;
import dev.cerez.titan.Titan;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.infrastructure.model.WebSocketContainer;
import dev.cerez.titan.infrastructure.user.custom.TitanUserDetails;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.utils.Messageable;
import dev.cerez.titan.utils.Utils;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.security.Principal;
import java.util.Collection;

@Component
public class PrivateWebSocketHandler extends TextWebSocketHandler {

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
        final Titan titan = Titan.getInstance();
        TitanUserDetails user = Utils.toTitanUser(session);
        if (user != null){
            EnvironmentManager manager = titan.getEnvironmentManager(user.getId());
            manager.setMessageable(new WebSocketMessenger(session));
            Log.info("Conectado como: " + user.getUsername());
        }else {
            Log.info("Connection desconocida");
            session.close(CloseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    protected void handleTextMessage(@NotNull WebSocketSession session, @NotNull TextMessage message) throws Exception {
        Principal user = session.getPrincipal();

        if (user == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        String received = message.getPayload();
        Log.info(received);
    }

    @RequiredArgsConstructor
    private static class WebSocketMessenger implements Messageable {

        private final Gson gson = new Gson();
        private final WebSocketSession session;

        @Override
        public void sendMessage(Object message) {
            String type;
            if (message instanceof Collection<?> collection) {
                if (collection.isEmpty()) {
                    return;
                }
                type = collection.iterator().next().getClass().getSimpleName();
            }else {
                type = message.getClass().getSimpleName();
            }
            WebSocketContainer webSocketContainer = new WebSocketContainer(type, message);
            String payload = gson.toJson(webSocketContainer);
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                Log.exception(e);
            }
        }
    }
}