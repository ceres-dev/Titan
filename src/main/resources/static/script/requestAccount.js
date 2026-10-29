async function loadAccount() {
    try {
        const response = await fetch("/auth/account");
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const account = await response.json();

        renderAccount(account);
    } catch (error) {
        console.error("Error obteniendo el balance:", error);
    }
}

function renderAccount(account) {
    const container = document.getElementById("account");

    // Limpiar contenido anterior
    container.innerHTML = "";
    const username = account.username;
    const strong = document.createElement("strong");
    strong.innerText = "Username: " + username;
    container.appendChild(strong);
}
loadAccount();