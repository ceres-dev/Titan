const formConversion = document.getElementById("formConversion");
const sumitConversion = document.getElementById("sumitConversion");
const errorConversion = document.getElementById("errorConversion");


formConversion.addEventListener("submit", async (event) => {
    event.preventDefault();

    sumitConversion.disabled = true;
    errorConversion.textContent = ""
    try {
        const amount = formConversion.elements.amount.value;

        if (amount < 0) {
            errorConversion.textContent = "La cantidad debe ser mayor que 0"
            return;
        }
        const formData = new URLSearchParams(new FormData(formConversion));
        const response = await fetch("/api/conversion", {
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
        sumitConversion.disabled = false;
    }
});