package dev.cerez.titan.infrastructure.websocket;

import com.google.gson.Gson;
import dev.cerez.titan.TitanApplication;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.infrastructure.model.WebSocketContainer;
import dev.cerez.titan.infrastructure.user.custom.TitanUserDetails;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.utils.Revocable;
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
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class WebSocketHandler extends TextWebSocketHandler {

    private final TitanApplication titan;
    private volatile boolean isClosed = true;

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
        TitanUserDetails user = Utils.toTitanUser(session);
        if (user != null){
            isClosed = false;
            EnvironmentManager manager = titan.getEnvironmentManager(user.getId());
            WebSocketServer webSocketServer = new WebSocketMessenger(session);
            webSocketServer.onClose(() -> manager.getPort().unsubscribe(null));
            manager.setWebSocketServer(webSocketServer);
        }else {
            session.close(CloseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) throws Exception {
        TitanUserDetails user = Utils.toTitanUser(session);
        if (user != null){
            isClosed = true;
            EnvironmentManager manager = titan.getEnvironmentManager(user.getId());
            if (manager.getWebSocketServer() instanceof WebSocketMessenger webSocketServer) {
                webSocketServer.callsAfterClose.forEach(Runnable::run);
                webSocketServer.callsAfterClose.clear();
            }
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
    private class WebSocketMessenger implements WebSocketServer {

        private final Gson gson = new Gson();
        private final WebSocketSession session;
        private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();
        private final Executor executor = Executors.newSingleThreadExecutor(Utils.getThreadFactory(this));
        private final LinkedList<Runnable> callsAfterClose = new LinkedList<>();

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
                synchronized (session) {
                    try {
                        if (session.isOpen() && !isClosed) session.sendMessage(new TextMessage(payload));
                    } catch (IOException e) {
                        Log.exception(e);
                    }
                }
            });
        }

        @Override
        public @NotNull Revocable onClose(Runnable callback) {
            callsAfterClose.add(callback);
            return () -> callsAfterClose.remove(callback);
        }
    }
}