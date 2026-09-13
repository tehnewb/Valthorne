import { AudioStreams } from './audio-streams.js';
// Web Audio implementation of the engine's internal source/buffer operations.
export class BrowserAudio {
    constructor(host){this.host=host;this.sources=new Map();this.buffers=new Map();this.next=1;this.streams=new AudioStreams();}
    context(){return this.host.platform.audio??=(new AudioContext());}
    source(id){const source=this.sources.get(id);if(!source)throw new Error('Disposed audio source');this.advance(source);return source;}
    createSource(){const gain=this.context().createGain();gain.connect(this.context().destination);const id=this.next++;this.sources.set(id,{gain,queue:[],nodes:[],processed:0,offset:0,time:this.context().currentTime,pitch:1,loop:false,state:0x1011,static:false});return id;}
    createBuffer(){const id=this.next++;this.buffers.set(id,null);return id;}
    upload(id,format,bytes,rate){
        if(!this.buffers.has(id))throw new Error('Disposed audio buffer');
        const channels=format>=0x1102?2:1,bits=(format&1)?16:8,frames=bytes.length/(channels*(bits/8));
        if(!Number.isInteger(frames)||frames<=0)throw new Error('Invalid PCM frame count');
        const buffer=this.context().createBuffer(channels,frames,rate),view=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
        for(let channel=0;channel<channels;channel++){const out=buffer.getChannelData(channel);for(let frame=0;frame<frames;frame++){const i=frame*channels+channel;out[frame]=bits===16?view.getInt16(i*2,true)/32768:(bytes[i]-128)/128;}}
        this.buffers.set(id,buffer);
    }
    advance(s){
        const now=this.context().currentTime;if(s.state!==0x1012){s.time=now;return;}
        s.offset+=(now-s.time)*s.pitch;s.time=now;
        if(s.static){const duration=this.buffers.get(s.queue[0])?.duration||0;if(s.loop&&duration)s.offset%=duration;else if(s.offset>=duration){s.state=0x1014;s.offset=0;this.cancel(s);}return;}
        while(s.processed<s.queue.length){const duration=this.buffers.get(s.queue[s.processed])?.duration||0;if(s.offset<duration)break;s.offset-=duration;s.processed++;}
        if(s.processed===s.queue.length){s.state=0x1014;s.offset=0;this.cancel(s);}
    }
    cancel(s){for(const node of s.nodes){node.stop();node.disconnect();}s.nodes.length=0;}
    schedule(s){
        this.cancel(s);if(s.state!==0x1012)return;
        let when=this.context().currentTime;const first=s.static?0:s.processed;
        for(let i=first;i<s.queue.length;i++){
            const buffer=this.buffers.get(s.queue[i]);if(!buffer)continue;
            const offset=i===first?s.offset:0;if(offset>=buffer.duration)continue;
            const node=this.context().createBufferSource();node.buffer=buffer;node.playbackRate.value=s.pitch;node.loop=s.static&&s.loop;node.connect(s.gain);node.start(when,offset);s.nodes.push(node);when+=(buffer.duration-offset)/s.pitch;
        }
    }
    command(id,action){const s=this.source(id);
        if(action==='play'){if(s.state===0x1012)s.offset=0;if(s.state===0x1014){s.offset=0;s.processed=0;}s.state=0x1012;}
        else if(action==='pause'){if(s.state===0x1012)s.state=0x1013;}
        else {s.state=action==='rewind'?0x1011:0x1014;s.offset=0;s.processed=action==='rewind'?0:s.queue.length;}
        s.time=this.context().currentTime;this.schedule(s);
    }
    integer(id,name,value){const s=this.source(id);if(name===0x1009){this.cancel(s);s.queue=value?[value]:[];s.processed=0;s.offset=0;s.static=!!value;}else if(name===0x1007){s.loop=!!value;this.schedule(s);}else throw new Error('Unsupported audio integer parameter');}
    scalar(id,name,value){const s=this.source(id);if(name===0x100a)s.gain.gain.value=value;else if(name===0x1003){s.pitch=value;this.schedule(s);}else if(name===0x1024){s.offset=value;s.time=this.context().currentTime;this.schedule(s);}else throw new Error('Unsupported audio scalar parameter');}
    query(id,name){const s=this.source(id);switch(name){case 0x1010:return s.state;case 0x1015:return s.queue.length;case 0x1016:return s.processed;case 0x1007:return +s.loop;case 0x1003:return s.pitch;case 0x100a:return s.gain.gain.value;case 0x1024:return s.offset;default:throw new Error('Unsupported audio query');}}
    queue(id,buffer){const s=this.source(id);s.static=false;s.queue.push(buffer);this.schedule(s);}
    unqueue(id){const s=this.source(id);if(!s.processed)throw new Error('No processed audio buffer');s.processed--;return s.queue.shift();}
    deleteSource(id){const s=this.sources.get(id);if(!s)return;this.cancel(s);s.gain.disconnect();this.sources.delete(id);}
    deleteBuffer(id){this.buffers.delete(id);}
    async decode(bytes){
        const buffer=await this.context().decodeAudioData(bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength));
        const channels=buffer.numberOfChannels;if(channels>2)throw new Error('PCM must be mono or stereo');
        const pcm=new Uint8Array(buffer.length*channels*2),view=new DataView(pcm.buffer);
        for(let c=0;c<channels;c++){const data=buffer.getChannelData(c);for(let i=0;i<data.length;i++)view.setInt16((i*channels+c)*2,Math.max(-32768,Math.min(32767,Math.round(data[i]*32768))),true);}
        return {pcm,channels,rate:buffer.sampleRate,duration:buffer.duration};
    }
    close(){this.streams.close();for(const id of this.sources.keys())this.deleteSource(id);this.buffers.clear();}
}
