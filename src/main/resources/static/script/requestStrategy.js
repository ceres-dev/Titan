async function loadStrategy() {
    try {
        const response = await fetch("/api/strategy");
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const strategy = await response.json();

        renderStrategy("strategy", strategy);
    } catch (error) {
        console.error("Error obteniendo el balance:", error);
    }
}

function renderStrategy(elementId, strategies) {
    const list = document.getElementById(elementId);

    // Limpiar contenido anterior
    list.innerHTML = "";
    if (strategies.length > 0) {
        strategies.forEach((strategy) => {
            const li = document.createElement("li");
            const strong = document.createElement("strong");
            const button = document.createElement("button");
            strong.textContent = strategy.label;

            li.appendChild(strong);
            li.append(` ${strategy.isRunning}`);
            li.append(button);

            list.appendChild(li);
            if (strategy.isRunning){
                button.textContent = "Stop"
            }else {
                button.textContent = "Start"
            }
        })
    }else {
        const li = document.createElement("li");
        const strong = document.createElement("strong");
        strong.textContent = "Sin Estrategias";
        li.appendChild(strong);
        list.appendChild(li);
    }

}
loadStrategy();