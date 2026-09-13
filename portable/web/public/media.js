export class BrowserMedia {
    constructor(host){this.host=host;this.pending=new Map();this.items=new Map();this.nextId=1;this.closed=false;this.bytes=0;}
    load(uri,sound,success,failure){
        if(this.closed||this.clearing)throw new Error('Media service closed or clearing');
        if(this.pending.size+this.items.size>=32)throw new Error('Media asset budget reached');
        const url=new URL(uri,location.href);if(url.origin!==location.origin||!['http:','https:'].includes(url.protocol))throw new Error('Media must be same-origin');
        const id=this.nextId++,controller=new AbortController(),request={controller,success,failure};this.pending.set(id,request);
        request.timer=setTimeout(()=>this.cancel(id,'Media load timed out'),15000);
        this.host.assets.fetchBytes(url,controller.signal).then(async bytes=>{
            if(!this.pending.has(id))return;
            let image=null,buffer=null;
            if(sound){const p=this.host.platform;p.audio??=new AudioContext();buffer=await p.audio.decodeAudioData(bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength));}
            else{
                // Only raster PNG/JPEG/WebP; SVG can reference external resources.
                const png=bytes[0]===137&&bytes[1]===80&&bytes[2]===78&&bytes[3]===71,jpeg=bytes[0]===255&&bytes[1]===216,webp=new TextDecoder().decode(bytes.subarray(8,12))==='WEBP';
                if(!png&&!jpeg&&!webp)throw new Error('Expected PNG, JPEG or WebP');
                image=await createImageBitmap(new Blob([bytes]));
            }
            if(!this.pending.has(id)){image?.close();return;}
            const size=image?image.width*image.height*4:buffer.length*buffer.numberOfChannels*4;
            if(size>32*1024*1024||this.bytes+size>64*1024*1024){image?.close();throw new Error('Decoded media budget exceeded');}
            this.pending.delete(id);clearTimeout(request.timer);this.items.set(id,{image,buffer,size,voices:new Set()});this.bytes+=size;request.delivered=true;success(id);
        }).catch(error=>{if(this.pending.has(id))this.cancel(id,String(error.message||error));else if(request.delivered)queueMicrotask(()=>{throw error;});});
        return id;
    }
    cancel(id,reason='cancelled'){const request=this.pending.get(id);if(!request)return;this.pending.delete(id);clearTimeout(request.timer);request.controller.abort();try{request.failure(reason);}catch(error){queueMicrotask(()=>{throw error;});}}
    get(id){const item=this.items.get(id);if(!item)throw new Error('Media handle closed');return item;}
    draw(id,x,y,w,h,alpha){const item=this.get(id);if(!item.image||![x,y,w,h,alpha].every(Number.isFinite)||w<0||h<0||alpha<0||alpha>1)throw new Error('Invalid sprite');const ctx=this.host.platform.ctx;ctx.globalAlpha=alpha;try{ctx.drawImage(item.image,x,y,w,h);}finally{ctx.globalAlpha=1;}}
    play(id,volume,pan){
        const item=this.get(id),p=this.host.platform,context=p.audio;
        if(!item.buffer||!Number.isFinite(volume)||volume<0||volume>1||!Number.isFinite(pan)||Math.abs(pan)>1)throw new Error('Invalid sound');
        if(!context||context.state!=='running'||p.voices.size>=32)return false;
        const oscillator=context.createBufferSource(),gain=context.createGain(),panner=context.createStereoPanner();oscillator.buffer=item.buffer;gain.gain.value=volume;panner.pan.value=pan;
        oscillator.connect(gain).connect(panner).connect(context.destination);const voice={oscillator,gain,panner};p.voices.add(voice);item.voices.add(voice);
        oscillator.onended=()=>{oscillator.disconnect();gain.disconnect();panner.disconnect();p.voices.delete(voice);item.voices.delete(voice);};oscillator.start();return true;
    }
    release(id){const item=this.items.get(id);if(!item)return;this.items.delete(id);this.bytes-=item.size;item.image?.close();for(const voice of item.voices)voice.oscillator.stop();}
    clear(){if(this.clearing)return;this.clearing=true;try{for(const id of this.pending.keys())this.cancel(id);for(const id of this.items.keys())this.release(id);}finally{this.clearing=false;}}
    close(){if(this.closed)return;this.closed=true;this.clear();}
}
