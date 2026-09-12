import {drawingCanvas} from './canvas.js';
import {parse} from './vendor/opentype/opentype.module.js';
/** Browser TrueType/OpenType rasterization behind FontData's baked-atlas API. */
export class BrowserFonts {
 outline(bytes){return parse(Uint8Array.from(bytes).buffer);}
 constructor(){this.next=1;this.fonts=new Set();}
 async bake(bytes,size,start,count){
  const data=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength),tables=new Map();
  if(bytes.length<12)throw new Error('Invalid font header');
  const number=data.getUint16(4);if(12+number*16>bytes.length)throw new Error('Truncated font directory');
  for(let i=0;i<number;i++){const at=12+i*16,tag=String.fromCharCode(...bytes.subarray(at,at+4)),offset=data.getUint32(at+8),length=data.getUint32(at+12);if(offset+length>bytes.length)throw new Error('Truncated font table');tables.set(tag,offset);}
  const head=tables.get('head'),hhea=tables.get('hhea');if(head===undefined||hhea===undefined)throw new Error('Font requires head and hhea metrics');
  const units=data.getUint16(head+18),asc=data.getInt16(hhea+4),desc=data.getInt16(hhea+6),gap=data.getInt16(hhea+8),scale=size/(asc-desc);
  if(!Number.isFinite(scale)||scale<=0)throw new Error('Invalid font metrics');
  const name='valthorne-font-'+this.next++,face=new FontFace(name,bytes);await face.load();document.fonts.add(face);
  try{
   let side=1;while(side<Math.ceil(Math.sqrt(count)*size*2))side*=2;
   const canvas=drawingCanvas(side,side),ctx=canvas.getContext('2d',{willReadFrequently:true});ctx.font=`${units*scale}px ${name}`;ctx.fillStyle='white';ctx.textBaseline='alphabetic';ctx.fontKerning='none';
   const glyphs=[];let x=1,y=1,row=0;
   for(let i=0;i<count;i++){
    const character=String.fromCharCode(start+i),m=ctx.measureText(character),left=Math.floor(-m.actualBoundingBoxLeft),top=Math.floor(-m.actualBoundingBoxAscent),right=Math.ceil(m.actualBoundingBoxRight),bottom=Math.ceil(m.actualBoundingBoxDescent),w=right-left,h=bottom-top;
    if(x+w+1>side){x=1;y+=row+1;row=0;}if(y+h+1>side)throw new Error('Font atlas is full');
    if(w&&h)ctx.fillText(character,x-left,y-top);
    glyphs.push(x,y,x+w,y+h,left,top,right,bottom,m.width);x+=w+1;row=Math.max(row,h);
   }
   const rgba=ctx.getImageData(0,0,side,side).data;for(let i=0;i<rgba.length;i+=4)rgba[i]=rgba[i+1]=rgba[i+2]=255;
   const measure=drawingCanvas(1,1).getContext('2d');measure.font=ctx.font;measure.fontKerning='normal';canvas.width=canvas.height=1;
   const font={face,ctx:measure,side,pixels:new Uint8Array(rgba.buffer),glyphs:new Float32Array(glyphs),ascent:asc*scale,descent:desc*scale,lineHeight:(asc-desc+gap)*scale,scale,kerning:new Map()};this.fonts.add(font);return font;
  }catch(error){document.fonts.delete(face);throw error;}
 }
 kern(font,left,right){const key=left*65536+right;if(font.kerning.has(key))return font.kerning.get(key);const a=String.fromCharCode(left),b=String.fromCharCode(right),value=font.ctx.measureText(a+b).width-font.ctx.measureText(a).width-font.ctx.measureText(b).width;font.kerning.set(key,value);return value;}
 release(font){if(!this.fonts.delete(font))return;document.fonts.delete(font.face);font.ctx=null;font.pixels=null;font.glyphs=null;font.kerning.clear();}
 close(){for(const font of this.fonts)this.release(font);}
}
