

const protocol =
    location.protocol === "https:" ? "wss:" : "ws:";

// El navegador envía automáticamente las cookies del
// mismo origen que correspondan a esta petición.
const socket = new WebSocket(
    `${protocol}//${location.host}/ws/private`
);

socket.addEventListener("open", () => {
    console.log("websocket open");
});

socket.addEventListener("message", event => {
    const payload = JSON.parse(event.data);
    switch (payload.type) {
        case "LogRecord": {
            addRenderLogs("logs", payload.data);
            break;
        }
        case "Balance": {
            addRenderLogs("futureBalance", payload.data.future);
            addRenderLogs("spotBalance", payload.data.spot);
            break;
        }
        default: {
            console.error("Unknow Response: " + payload);
        }
    }
});

socket.addEventListener("close", (close) => {
    console.log("Websocket close: " + close);

});

socket.addEventListener("error", (error) => {
    console.log("Websocket error: " + error);

});

/*sendButton.addEventListener("click", () => {
    if (socket.readyState !== WebSocket.OPEN) {
        output.textContent += "El WebSocket no está conectado.\n";
        return;
    }

    socket.send(input.value);
    input.value = "";
});*/