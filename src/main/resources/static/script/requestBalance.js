async function loadBalance() {
    try {
        const response = await fetch("/api/balance");
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const balance = await response.json();

        renderBalance("futureBalance", balance.future);
        renderBalance("spotBalance", balance.spot);
        //renderBalance("fondoBalance", balance.spot);
        //renderBalance("totalBalance", balance.spot);


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
loadBalance();