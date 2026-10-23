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


window.utils = {
    trunc: trunc,
    padCustom: padCustom,
    pad: pad,
    formatBytes: formatBytes,
}