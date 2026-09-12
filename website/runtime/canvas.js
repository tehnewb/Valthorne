/** Private drawing surface; an unattached canvas also works without OffscreenCanvas. */
export function drawingCanvas(width,height){
    if(typeof OffscreenCanvas!=='undefined')return new OffscreenCanvas(width,height);
    const canvas=document.createElement('canvas');canvas.width=width;canvas.height=height;return canvas;
}
