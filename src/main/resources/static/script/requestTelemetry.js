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
    const itemConnector = document.createElement("li");
    {
        const strong = document.createElement("strong");
        strong.textContent = "Requests: ";
        const span = document.createElement("span");
        span.textContent = `${telemetry.requestLastHour}r/h`;
        itemConnector.appendChild(strong)
        itemConnector.appendChild(span)
    }
    listConnector.appendChild(itemConnector);
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
    listSystem.appendChild(itemRam)
}
loadTelemetry();