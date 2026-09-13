// Incremental PCM16, MPEG and Vorbis playback. Encoded memory sources are borrowed;
// URL sources use a reader and never call Response.arrayBuffer().
const scripts = new Map();
async function codec(format) {
    const name = format === 'OGG' ? 'ogg-vorbis-decoder' : 'mpg123-decoder';
    if (!scripts.has(name)) scripts.set(name, new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.charset = 'utf-8';
        script.src = `vendor/audio/${name}.min.js`;
        script.onload = resolve; script.onerror = () => reject(new Error(`Cannot load ${name}`));
        document.head.append(script);
    }).catch(error => { scripts.delete(name); throw error; }));
    await scripts.get(name);
    const Decoder = globalThis[name][format === 'OGG' ? 'OggVorbisDecoder' : 'MPEGDecoder'];
    const decoder = new Decoder(); try { await decoder.ready; return decoder; } catch(error) { try { decoder.free(); } catch {} throw error; }
}

class ByteReader {
    constructor(path, bytes) { this.path = path; this.bytes = bytes; this.at = 0; this.totalRead = 0; this.prefix = null; this.chunk = null; this.offset = 0; this.peak = 0; }
    async start() {
        if (this.bytes) return;
        this.abort = new AbortController();
        const timeout=setTimeout(()=>this.abort.abort(new Error('Audio request timed out')),30000);
        let response;try{response = await fetch(this.path, { signal: this.abort.signal });}finally{clearTimeout(timeout);}
        if (!response.ok || !response.body) throw new Error(`Audio HTTP ${response.status}: ${this.path}`);
        try { this.reader = response.body.getReader({ mode: 'byob' }); this.byob = true; }
        catch { this.reader = response.body.getReader(); }
    }
    async read(size, allowEnd = false) {
        const out = new Uint8Array(size); let count = 0;
        while (count < size) {
            if (this.prefix?.length) {
                const n = Math.min(size - count, this.prefix.length);
                out.set(this.prefix.subarray(0, n), count); this.prefix = this.prefix.subarray(n); count += n; continue;
            }
            if (this.bytes) {
                const n = Math.min(size - count, this.bytes.length - this.at);
                out.set(this.bytes.subarray(this.at, this.at + n), count); this.at += n; count += n; break;
            }
            if (!this.chunk || this.offset === this.chunk.length) {
                const timeout=setTimeout(()=>this.abort.abort(new Error('Audio stream stalled')),30000);
                let result;try{result = await this.reader.read(this.byob ? new Uint8Array(65536) : undefined);}finally{clearTimeout(timeout);}
                this.chunk = result.value; this.offset = 0;
                if (result.done && !this.chunk?.length) break;
                this.peak = Math.max(this.peak, this.chunk.length);
                this.totalRead += this.chunk.length;
            }
            const n = Math.min(size - count, this.chunk.length - this.offset);
            out.set(this.chunk.subarray(this.offset, this.offset + n), count); this.offset += n; count += n;
        }
        if (count !== size && !allowEnd) throw new Error('Truncated audio stream');
        return count === size ? out : out.subarray(0, count);
    }
    async skip(size) { while (size) { const n = Math.min(65536, size); await this.read(n); size -= n; } }
    close() { this.abort?.abort(); this.reader?.cancel().catch(() => {}); this.chunk = this.prefix = this.bytes = null; }
}

export class AudioStreams {
    constructor() { this.active = new Set(); this.peakPcmBytes = 0; this.peakInputBytes = 0; }
    async open(path, bytes) {
        const stream = new PcmStream(this, path, bytes); this.active.add(stream);
        try { await stream.start(); return stream; } catch (error) { stream.close(); throw error; }
    }
    async probe(path, bytes) {
        const stream = await this.open(path, bytes);
        try {
            if (stream.format === 'WAV') {const result=stream.metadata();if(stream.duration<=15&&stream.dataLength<=8*1024*1024)result.pcm=await stream.read(stream.dataLength);return result;}
            let frames = 0,retained=0,chunks=[];
            while (await stream.next()) {
                frames += stream.pcm.length / (stream.channels * 2);
                if(chunks){retained+=stream.pcm.length;if(retained>8*1024*1024||frames/stream.rate>15){chunks=null;retained=0;}else{chunks.push(stream.pcm);this.peakPcmBytes=Math.max(this.peakPcmBytes,retained+stream.pcm.length*2);}}
                stream.pcm = null;
            }
            if (!frames || !stream.rate) throw new Error('Audio contains no decodable samples');
            stream.duration = frames / stream.rate;
            const result=stream.metadata();if(chunks){result.pcm=new Uint8Array(retained);let at=0;for(const chunk of chunks){result.pcm.set(chunk,at);at+=chunk.length;}}return result;
        } finally { stream.close(); }
    }
    close() { for (const stream of this.active) stream.close(); }
}

