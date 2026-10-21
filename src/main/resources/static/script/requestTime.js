let remoteTime;
let exchangeTime;
let localTime;

let lastUpdate;

async function loadTime() {
    try {
        const response = await fetch("/api/time");

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const time = await response.json();

        remoteTime = new Date(time.local);
        exchangeTime = new Date(time.exchange);
        lastUpdate = localTime = Date.now();

        renderTimes();

    } catch (error) {
        console.error("Error obteniendo el tiempo:", error);
    }
}

function renderTimes() {
    const elapsedSeconds = Math.floor((Date.now() - lastUpdate));

    const local = new Date(localTime + elapsedSeconds);
    const remote = new Date(remoteTime.getTime() + elapsedSeconds);
    const exchange = new Date(exchangeTime.getTime() + elapsedSeconds);


    renderTime("timeLocal", local, "Local");
    renderTime("timeRemote", remote, "Remote");
    renderTime("timeExchange", exchange, "Exchange");
}

function renderTime(elementId, time, name) {
    const clock = document.getElementById(elementId);

    const pad = (num) => String(num).padStart(2, "0");

    const hours = pad(time.getHours());
    const minutes = pad(time.getMinutes());
    const seconds = pad(time.getSeconds());

    clock.textContent = `${name} ${hours}:${minutes}:${seconds}`;
}

// Actualizar los relojes cada segundo
setInterval(renderTimes, 100);
loadTime();
