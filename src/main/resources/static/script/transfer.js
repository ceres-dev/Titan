const formTransfer = document.getElementById("formTransfer");
const sumitTransfer = document.getElementById("sumitTransfer");
const errorTransfer = document.getElementById("errorTransfer");


formTransfer.addEventListener("submit", async (event) => {
    event.preventDefault();

    sumitTransfer.disabled = true;
    errorTransfer.textContent = ""
    try {
        const amount = formTransfer.elements.amount.value;

        if (amount < 0) {
            errorTransfer.textContent = "La cantidad debe ser mayor que 0"
            return;
        }
        const formData = new URLSearchParams(new FormData(formTransfer));
        const response = await fetch("/api/transfer", {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded"
            },
            body: formData,
            credentials: "same-origin"
        });
    } catch (error) {
        console.log(error);
    }finally {
        sumitTransfer.disabled = false;
    }
});