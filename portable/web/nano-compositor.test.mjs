import test from 'node:test';
import assert from 'node:assert/strict';
import {BrowserNano} from './public/nano-backend.js';

test('vector passes submit an owned snapshot and release it once', () => {
    const events=[];
    const frame={close(){events.push('close');}};
    const nano=new BrowserNano({graphics:{composite(value){assert.equal(value,frame);events.push('submit');}}});
    nano.contexts.set(1,{dirty:true,canvas:{transferToImageBitmap(){events.push('snapshot');return frame;}}});
    nano.end(1);nano.end(1);
    assert.deepEqual(events,['snapshot','submit','close']);
    assert.equal(nano.get(1).dirty,false);
});

test('vector snapshots are released if composition fails', () => {
    let closed=false;
    const nano=new BrowserNano({graphics:{composite(){throw new Error('upload failed');}}});
    nano.contexts.set(1,{dirty:true,canvas:{transferToImageBitmap(){return {close(){closed=true;}};}}});
    assert.throws(()=>nano.end(1),/upload failed/);
    assert.equal(closed,true);
});

test('fallback clears only submitted pixels and preserves drawing state', () => {
    const events=[];
    const canvas={width:80,height:40};
    const nano=new BrowserNano({graphics:{composite(value){assert.equal(value,canvas);events.push('submit');}}});
    const ctx={save(){events.push('save');},resetTransform(){events.push('reset');},
        clearRect(...rect){assert.deepEqual(rect,[0,0,80,40]);events.push('clear');},restore(){events.push('restore');}};
    nano.contexts.set(1,{dirty:true,canvas,ctx});
    nano.end(1);
    assert.deepEqual(events,['submit','save','reset','clear','restore']);
});
