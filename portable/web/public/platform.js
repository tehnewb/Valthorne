import { BrowserWindow } from './window-backend.js';
const isMobile = () => typeof matchMedia === 'function' && (matchMedia('(pointer: coarse)').matches || matchMedia('(hover: none)').matches);
const getDevicePixelRatio = () => {
  const deviceRatio = Number(window.devicePixelRatio) || 1;
  const maxRatio = isMobile() ? 3 : 2;
  return Math.max(1, Math.min(deviceRatio, maxRatio));
};
/** Owns browser listeners and voices. Never installs process-wide prototype patches. */
export class BrowserPlatform {
    constructor(canvas) {
        this.canvas=canvas;this.events=new AbortController();this.keys=new Set();this.pressed=new Set();this.buttons=new Set();
        this.pointerId = null;
        this.look=[0,0];this.voices=new Set();this.closed=false;this.audio=null;
        this.window=new BrowserWindow(canvas,this);
        this.mouseX=0;this.mouseY=0;this.scrollX=0;this.scrollY=0;
        this.overlay=document.createElement('canvas');this.overlay.id='overlay';this.overlay.setAttribute('aria-hidden','true');
        Object.assign(this.overlay.style,{position:'absolute',inset:'0',pointerEvents:'none',width:'100%',height:'100%'});
        canvas.after(this.overlay);this.ctx=this.overlay.getContext('2d');
        const on=(target,event,callback)=>target.addEventListener(event,callback,{signal:this.events.signal});
        on(window,'keydown',e=>{
            if(e.target instanceof HTMLInputElement||e.target instanceof HTMLTextAreaElement||e.target instanceof HTMLSelectElement)return;
            const fresh=!this.keys.has(e.code);if(fresh)this.pressed.add(e.code);this.keys.add(e.code);
            this.capsLock=e.getModifierState('CapsLock');if(fresh)this.publishKey(e,true);
            if(!e.isComposing&&!e.ctrlKey&&!e.metaKey&&!e.altKey&&[...e.key].length===1)this.legacyTextEvent?.(e.key);
            if(this.legacyKeyEvent&&['Tab','Backspace','ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.code))e.preventDefault();
            if(document.pointerLockElement===canvas&&['Space','ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.code))e.preventDefault();
        });
        on(window,'keyup',e=>{if(this.keys.delete(e.code))this.publishKey(e,false);});
        on(canvas,'pointerdown',e=>{
            if(this.pointerId !== null && this.pointerId !== e.pointerId) return;
            if(this.pointerId === null)this.pointerId = e.pointerId;
            if(this.canvas.setPointerCapture){
                try { this.canvas.setPointerCapture(e.pointerId); } catch (error) {}
            }
            if(e.pointerType === 'touch' || e.pointerType === 'pen') e.preventDefault();
            this.buttons.add(e.button);
            this.mouseButton(e,0);
            this.unlockAudio();
        });
        on(window,'pointerup',e=>{
            if(this.pointerId !== null && this.pointerId !== e.pointerId) return;
            if(this.pointerId === e.pointerId){
                this.pointerId = null;
                if(this.canvas.releasePointerCapture && this.canvas.hasPointerCapture?.(e.pointerId)){
                    try { this.canvas.releasePointerCapture(e.pointerId); } catch (error) {}
                }
            }
            if(this.buttons.delete(e.button))this.mouseButton(e,1);
        });
        on(window,'pointermove',e=>{
            if(this.pointerId !== null && e.pointerId !== this.pointerId) return;
            if(e.pointerType === 'touch' || e.pointerType === 'pen') e.preventDefault();
            const locked=document.pointerLockElement===canvas;
            if(!locked && this.pointerId===null && e.target!==canvas)return;
            const fromX=this.mouseX,fromY=this.window.height-this.mouseY;
            if(locked){this.look[0]+=e.movementX;this.look[1]+=e.movementY;this.mouseX+=e.movementX;this.mouseY+=e.movementY;}
            else{const rect=this.canvas.getBoundingClientRect();this.mouseX=e.clientX-rect.left;this.mouseY=e.clientY-rect.top;}
            const mods=this.modifiers(e);
            if(this.buttons.size)for(const button of this.buttons)this.legacyMouseEvent?.(3,this.nativeButton(button),mods,fromX,fromY,this.mouseX,this.window.height-this.mouseY);
            else this.legacyMouseEvent?.(2,-1,mods,fromX,fromY,this.mouseX,this.window.height-this.mouseY);
        });
        on(canvas,'wheel',e=>{const scale=e.deltaMode===1?1:e.deltaMode===2?10:.01;this.scrollX=-e.deltaX*scale;this.scrollY=-e.deltaY*scale;this.legacyScrollEvent?.(this.scrollX,this.scrollY);});
        on(window,'pointercancel',e=>{
            if(this.pointerId !== null && e.pointerId !== this.pointerId) return;
            if(this.pointerId === e.pointerId){
                if(this.canvas.releasePointerCapture && this.canvas.hasPointerCapture?.(e.pointerId)){
                    try { this.canvas.releasePointerCapture(e.pointerId); } catch (error) {}
                }
                this.pointerId = null;
            }
            this.resetInput();
        });
        on(document,'pointerlockchange',()=>{
            this.resetInput();const locked=document.pointerLockElement===canvas;
            document.body.classList.toggle('playing',locked);
            // Establish the captured coordinate origin before the first real delta.
            // FPS listeners can reset their previous position when capture starts.
            if(locked)this.legacyMouseEvent?.(2,-1,0,this.mouseX,this.window.height-this.mouseY,this.mouseX,this.window.height-this.mouseY);
        });
        on(document,'pointerlockerror',()=>{this.captureError='Pointer capture was denied';});
        on(document,'mousemove',e=>{
            const fromX=this.mouseX,fromY=this.window.height-this.mouseY;
            const locked=document.pointerLockElement===canvas;
            if(!locked&&e.target!==canvas)return;
            if(this.pointerId !== null) return;
            if(locked){this.look[0]+=e.movementX;this.look[1]+=e.movementY;this.mouseX+=e.movementX;this.mouseY+=e.movementY;}
            else{const rect=this.canvas.getBoundingClientRect();this.mouseX=e.clientX-rect.left;this.mouseY=e.clientY-rect.top;}
            const mods=this.modifiers(e);
            if(this.buttons.size)for(const button of this.buttons)this.legacyMouseEvent?.(3,this.nativeButton(button),mods,fromX,fromY,this.mouseX,this.window.height-this.mouseY);
            else this.legacyMouseEvent?.(2,-1,mods,fromX,fromY,this.mouseX,this.window.height-this.mouseY);
        });
        on(window,'blur',()=>{this.resetInput();this.legacyFocusEvent?.(false);});
        on(window,'focus',()=>this.legacyFocusEvent?.(true));
        on(window,'compositionend',e=>{if(e.target===canvas||e.target===document.body)this.legacyTextEvent?.(e.data);});
        on(window,'resize',()=>this.window.changed());
        on(document,'visibilitychange',()=>{if(document.hidden){this.resetInput();this.audio?.suspend();}});
        on(document,'pointerdown',()=>this.unlockAudio());
        on(document,'keydown',()=>this.unlockAudio());
    }
    check(){if(this.closed)throw new Error('Platform is closed');}
    keyDown(code){this.check();return this.keys.has(code);}
    nativeButton(button){return button===1?2:button===2?1:button;}
    modifiers(e){return (e.shiftKey?1:0)|(e.ctrlKey?2:0)|(e.altKey?4:0)|(e.metaKey?8:0);}
    mouseButton(e,kind){if(document.pointerLockElement!==this.canvas){const rect=this.canvas.getBoundingClientRect();this.mouseX=e.clientX-rect.left;this.mouseY=e.clientY-rect.top;}this.legacyMouseEvent?.(kind,this.nativeButton(e.button),this.modifiers(e),0,0,this.mouseX,this.window.height-this.mouseY);}
    mouseCoordinate(index){return index===0?this.mouseX:index===1?this.window.height-this.mouseY:index===2?this.scrollX:this.scrollY;}
    cursorMode(mode){if(mode===0x34003)this.capture(true);else if(mode===0x34001||mode===0x34002){this.capture(false);this.canvas.style.cursor=mode===0x34002?'none':(this.cursorValue||'default');}else throw new Error('Invalid cursor mode');}
    cursorShape(shape){const shapes=['default','text','crosshair','pointer','ew-resize','ns-resize'];if(!shapes[shape-0x36001])throw new Error('Invalid cursor shape');this.canvas.style.cursor=this.cursorValue=shapes[shape-0x36001];}
    keyCode(code){
        if(/^Key[A-Z]$/.test(code))return code.charCodeAt(3);
        if(/^Digit[0-9]$/.test(code))return code.charCodeAt(5);
        if(/^Numpad[0-9]$/.test(code))return 320+Number(code.slice(6));
        if(/^F\d+$/.test(code))return 289+Number(code.slice(1));
        return ({Space:32,Quote:39,Comma:44,Minus:45,Period:46,Slash:47,Semicolon:59,Equal:61,BracketLeft:91,Backslash:92,BracketRight:93,Backquote:96,Escape:256,Enter:257,Tab:258,Backspace:259,Insert:260,Delete:261,ArrowRight:262,ArrowLeft:263,ArrowDown:264,ArrowUp:265,PageUp:266,PageDown:267,Home:268,End:269,CapsLock:280,ScrollLock:281,NumLock:282,PrintScreen:283,Pause:284,NumpadDecimal:330,NumpadDivide:331,NumpadMultiply:332,NumpadSubtract:333,NumpadAdd:334,NumpadEnter:335,NumpadEqual:336,ShiftLeft:340,ControlLeft:341,AltLeft:342,MetaLeft:343,ShiftRight:344,ControlRight:345,AltRight:346,MetaRight:347,ContextMenu:348})[code]??-1;
    }
    legacyKeyDown(key){for(const code of this.keys)if(this.keyCode(code)===key)return true;return false;}
    publishKey(event,down){const key=this.keyCode(event.code);if(key>=0)this.legacyKeyEvent?.(key,(event.shiftKey?1:0)|(event.ctrlKey?2:0)|(event.altKey?4:0)|(event.metaKey?8:0),down);}
    takeKeyPress(code){this.check();return this.pressed.delete(code);}
    resetInput(){const released=Array.from(this.keys),buttons=Array.from(this.buttons);this.keys.clear();this.pressed.clear();this.buttons.clear();this.pointerId=null;this.look.fill(0);for(const code of released)this.publishKey({code},false);for(const button of buttons)this.legacyMouseEvent?.(1,this.nativeButton(button),0,0,0,this.mouseX,this.window.height-this.mouseY);}
    takeLook(axis){this.check();const value=this.look[axis];this.look[axis]=0;return value;}
    capture(enabled){this.check();if(enabled){const result=this.canvas.requestPointerLock();result?.catch(error=>{this.captureError=String(error);});}else if(document.pointerLockElement===this.canvas)document.exitPointerLock();}
    settingKey(key){this.check();if(typeof key!=='string'||!key.length||key.length>128)throw new Error('Invalid setting key');return `valthorne:${key}`;}
    loadSetting(key){return localStorage.getItem(this.settingKey(key));}
    saveSetting(key,value){if(typeof value!=='string'||value.length>65536)throw new Error('Setting exceeds 64 Ki characters');localStorage.setItem(this.settingKey(key),value);}
    removeSetting(key){localStorage.removeItem(this.settingKey(key));}
    unlockAudio(){if(this.closed)return;try{this.audio??=new AudioContext();this.audio.resume().catch(error=>{this.audioError=String(error);});}catch(error){this.audioError=String(error);}}
    tone(frequency,seconds,volume,pan){
        this.check();if(![frequency,seconds,volume,pan].every(Number.isFinite)||frequency<20||frequency>20000||seconds<=0||seconds>10||volume<0||volume>1||Math.abs(pan)>1)throw new Error('Invalid tone');
        if(!this.audio||this.audio.state!=='running')return false;
        if(this.voices.size>=32)return false;
        const context=this.audio,oscillator=context.createOscillator(),gain=context.createGain(),panner=context.createStereoPanner(),now=context.currentTime;
        oscillator.frequency.value=frequency;panner.pan.value=pan;
        gain.gain.setValueAtTime(0,now);gain.gain.linearRampToValueAtTime(volume,now+Math.min(.005,seconds/2));gain.gain.linearRampToValueAtTime(0,now+seconds);
        oscillator.connect(gain).connect(panner).connect(context.destination);
        const voice={oscillator,gain,panner};this.voices.add(voice);
        oscillator.onended=()=>{oscillator.disconnect();gain.disconnect();panner.disconnect();this.voices.delete(voice);};
        oscillator.start(now);oscillator.stop(now+seconds);return true;
    }
    beginOverlay(){
        this.check();const ratio=getDevicePixelRatio(),w=Math.round(this.window.width*ratio),h=Math.round(this.window.height*ratio);
        if(this.overlay.width!==w||this.overlay.height!==h){this.overlay.width=w;this.overlay.height=h;}
        this.ctx.setTransform(ratio,0,0,ratio,0,0);this.ctx.clearRect(0,0,this.window.width,this.window.height);
    }
    color(rgb){return `#${(rgb&0xffffff).toString(16).padStart(6,'0')}`;}
    rectangle(x,y,w,h,rgb,alpha){this.check();if(![x,y,w,h,alpha].every(Number.isFinite)||w<0||h<0||alpha<0||alpha>1)throw new Error('Invalid rectangle');this.ctx.globalAlpha=alpha;this.ctx.fillStyle=this.color(rgb);this.ctx.fillRect(x,y,w,h);this.ctx.globalAlpha=1;}
    text(text,x,y,size,rgb){this.check();if(![x,y,size].every(Number.isFinite)||size<=0||size>256||text.length>4096)throw new Error('Invalid text');this.ctx.fillStyle=this.color(rgb);this.ctx.font=`${size}px system-ui`;this.ctx.fillText(text,x,y);}
    close(){if(this.closed)return;this.closed=true;this.events.abort();this.resetInput();document.body.classList.remove('playing');if(document.pointerLockElement===this.canvas)document.exitPointerLock();for(const {oscillator} of this.voices)oscillator.stop();this.audio?.close().catch(()=>{});this.overlay.remove();this.window.close();}
}