class PcmStream {
    constructor(owner, path, bytes) { this.owner = owner; this.path = path; this.bytes = bytes; this.closed = false; }
    metadata() { return { format: this.format, channels: this.channels, rate: this.rate, duration: this.duration, offset: this.dataOffset || 0, length: this.dataLength ?? this.bytes?.length ?? this.reader.totalRead, pcm: null }; }
    async start() {
        this.reader = new ByteReader(this.path, this.bytes); await this.reader.start();
        this.pcm = null; this.cursor = 0; this.eof = false; this.position = 0;
        const header = await this.reader.read(12), text = String.fromCharCode(...header);
        this.reader.prefix = header;
        this.format = text.startsWith('RIFF') && text.slice(8) === 'WAVE' ? 'WAV' : text.startsWith('OggS') ? 'OGG' : 'MP3';
        if (this.format !== 'WAV') {
            if(this.format==='MP3'&&text.startsWith('ID3')){await this.reader.read(10);const size=((header[6]&127)<<21)|((header[7]&127)<<14)|((header[8]&127)<<7)|(header[9]&127);await this.reader.skip(size+(header[5]&16?10:0));}
            this.decoder = await codec(this.format); return;
        }
        await this.reader.read(12); let offset = 12;
        for (;;) {
            const chunk = await this.reader.read(8), view = new DataView(chunk.buffer), size = view.getUint32(4, true), tag = view.getUint32(0, true); offset += 8;
            if (tag === 0x20746d66) {
                if (size < 16) throw new Error('Invalid WAV format');
                const data = await this.reader.read(16), fmt = new DataView(data.buffer);
                this.channels = fmt.getUint16(2, true); this.rate = fmt.getUint32(4, true);
                if (fmt.getUint16(0, true) !== 1 || fmt.getUint16(14, true) !== 16 || this.channels < 1 || this.channels > 2 || !this.rate) throw new Error('WAV streaming requires mono/stereo PCM16');
                await this.reader.skip(size - 16 + (size & 1));
            } else if (tag === 0x61746164) {
                if (!this.rate || size % (this.channels * 2)) throw new Error('Invalid WAV data');
                this.remaining = this.dataLength = size; this.dataOffset = offset; this.duration = size / (this.channels * 2 * this.rate); return;
            } else await this.reader.skip(size + (size & 1));
            offset += size + (size & 1);
        }
    }
    async next() {
        if (this.closed) throw new Error('Sound stream is closed');
        for (;;) {
            if (this.eof) { this.pcm = null; return false; }
            if (this.format === 'WAV') {
                const n = Math.min(this.remaining, 16384); this.remaining -= n;
                this.eof = !this.remaining; this.pcm = await this.reader.read(n); this.cursor = 0;
                this.measure(); return !!n;
            }
            let input;
            if (this.format === 'OGG') {
                const header = await this.reader.read(27, true);
                if (!header.length) input = null;
                else {
                    if (header.length !== 27 || String.fromCharCode(...header.subarray(0, 4)) !== 'OggS') throw new Error('Invalid Ogg page');
                    const segments = await this.reader.read(header[26]);
                    const payload = await this.reader.read(segments.reduce((a, b) => a + b, 0));
                    input = new Uint8Array(27 + segments.length + payload.length);
                    input.set(header); input.set(segments, 27); input.set(payload, 27 + segments.length);
                }
            } else input = await this.reader.read(1024, true);
            let decoded;
            if (!input?.length) { this.eof = true; decoded = this.format === 'OGG' ? await this.decoder.flush() : null; }
            else decoded = await this.decoder.decode(input);
            if (decoded?.errors?.length) throw new Error(`Audio decoder: ${decoded.errors[0].message || JSON.stringify(decoded.errors[0])}`);
            if (!decoded?.samplesDecoded) continue;
            const channels = decoded.channelData.length, frames = decoded.samplesDecoded;
            if (channels < 1 || channels > 2 || (this.rate && this.rate !== decoded.sampleRate) || (this.channels && this.channels !== channels)) throw new Error('Unsupported audio channel/rate change');
            // A corrupt page must not grow the retained PCM queue without limit.
            if (frames * channels * 4 > 16 * 1024 * 1024) throw new Error('Audio page exceeds decoder working-set budget');
            this.channels = channels; this.rate = decoded.sampleRate;
            this.pcm = new Uint8Array(frames * channels * 2); this.cursor = 0;
            const view = new DataView(this.pcm.buffer);
            for (let c = 0; c < channels; c++) for (let i = 0; i < frames; i++) view.setInt16((i * channels + c) * 2, Math.max(-32768, Math.min(32767, Math.round(decoded.channelData[c][i] * 32768))), true);
            this.measure(frames * channels * 4); return true;
        }
    }
    measure(decodedBytes = 0) {
        this.owner.peakPcmBytes = Math.max(this.owner.peakPcmBytes, (this.pcm?.length || 0) + decodedBytes);
        this.owner.peakInputBytes = Math.max(this.owner.peakInputBytes, this.reader.peak);
    }
    async read(size) {
        if (this.closed) throw new Error('Sound stream is closed');
        if (!Number.isInteger(size) || size < 0 || size > 8 * 1024 * 1024) throw new Error('Invalid audio read size');
        if (!size) return new Uint8Array(0);
        try {
            if (!this.channels && !await this.next()) return new Uint8Array(0);
            const frame = this.channels * 2, out = new Uint8Array(size - size % frame); let at = 0;
            while (at < out.length) {
                if ((!this.pcm || this.cursor === this.pcm.length) && !await this.next()) break;
                const n = Math.min(out.length - at, this.pcm.length - this.cursor);
                out.set(this.pcm.subarray(this.cursor, this.cursor + n), at); this.cursor += n; at += n;
            }
            this.position += at / frame; return out.subarray(0, at);
        } catch (error) { this.close(); throw error; }
    }
    async seek(seconds) {
        if (this.closed) throw new Error('Sound stream is closed');
        if (!Number.isFinite(seconds)) throw new Error('Invalid seek time');
        try {
            this.reader.close(); this.decoder?.free(); this.decoder = null;
            await this.start(); if (!this.channels && !await this.next()) return;
            let frames = Math.floor(Math.max(0, seconds) * this.rate);
            while (frames > 0) { const data = await this.read(Math.min(frames, 4096) * this.channels * 2); if (!data.length) break; frames -= data.length / (this.channels * 2); }
        } catch (error) { this.close(); throw error; }
    }
    close() {
        if (this.closed) return; this.closed = true; this.reader?.close(); this.decoder?.free();
        this.decoder = this.pcm = this.bytes = null; this.owner.active.delete(this);
    }
}
