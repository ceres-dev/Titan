function trunc(num, decimals) {
    const factor = Math.pow(10, decimals);
    return Math.trunc(num * factor) / factor;
}

function pad(str){
    return String(str).padStart(2, "0")
}
function padCustom(str, amount){
    return String(str).padStart(amount, "0")
}
function formatBytes(bytes, decimales = 2) {
    if (bytes === 0) return '0 Bytes';

    const k = 1024;
    const dm = decimales < 0 ? 0 : decimales;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB'];

    const i = Math.floor(Math.log(bytes) / Math.log(k));

    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}
function colorForProfit(element, number) {
    if (number > 0.01){
        element.classList.add('profit');
    }else if (number < -0.01) {
        element.classList.add('loses');
    }else {
        element.style.color = "gray"
    }
}

function formatMiliSeconds(ms) {
    const MS_POR_SEGUNDO = 1000;
    const MS_POR_MINUTO = MS_POR_SEGUNDO * 60;
    const MS_POR_HORA = MS_POR_MINUTO * 60;
    const MS_POR_DIA = MS_POR_HORA * 24;

    const dias = Math.floor(ms / MS_POR_DIA);
    const horas = Math.floor((ms % MS_POR_DIA) / MS_POR_HORA);
    const minutos = Math.floor((ms % MS_POR_HORA) / MS_POR_MINUTO);
    const segundos = Math.floor((ms % MS_POR_MINUTO) / MS_POR_SEGUNDO);

    let resultado = [];
    if (dias > 0) resultado.push(`${dias}d`);
    if (horas > 0) resultado.push(`${horas}h`);
    if (minutos > 0) resultado.push(`${minutos}m`);
    if (segundos > 0 || resultado.length === 0) resultado.push(`${segundos}s`);

    return resultado.join(' ');
}

window.utils = {
    trunc: trunc,
    padCustom: padCustom,
    pad: pad,
    formatBytes: formatBytes,
    formatMiliSeconds: formatMiliSeconds,
}