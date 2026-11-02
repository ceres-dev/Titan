
const breadcrumb = document.getElementById("breadcrumb");

const path = window.location.pathname;

const segments = path.split("/").filter(Boolean);
let currentPath = "";

segments.forEach((segment, index) => {
    currentPath += "/" + segment;

    const link = document.createElement("a");
    link.href = currentPath;
    const text = decodeURIComponent(segment);
    link.textContent = text.charAt(0).toUpperCase() + text.slice(1);

    breadcrumb.appendChild(link);

    // Agregar separador, excepto al último elemento
    if (index < segments.length - 1) {
        const separator = document.createElement("span");
        separator.textContent = "/";
        breadcrumb.appendChild(separator);
    }
});
