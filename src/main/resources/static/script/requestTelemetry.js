async function loadTelemetry() {
    try {
        const response = await fetch("/api/telemetry");
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const telemetry = await response.json();

        renderTelemetryConnector(telemetry.connector);
        renderTelemetrySystem(telemetry.system);
    } catch (error) {
        console.error("Error obteniendo el balance:", error);
    }
}
function renderTelemetryConnector(telemetry) {
    const listConnector = document.getElementById("telemetryConnector");

    listConnector.innerHTML = "";
    const requestsConnector = document.createElement("li");
    {
        const strong = document.createElement("strong");
        strong.textContent = "Requests: ";
        const span = document.createElement("span");
        span.textContent = `${utils.trunc(telemetry.requestLastHour/60, 2)}r/m`;
        requestsConnector.appendChild(strong)
        requestsConnector.appendChild(span)
    }
    const rateLimitConnector = document.createElement("li");
    {
        const strong = document.createElement("strong");
        strong.textContent = "RateLimit: ";
        const span = document.createElement("span");
        span.textContent = `${telemetry.useWeight}/${telemetry.maxWeight}`;
        rateLimitConnector.appendChild(strong)
        rateLimitConnector.appendChild(span)
    }
    const pingConnector = document.createElement("li");
    {
        const strong = document.createElement("strong");
        strong.textContent = "Ping: ";
        const span = document.createElement("span");
        span.textContent = `${telemetry.sPing}ms`;
        pingConnector.appendChild(strong)
        pingConnector.appendChild(span)
    }
    listConnector.appendChild(requestsConnector);
    listConnector.appendChild(rateLimitConnector);
    listConnector.appendChild(pingConnector);
}
function renderTelemetrySystem(telemetry) {
    const listSystem = document.getElementById("telemetrySystem");

    listSystem.innerHTML = "";
    const itemRam = document.createElement("li");
    {
        const strong = document.createElement("strong");
        strong.textContent = "RAM: ";
        const span = document.createElement("span");
        span.textContent = `${utils.formatBytes(telemetry.usedMemory)}/${utils.formatBytes(telemetry.totalMemory)}`;
        itemRam.appendChild(strong);
        itemRam.appendChild(span);
    }
    const itemUptime = document.createElement("li");
    {
        const strong = document.createElement("strong");
        strong.textContent = "Uptime: ";
        lastUptime = telemetry.uptime;
        lastUpdateTelemetry = Date.now();
        const span = document.createElement("span");
        span.textContent = `${utils.formatMiliSeconds(telemetry.uptime)}`;
        span.id = "uptime";
        itemUptime.appendChild(strong);
        itemUptime.appendChild(span);
    }
    listSystem.appendChild(itemRam)
    listSystem.appendChild(itemUptime)
}

let lastUptime = null;
let lastUpdateTelemetry = Date.now();
function updateUpdateTime(){
    let element = document.getElementById("uptime");
    let current = Date.now();
    if (element != null) {
        element.textContent = `${utils.formatMiliSeconds(lastUptime + (current - lastUpdateTelemetry))}`;
    }
}
loadTelemetry();
setInterval(updateUpdateTime, 1000);