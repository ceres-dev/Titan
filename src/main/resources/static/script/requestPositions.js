async function loadPosition() {
    try {
        const response = await fetch("/api/positions");
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const positions = await response.json();

        renderPosition("positions", positions);
    } catch (error) {
        console.error("Error obteniendo el balance:", error);
    }
}
function renderPosition(elementId, positions) {
    const table = document.getElementById(elementId);
    table.innerHTML = "";

    if (positions.length > 0) {
        positions.forEach((position) => {
            const tr = document.createElement("tr");

            const tdSymbol = document.createElement("td");
            tdSymbol.textContent = position.symbol

            const tdQuantity = document.createElement("td");
            tdQuantity.textContent = utils.trunc(position.quantity, 2)

            const tdEntryPriceAvg = document.createElement("td");
            tdEntryPriceAvg.textContent = utils.trunc(position.entryPriceAvg, 2)

            const tdPnlUnrealize = document.createElement("td");

            tr.appendChild(tdSymbol);
            tr.appendChild(tdQuantity);
            tr.appendChild(tdEntryPriceAvg);
            const span = document.createElement("span");
            if (position.pnlUnrealize > 0.01){
                span.style.color = "green"
            }else if (position.pnlUnrealize < -0.01) {
                span.style.color = "red"
            }else {
                span.style.color = "gray"
            }
            span.textContent = utils.trunc(position.pnlUnrealize, 3)
            tdPnlUnrealize.appendChild(span);
            tr.appendChild(tdPnlUnrealize);
            table.appendChild(tr);
        })
    }else {
        const li = document.createElement("li");
        const strong = document.createElement("strong");
        strong.textContent = "Sin Posiciones";
        li.appendChild(strong);
        list.appendChild(li);
    }
}
loadPosition();