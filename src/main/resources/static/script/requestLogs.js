async function loadBalance() {
    try {
        const response = await fetch("/api/logs");
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const logs = await response.json();
        document.getElementById("logs").innerHTML = "";

        addRenderLogs(logs);

    } catch (error) {
        console.error("Error obteniendo el balance:", error);
    }
}

function addRenderLogs(logs) {
    const container = document.getElementById("logs");
    if (logs.length > 0) {
        logs.forEach((log) => {
            const li = document.createElement("pre");
            const code = document.createElement("code");
            li.classList.add("log");
            li.appendChild(code);

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

            code.append(`${hours}:${minutes}:${seconds}:${milliseconds} [`, threadSpan, `] [`, levelSpan, `]: `);
            code.insertAdjacentHTML("beforeend", formatLog(log.message))

            container.appendChild(li);
            container.scrollTop = container.scrollHeight;
        });
    }
}
function formatLog(text) {
    const regex = /\b(?:true|false|SELL|BUY)\b|\b-?\d+(?:[.,]\d+)?\b/g;

    let result = "";
    let lastIndex = 0;

    for (const match of text.matchAll(regex)) {
        // Escapar el texto normal antes de la coincidencia
        result += escapeHtml(text.slice(lastIndex, match.index));

        const value = match[0];
        let cssClass;

        if (value === "true" || value === "false") {
            cssClass = "variable";
        }else if (value.toUpperCase() === "BUY"){
            cssClass = "variableBuy";
        }else if (value.toUpperCase() === "SELL"){
            cssClass = "variableSell";
        }else {
            // Números
            //cssClass = "variable";
        }

        result += `<span class="${cssClass}">${value}</span>`;
        lastIndex = match.index + value.length;
    }

    result += escapeHtml(text.slice(lastIndex));

    return result;
}

function escapeHtml(text) {
    return text.replace(/[&<>"']/g, char => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[char]);
}
loadBalance();