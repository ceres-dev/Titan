async function loadBalance() {
    try {
        const response = await fetch("/api/balance");
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const balance = await response.json();

        renderBalance("futureBalance", balance.future);
        renderBalance("futureValued", balance.futureValued);
        renderBalance("spotBalance", balance.spot);
        renderRoi("roi", balance.roi)
    } catch (error) {
        console.error("Error obteniendo el balance:", error);
    }
}

function renderBalance(elementId, balances) {
    const list = document.getElementById(elementId);

    // Limpiar contenido anterior
    list.innerHTML = "";
    if (Object.entries(balances).length > 0) {
        for (const [asset, amount] of Object.entries(balances)) {
            const li = document.createElement("li");
            const strong = document.createElement("strong");
            strong.textContent = asset;

            li.appendChild(strong);
            li.append(` ${amount}`);

            list.appendChild(li);
        }
    }else {
        const li = document.createElement("li");
        const strong = document.createElement("strong");
        strong.textContent = "Sin Fondos";
        li.appendChild(strong);
        list.appendChild(li);
    }
}
function renderRoi(elementId, roi) {
    const content = document.getElementById(elementId);
    content.innerHTML = "";

    const div1D = document.createElement("div");
    const strong1D = document.createElement("strong");
    div1D.textContent = "ROI 1D: "
    colorForProfit(strong1D, roi.percent1D*100);

    strong1D.innerHTML = `${utils.trunc(roi.percent1D*100, 2)}% ${utils.trunc(roi.absolute1D, 2)} USD~`;
    div1D.appendChild(strong1D);

    const div7D = document.createElement("div");
    const strong7D = document.createElement("strong");
    div7D.textContent = "ROI 7D: "
    colorForProfit(strong7D, roi.percent7D*100);

    strong7D.innerHTML = `${utils.trunc(roi.percent7D*100, 2)}% ${utils.trunc(roi.absolute7D, 2)} USD~`;
    div7D.appendChild(strong7D);

    content.appendChild(div1D);
    content.appendChild(div7D);
}
loadBalance();