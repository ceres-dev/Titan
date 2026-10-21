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


window.utils = {
    trunc: trunc,
    padCustom: padCustom,
    pad: pad,
}