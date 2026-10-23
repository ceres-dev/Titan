let highestZIndex = 1;

function createWindow({id}) {

    const windowElement = document.getElementById(id);

    const header = windowElement.querySelector(".windowHeader");
    const closer = windowElement.querySelector(".windowCloser");

    makeDraggable(windowElement, header, closer);

    // Al hacer click, poner la ventana encima
    windowElement.addEventListener("mousedown", () => {
        highestZIndex++;
        windowElement.style.zIndex = highestZIndex;
    });

    return windowElement;
}

function makeDraggable(windowElement, header, closer) {
    const resetButton = document.getElementById("resetWindows");

    let dragging = false;

    let offsetX = 0;
    let offsetY = 0;

    header.addEventListener("mousedown", (event) => {

        dragging = true;
        const rect = new DOMMatrix(windowElement.style.transform);
        offsetX = event.clientX - rect.m41;
        offsetY = event.clientY - rect.m42;

        event.preventDefault();
    });

    document.addEventListener("mousemove", (event) => {

        if (!dragging)
            return;
        console.log("x", event.clientX , event.clientY );
        console.log("x", offsetX , offsetY);

        let x = event.clientX - offsetX;
        let y = event.clientY - offsetY;
        windowElement.style.transform = `translate(${x}px, ${y}px)`;
    });

    document.addEventListener("mouseup", () => {
        dragging = false;
    });
    // No se usa display = node por qué puede romper el layout
    closer.addEventListener("click", (event) => {
        windowElement.style.visibility = "hidden";
        windowElement.style.pointerEvents = "none";
    })

    resetButton.addEventListener("click", (event) => {
        windowElement.style.visibility = "visible";
        windowElement.style.pointerEvents = "all";
        windowElement.style.transform = `translate(0px, 0px)`;
    })
}