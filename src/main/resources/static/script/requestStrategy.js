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
            const h3 = document.createElement("h3");
            const div = document.createElement("div");
            const small = document.createElement("small");
            const span = document.createElement("span");
            span.textContent = strategy.status
            span.classList.add("status" + strategy.status);
            h3.textContent = strategy.label;
            div.classList.add("strategyElement");
            div.appendChild(h3);
            div.appendChild(span);
            small.textContent = strategy.name;

            li.appendChild(div);
            li.appendChild(small);

            list.appendChild(li);
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