import { chromium } from '@playwright/test';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
const browser = await chromium.launch({channel:process.env.BROWSER_CHANNEL || 'chrome',headless:true});
try {
 const page = await browser.newPage();
 await page.route('**/audio-test.html', route => route.fulfill({contentType:'text/html',body:'<!doctype html><title>Audio streaming verification</title>'}));
 await page.goto((process.env.TEST_URL || 'http://127.0.0.1:8095') + '/audio-test.html');
 const result = await page.evaluate(async () => {
  const {AudioStreams} = await import('./audio-streams.js'), streams = new AudioStreams(), results = [];
  for (const format of ['mp3','ogg']) {
   const path = `portable/compatibility/assets/audio/tone.${format}`, start = performance.now();
   const info = await streams.probe(path,null);
   if (Math.abs(info.duration-60)>.15 || info.channels!==2 || info.rate!==22050) throw new Error('Incorrect compressed metadata: '+JSON.stringify(info));
   const stream = await streams.open(path,null); let total=0,energy=0,earlyPeak=0;
   while(true) {
    const pcm = await stream.read(4096); if(!pcm.length)break;
    if(pcm.length%4)throw new Error('Partial stereo frame');
    total+=pcm.length; const view=new DataView(pcm.buffer,pcm.byteOffset,pcm.byteLength);
    for(let i=0;i<pcm.length;i+=2)energy+=Math.abs(view.getInt16(i,true));
    if(total<22050*4*10)earlyPeak=streams.peakPcmBytes;
   }
   if(Math.abs(total/(4*22050)-60)>.15 || !energy)throw new Error('Decoded duration/energy invalid');
   await stream.seek(30); const middle=await stream.read(4096);
   if(middle.length!==4096)throw new Error('Seek failed');
   await stream.seek(1000); if((await stream.read(4096)).length)throw new Error('Seek beyond EOF');
   await stream.seek(0); if((await stream.read(4096)).length!==4096)throw new Error('Rewind failed');
   stream.close();stream.close();
   try {await stream.read(4);throw new Error('Closed read accepted');}catch(error){if(!String(error).includes('closed'))throw error;}
   if(streams.active.size)throw new Error('Decoder leaked');
   results.push({format,decodedBytes:total,elapsedMs:performance.now()-start,earlyPeakPcmBytes:earlyPeak,peakPcmBytes:streams.peakPcmBytes,peakInputBytes:streams.peakInputBytes});
  }
  for(let i=0;i<12;i++){const stream=await streams.open('portable/compatibility/assets/audio/tone.mp3',null);await stream.read(4096);stream.close();}
  try {await streams.probe(null,new Uint8Array(30));throw new Error('Invalid data accepted');}catch(error){if(String(error).includes('accepted'))throw error;}
  if(streams.active.size)throw new Error('Failure/teardown leaked a decoder');
  return {results,activeDecoders:streams.active.size};
 });
 for(const item of result.results){assert(item.peakPcmBytes<2*1024*1024,JSON.stringify(item));assert(item.peakPcmBytes<=item.earlyPeakPcmBytes*2,'Retained PCM must not grow with track duration');}
 await mkdir('build/verification',{recursive:true});await writeFile('build/verification/audio-streaming.json',JSON.stringify(result,null,2));
 console.log('BOUNDED_AUDIO_STREAMING_VALIDATED '+JSON.stringify(result));
} finally {await browser.close();}
