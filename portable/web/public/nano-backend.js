import {drawingCanvas} from './canvas.js';
// Canvas vector rasterization, composited into the engine batch at each painter-order boundary.
export class BrowserNano {
    constructor(host){this.host=host;this.contexts=new Map();this.next=1;}
    create(){const id=this.next++,canvas=drawingCanvas(1,1);this.contexts.set(id,{canvas,ctx:canvas.getContext('2d'),stack:[],images:new Map(),fonts:new Map(),dirty:false});this.reset(id);return id;}
    get(id){const c=this.contexts.get(id);if(!c)throw new Error('Disposed vector context '+id);return c;}
    reset(id){const c=this.get(id);c.state={matrix:new DOMMatrix(),clips:[],fill:'white',stroke:'black',width:1,cap:'butt',join:'miter',face:'sans-serif',size:16,align:9,paint:null};c.path=new Path2D();c.stack.length=0;}
    begin(id,w,h,ratio){const c=this.get(id);c.ratio=ratio;c.width=w;c.height=h;const pw=Math.max(1,Math.ceil(w*ratio)),ph=Math.max(1,Math.ceil(h*ratio));if(c.canvas.width!==pw)c.canvas.width=pw;if(c.canvas.height!==ph)c.canvas.height=ph;c.ctx.resetTransform();c.ctx.clearRect(0,0,pw,ph);this.reset(id);c.dirty=false;}
    end(id){
        const c=this.get(id);if(!c.dirty)return;
        // Detach a completed frame before the next pass reuses the drawing surface.
        // This also prevents already-submitted translucent UI from being drawn twice
        // when texture and vector nodes alternate within a root.
        const frame=c.canvas.transferToImageBitmap?.();
        try{this.host.graphics.composite(frame||c.canvas);}
        finally{
            frame?.close();
            if(!frame){c.ctx.save();c.ctx.resetTransform();c.ctx.clearRect(0,0,c.canvas.width,c.canvas.height);c.ctx.restore();}
        }
        c.dirty=false;
    }
    save(id){const c=this.get(id),s=c.state;c.stack.push({...s,matrix:DOMMatrix.fromMatrix(s.matrix),clips:s.clips.slice()});}
    restore(id){const c=this.get(id);if(c.stack.length)c.state=c.stack.pop();}
    resetTransform(id){this.get(id).state.matrix=new DOMMatrix();}
    translate(id,x,y){this.get(id).state.matrix.translateSelf(x,y);}
    scale(id,x,y){this.get(id).state.matrix.scaleSelf(x,y);}
    resetScissor(id){this.get(id).state.clips=[];}
    scissor(id,x,y,w,h,intersect=false){const s=this.get(id).state;if(!intersect)s.clips=[];s.clips.push({x,y,w:Math.max(0,w),h:Math.max(0,h),matrix:DOMMatrix.fromMatrix(s.matrix)});}
    beginPath(id){this.get(id).path=new Path2D();}
    path(id,method,args){this.get(id).path[method](...args);}
    circle(id,x,y,r){this.get(id).path.arc(x,y,Math.max(0,r),0,Math.PI*2);}
    roundedRect(id,x,y,w,h,r){this.get(id).path.roundRect(x,y,w,h,Math.max(0,Math.min(r,Math.abs(w)/2,Math.abs(h)/2)));}
    color(id,slot,r,g,b,a){const s=this.get(id).state;s[slot]=`rgba(${r*255},${g*255},${b*255},${a})`;if(slot==='fill')s.paint=null;}
    set(id,key,value){this.get(id).state[key]=value;}
    prepare(c){const {ctx,state:s}=c;ctx.save();ctx.resetTransform();ctx.globalAlpha=s.alpha??1;ctx.scale(c.ratio||1,c.ratio||1);const base=ctx.getTransform();for(const clip of s.clips){ctx.setTransform(base.multiply(clip.matrix));ctx.beginPath();ctx.rect(clip.x,clip.y,clip.w,clip.h);ctx.clip();}ctx.setTransform(base.multiply(s.matrix));ctx.fillStyle=s.fill;ctx.strokeStyle=s.stroke;ctx.lineWidth=s.width;ctx.lineCap=s.cap;ctx.lineJoin=s.join;this.font(c);return ctx;}
    font(c){const {ctx,state:s}=c;ctx.font=`${s.size}px "${c.fonts.get(s.face)?.family||s.face}"`;ctx.textAlign=s.align&2?'center':s.align&4?'right':'left';ctx.textBaseline=s.align&8?'top':s.align&16?'middle':s.align&32?'bottom':'alphabetic';}
    fill(id){const c=this.get(id),ctx=this.prepare(c);try{const p=c.state.paint;if(p){const image=c.images.get(p.image);if(!image)throw new Error('Disposed UI image');ctx.clip(c.path);ctx.translate(p.x,p.y);ctx.rotate(p.angle);ctx.globalAlpha*=p.alpha;ctx.drawImage(image,0,0,p.w,p.h);}else ctx.fill(c.path);c.dirty=true;}finally{ctx.restore();}}
    stroke(id){const c=this.get(id),ctx=this.prepare(c);try{ctx.stroke(c.path);c.dirty=true;}finally{ctx.restore();}}
    text(id,x,y,text){const c=this.get(id),ctx=this.prepare(c);try{ctx.fillText(text,x,y);c.dirty=true;return x+ctx.measureText(text).width;}finally{ctx.restore();}}
    bounds(id,x,y,text){const c=this.get(id);this.font(c);const m=c.ctx.measureText(text);return [x-m.actualBoundingBoxLeft,y-m.actualBoundingBoxAscent,x+m.actualBoundingBoxRight,y+m.actualBoundingBoxDescent,m.width];}
    metrics(id){const c=this.get(id);this.font(c);const m=c.ctx.measureText('Mg');const ascent=m.fontBoundingBoxAscent??m.actualBoundingBoxAscent,descent=m.fontBoundingBoxDescent??m.actualBoundingBoxDescent;return [ascent,-descent,ascent+descent];}
    async fontFace(id,name,path){const c=this.get(id),response=await fetch(path,{signal:AbortSignal.timeout(30000)});if(!response.ok)throw new Error('UI font HTTP '+response.status);const family='valthorne-vector-'+this.next++,face=new FontFace(family,await response.arrayBuffer());await face.load();if(!this.contexts.has(id))throw new Error('Vector context closed during font load');document.fonts.add(face);const old=c.fonts.get(name);if(old)document.fonts.delete(old.face);c.fonts.set(name,{family,face});return this.next++;}
    image(id,w,h,flags,bytes){const canvas=drawingCanvas(w,h),ctx=canvas.getContext('2d'),data=new ImageData(new Uint8ClampedArray(bytes),w,h);if(flags&8){const row=w*4;for(let y=0;y<(h>>1);y++){const a=y*row,b=(h-1-y)*row;for(let i=0;i<row;i++){const v=data.data[a+i];data.data[a+i]=data.data[b+i];data.data[b+i]=v;}}}ctx.putImageData(data,0,0);const image=this.next++;this.get(id).images.set(image,canvas);return image;}
    deleteImage(id,image){this.get(id).images.delete(image);}
    paint(id,x,y,w,h,angle,image,alpha){this.get(id).state.paint={x,y,w,h,angle,image,alpha};}
    delete(id){const c=this.contexts.get(id);if(!c)return;for(const f of c.fonts.values())document.fonts.delete(f.face);c.fonts.clear();c.images.clear();c.canvas.width=c.canvas.height=1;this.contexts.delete(id);}
    close(){for(const id of this.contexts.keys())this.delete(id);}
}
