async function loadBalance() {
    try {
        const response = await fetch("/api/logs");
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const logs = await response.json();

        renderLogs("logs", logs);
    } catch (error) {
        console.error("Error obteniendo el balance:", error);
    }
}

function renderLogs(elementId, logs) {
    const list = document.getElementById(elementId);

    list.innerHTML = "";
    if (logs.length > 0) {
        logs.forEach((log) => {
            const li = document.createElement("li");
            const strong = document.createElement("code");
            console.log(log)
            li.classList.add("log");
            li.appendChild(strong);

            const pad = (num) => String(num).padStart(2, "0");
            const date = new Date(log.time)

            const hours = pad(date.getHours());
            const minutes = pad(date.getMinutes());
            const seconds = pad(date.getSeconds());
            const milliseconds = pad(date.getMilliseconds());


            li.append(` ${hours}:${minutes}:${seconds}:${milliseconds} [${log.thread}] [${log.level}]: ${log.message}`);

            list.appendChild(li);
        });
    }
}
loadBalance();