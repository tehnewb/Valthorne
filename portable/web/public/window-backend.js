/** The browser window is a game surface inside the page, not an OS window. */
export class BrowserWindow {
 constructor(canvas,platform){
  this.platform=platform;this.canvas=canvas;this.resizable=true;this.borderless=true;
  this.stage=document.createElement('div');this.stage.id='valthorne-window';
  this.stage.style.cssText='position:fixed;left:0;top:0;width:100vw;height:100vh;overflow:hidden;box-sizing:border-box;touch-action:none;overscroll-behavior:none;user-select:none;-webkit-user-select:none;';
  canvas.before(this.stage);this.stage.append(canvas);canvas.style.position='absolute';canvas.style.touchAction='none';canvas.style.userSelect='none';canvas.style.WebkitUserSelect='none';canvas.tabIndex=0;
  this.oldWidth=this.width;this.oldHeight=this.height;
  this.observer=new ResizeObserver(()=>this.changed());this.observer.observe(this.stage);
  const options={signal:platform.events.signal};
  for(const event of ['pointerdown','keydown'])document.addEventListener(event,()=>{if(this.pendingFullscreen)this.fullscreen(true);},options);
  document.addEventListener('fullscreenchange',()=>{this.pendingFullscreen=false;this.changed();},options);
 }
 get width(){return this.stage.clientWidth||1;}get height(){return this.stage.clientHeight||1;}
 changed(){const w=this.width,h=this.height;if(w!==this.oldWidth||h!==this.oldHeight){this.platform.legacyResizeEvent?.(this.oldWidth,this.oldHeight,w,h);this.oldWidth=w;this.oldHeight=h;}}
 size(w,h){if(!Number.isInteger(w)||!Number.isInteger(h)||w<1||h<1)throw new Error('Window dimensions must be positive integers');this.stage.style.width=w+'px';this.stage.style.height=h+'px';this.changed();}
 position(x,y){if(!Number.isFinite(x+y))throw new Error('Invalid window position');this.stage.style.left=x+'px';this.stage.style.top=y+'px';}
 center(){this.position(Math.max(0,(innerWidth-this.width)/2),Math.max(0,(innerHeight-this.height)/2));}
 setResizable(value){this.resizable=value;this.stage.style.resize=value?'both':'none';}
 setBorderless(value){this.borderless=value;this.stage.style.border=value?'none':'1px solid #667';this.changed();}
 fullscreen(value){
  if(!value){this.pendingFullscreen=false;if(document.fullscreenElement===this.stage)document.exitFullscreen().catch(e=>this.error=String(e));return;}
  if(document.fullscreenElement===this.stage)return;
  if(!document.fullscreenEnabled){this.error='Fullscreen is unavailable in this browser or embedding policy';return;}
  if(!navigator.userActivation.isActive){this.pendingFullscreen=true;return;}
  this.pendingFullscreen=false;this.stage.requestFullscreen().catch(e=>this.error=String(e));
 }
 minimize(){this.stage.style.visibility='hidden';this.platform.resetInput();}
 maximize(){if(!this.saved)this.saved=[this.stage.style.left,this.stage.style.top,this.stage.style.width,this.stage.style.height];this.stage.style.visibility='visible';Object.assign(this.stage.style,{left:'0',top:'0',width:'100vw',height:'100vh'});this.changed();}
 restore(){this.stage.style.visibility='visible';if(this.saved){const [left,top,width,height]=this.saved;Object.assign(this.stage.style,{left,top,width,height});this.saved=null;this.changed();}}
 focus(){window.focus();this.canvas.focus({preventScroll:true});}
 opacity(value){if(!Number.isFinite(value)||value<0||value>1)throw new Error('Opacity must be in [0,1]');this.stage.style.opacity=String(value);}
 limits(a,b,c,d){for(const n of [a,b,c,d])if(n!==-1&&(!Number.isInteger(n)||n<1))throw new Error('Invalid size limit');if(a!==-1&&c!==-1&&a>c||b!==-1&&d!==-1&&b>d)throw new Error('Minimum exceeds maximum');Object.assign(this.stage.style,{minWidth:a===-1?'':a+'px',minHeight:b===-1?'':b+'px',maxWidth:c===-1?'':c+'px',maxHeight:d===-1?'':d+'px'});this.changed();}
 image(bytes,width,height){if(width<1||height<1||width>4096||height>4096||bytes.length!==width*height*4)throw new Error('Invalid window image');const canvas=document.createElement('canvas');canvas.width=width;canvas.height=height;canvas.getContext('2d').putImageData(new ImageData(new Uint8ClampedArray(bytes),width,height),0,0);return canvas.toDataURL();}
 icon(bytes,width,height){if(!this.favicon){this.favicon=document.querySelector('link[rel~="icon"]');this.ownedIcon=!this.favicon;if(this.favicon)this.originalIcon=this.favicon.getAttribute('href');else this.favicon=document.head.appendChild(Object.assign(document.createElement('link'),{rel:'icon'}));}this.favicon.href=this.image(bytes,width,height);}
 close(){this.observer.disconnect();if(this.ownedIcon)this.favicon?.remove();else if(this.favicon){if(this.originalIcon===null)this.favicon.removeAttribute('href');else this.favicon.setAttribute('href',this.originalIcon);}this.stage.before(this.canvas);this.stage.remove();}
}
