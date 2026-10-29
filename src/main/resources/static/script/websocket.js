async function startWebSocket(whiteList = []){

    const protocol =
        location.protocol === "https:" ? "wss:" : "ws:";

// El navegador envía automáticamente las cookies del
// mismo origen que correspondan a esta petición.
    const socket = new WebSocket(
        `${protocol}//${location.host}/ws`
    );

    socket.addEventListener("open", () => {
        const indicator = document.getElementById("webSocketIndicator");
        indicator.classList.remove("webSocketClose");
        indicator.classList.add("webSocketOpen");
        console.log("websocket open");
    });

    const requestCount = new Map();

    socket.addEventListener("message", event => {
        const payload = JSON.parse(event.data);

        let lastCount = requestCount.get(payload.type)
        if (lastCount > payload.count) {
            return;
        }
        requestCount.set(payload.type, payload.count)

        if (whiteList.length !== 0) {
            if (whiteList.indexOf(payload.type) === -1) {
                return;
            }
        }
        switch (payload.type) {
            case "LogRecord": {
                addRenderLogs(payload.data);
                break;
            }
            case "Balance": {
                renderBalance("spotBalance", payload.data.spot);
                renderBalance("futureBalance", payload.data.future);
                renderBalance("futureValued", payload.data.futureValued);
                break;
            }
            case "Strategy": {
                renderStrategy("strategy", payload.data);
                break;
            }
            case "Telemetry": {
                renderTelemetryConnector(payload.data.connector);
                renderTelemetrySystem(payload.data.system);
                break
            }
            case "Position": {
                renderPosition(payload.data);
                break;
            }
            default: {
                console.error("Unknow Response: " + payload.data);
            }
        }
    });

    socket.addEventListener("close", (close) => {
        const indicator = document.getElementById("webSocketIndicator");
        indicator.classList.remove("webSocketOpen");
        indicator.classList.add("webSocketClose");
        console.log("Websocket close: " + close.reason + " Code=" + close.code);

    });

    socket.addEventListener("error", (error) => {
        console.log("Websocket error: " + error.type);

    });

    /*sendButton.addEventListener("click", () => {
        if (socket.readyState !== WebSocket.OPEN) {
            output.textContent += "El WebSocket no está conectado.\n";
            return;
        }

        socket.send(input.value);
        input.value = "";
    });*/
}