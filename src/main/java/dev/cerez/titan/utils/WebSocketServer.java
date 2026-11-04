package dev.cerez.titan.utils;

public interface WebSocketServer {

    void sendMessage(Object message);

    @SuppressWarnings("UnusedReturnValue")
    Revocable onClose(Runnable callback);

    static WebSocketServer empty(){
        return new WebSocketServer() {
            @Override
            public void sendMessage(Object message) {}
            @Override
            public Revocable onClose(Runnable callback) {
                return () -> {};
            }
        };
    }
}
