let highestZIndex = 1;

function createWindow({id}) {

    const windowElement = document.getElementById(id);
    /*
    windowElement.innerHTML = `
        <div class="window-header">
            <span class="window-title">${title}</span>
        </div>

        <div class="window-content">
            ${content}
        </div>
    `;*/

    //document.getElementById("desktop").appendChild(windowElement);

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
}