package dev.cerez.titan.infrastructure.websocket;

import com.google.gson.Gson;
import dev.cerez.titan.TitanApplication;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.infrastructure.model.WebSocketContainer;
import dev.cerez.titan.infrastructure.user.custom.TitanUserDetails;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.utils.WebSocketServer;
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
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class WebSocketHandler extends TextWebSocketHandler {

    private final TitanApplication titan;

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
        TitanUserDetails user = Utils.toTitanUser(session);
        if (user != null){
            EnvironmentManager manager = titan.getEnvironmentManager(user.getId());
            manager.setWebSocketServer(new WebSocketMessenger(session));
        }else {
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
    private static class WebSocketMessenger implements WebSocketServer {

        private final Gson gson = new Gson();
        private final WebSocketSession session;
        private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();
        private final Executor executor = Executors.newSingleThreadExecutor(Utils.getThreadFactory(this));

        @Override
        public synchronized void sendMessage(Object message) {
            String type;
            if (message instanceof Collection<?> collection) {
                if (collection.isEmpty()) {
                    return;
                }
                type = collection.iterator().next().getClass().getSimpleName();
            }else {
                type = message.getClass().getSimpleName();
            }
            long sequence = sequences
                    .computeIfAbsent(type, key -> new AtomicLong())
                    .incrementAndGet();

            WebSocketContainer webSocketContainer = new WebSocketContainer(type, message, sequence);
            String payload = gson.toJson(webSocketContainer);
            executor.execute(() -> {
                try {
                    if (session.isOpen()) session.sendMessage(new TextMessage(payload));
                } catch (IOException e) {
                    Log.exception(e);
                }
            });
        }
    }
}