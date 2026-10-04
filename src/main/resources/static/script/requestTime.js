async function loadTime() {
    try {
        const response = await fetch("/api/time/exchange");
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const balance = await response.json();

        renderTime("futureBalance", balance.future);
        renderTime("spotBalance", balance.spot);
        //renderBalance("fondoBalance", balance.spot);
        //renderBalance("totalBalance", balance.spot);


    } catch (error) {
        console.error("Error obteniendo el balance:", error);
    }
}

function renderTime(elementId, time) {
    const display = document.getElementById(elementId);
}
loadTime();