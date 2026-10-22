async function loadBalance() {
    try {
        const response = await fetch("/api/logs");
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const logs = await response.json();
        document.getElementById("logs").innerHTML = "";

        addRenderLogs("logs", logs);

    } catch (error) {
        console.error("Error obteniendo el balance:", error);
    }
}

function addRenderLogs(elementId, logs) {
    const list = document.getElementById(elementId);
    if (logs.length > 0) {
        logs.forEach((log) => {
            const li = document.createElement("li");
            const strong = document.createElement("code");
            li.classList.add("log");
            li.appendChild(strong);

            const date = new Date(log.time)

            const hours = utils.pad(date.getHours());
            const minutes = utils.pad(date.getMinutes());
            const seconds = utils.pad(date.getSeconds());
            const milliseconds = utils.padCustom(date.getMilliseconds(), 3);

            const levelSpan = document.createElement("span");

            switch (log.level) {
                case "INFO":
                    levelSpan.style.color = "green";
                    break;
                case "WARN":
                    levelSpan.style.color = "yellow";
                    break;
                case "ERROR":
                    levelSpan.style.color = "red";
                    break;
                default:
                    levelSpan.style.color = "gray";
            }
            levelSpan.textContent = log.level;

            const threadSpan = document.createElement("span");
            threadSpan.textContent = log.thread;
            threadSpan.style.color = "#aaf"

            li.append(`${hours}:${minutes}:${seconds}:${milliseconds} [`, threadSpan, `] [`, levelSpan, `]: ${log.message}`);

            list.appendChild(li);
        });
    }
}
loadBalance();