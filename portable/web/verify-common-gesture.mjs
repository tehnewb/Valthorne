import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir, writeFile} from 'node:fs/promises';
import {browserTestOptions} from './browser-test-options.mjs';

const output = new URL('./build/verification/common-gesture.json', import.meta.url);
const messages = [], errors = [];
const result = {fixture: 'common-gesture', status: 'running', phase: 'startup'};
const browser = await chromium.launch(browserTestOptions);
let page;
const has = prefix => messages.some(message => message.startsWith(prefix));
async function message(prefix) {
    const deadline = Date.now() + 15000;
    while (!has(prefix)) {
        assert.deepEqual(errors, [], messages.join('\n'));
        if (Date.now() >= deadline) throw new Error(`Missing Java marker: ${prefix}\n${messages.join('\n')}`);
        await new Promise(resolve => setTimeout(resolve, 50));
    }
}

try {
    page = await browser.newPage({viewport: {width: 640, height: 480}});
    page.on('console', value => messages.push(value.text()));
    page.on('pageerror', error => errors.push(error.stack || String(error)));
    await page.addInitScript(() => {
        // Freeze only the test browser's animation scheduler. Real trusted input,
        // pointer-lock policy and the compiled Java runtime remain in control.
        const request = window.requestAnimationFrame.bind(window);
        const cancel = window.cancelAnimationFrame.bind(window);
        const pending = new Map();
        let next = 1;
        const probe = globalThis.gestureProbe = {held: false, delivered: 0, requests: [], pendingIO: []};
        function schedule(entry) {
            entry.native = request(time => {
                entry.native = null;
                if (probe.held) return;
                pending.delete(entry.id);
                probe.delivered++;
                entry.callback(time);
            });
        }
        window.requestAnimationFrame = callback => {
            const entry = {id: next++, callback, native: null};
            pending.set(entry.id, entry);
            if (!probe.held) schedule(entry);
            return entry.id;
        };
        window.cancelAnimationFrame = id => {
            const entry = pending.get(id);
            if (entry?.native != null) cancel(entry.native);
            pending.delete(id);
        };
        probe.hold = () => {
            probe.held = true;
            for (const entry of pending.values()) {
                if (entry.native != null) cancel(entry.native);
                entry.native = null;
            }
            return probe.delivered;
        };
        probe.resume = () => {
            probe.held = false;
            for (const entry of pending.values()) if (entry.native == null) schedule(entry);
        };
        probe.releaseIO = () => {
            probe.blockIO = false;
            for (const resolve of probe.pendingIO.splice(0)) resolve();
        };
        const lock = Element.prototype.requestPointerLock;
        Element.prototype.requestPointerLock = function(...args) {
            probe.requests.push({active: navigator.userActivation.isActive,
                focused: document.hasFocus(), delivered: probe.delivered, atMs: performance.now()});
            return lock.apply(this, args);
        };
    });
    await page.goto(process.env.TEST_URL || 'http://127.0.0.1:8095');
    await message('COMMON_GESTURE_READY');
    await message('GESTURE_FRAME ');
    result.phase = 'freeze-animation-and-ui-io';
    result.frozenDelivery = await page.evaluate(() => {
        const transaction = valthorneFiles.transaction.bind(valthorneFiles);
        valthorneFiles.transaction = async (...args) => {
            if (gestureProbe.blockIO) await new Promise(resolve => gestureProbe.pendingIO.push(resolve));
            return transaction(...args);
        };
        gestureProbe.blockIO = true;
        return gestureProbe.hold();
    });
    const frozenFrame = messages.filter(value => value.startsWith('GESTURE_FRAME ')).at(-1);
    await page.keyboard.press('F8');
    await message('GESTURE_ARMED ');
    await page.mouse.click(220, 130);
    await message('GESTURE_CALLBACK_IO');
    await page.waitForFunction(() => gestureProbe.pendingIO.length > 0, null, {polling: 50, timeout: 5000});
    await page.keyboard.press('k');
    await page.mouse.wheel(0, 120);
    // Exceed transient activation's usual lifetime with RAF still suspended.
    // A next-frame callback implementation cannot pass the capture check below.
    await page.waitForTimeout(6100);
    result.suspended = await page.evaluate(() => ({
        delivered: gestureProbe.delivered, requests: gestureProbe.requests,
        locked: document.pointerLockElement?.id ?? null,
        pendingIO: gestureProbe.pendingIO.length,
        error: globalThis.valthorneError ?? valthorneHost.platform.captureError ?? null
    }));
    assert.equal(result.suspended.delivered, result.frozenDelivery, 'No animation callbacks during the held interval');
    assert.equal(messages.filter(value => value.startsWith('GESTURE_FRAME ')).at(-1), frozenFrame);
    assert.equal(result.suspended.error, null);
    assert.equal(result.suspended.locked, 'scene', 'Trusted Java UI callback must capture without another RAF');
    assert.equal(result.suspended.requests.length, 1);
    assert.equal(result.suspended.requests[0].active, true, 'Pointer-lock request must retain transient user activation');
    assert(result.suspended.pendingIO > 0, 'The Java UI callback must still be suspended in filesystem I/O');
    for (const marker of ['GESTURE_CLICK ', 'GESTURE_KEY', 'GESTURE_TEXT', 'GESTURE_SCROLL', 'GESTURE_ORDINARY_TASK']) {
        assert(!has(marker), `Callback reentry or premature task execution: ${marker}`);
    }
    result.phase = 'resume-ui-callback';
    await page.evaluate(() => gestureProbe.releaseIO());
    for (const marker of ['GESTURE_CLICK ', 'GESTURE_KEY', 'GESTURE_TEXT', 'GESTURE_SCROLL']) await message(marker);
    assert(!has('GESTURE_ORDINARY_TASK'), 'Input-only dispatch must not flush ordinary tasks');
    assert.equal(await page.evaluate(() => gestureProbe.delivered), result.frozenDelivery);
    assert.equal(messages.filter(value => value.startsWith('GESTURE_FRAME ')).at(-1), frozenFrame);
    result.phase = 'resume-animation';
    await page.evaluate(() => gestureProbe.resume());
    await message('GESTURE_ORDINARY_TASK');
    await page.waitForFunction(start => gestureProbe.delivered > start, result.frozenDelivery, {polling: 50});
    result.phase = 'input-shutdown';
    await page.keyboard.down('q');
    try {
        await page.waitForFunction(() => valthorneHost.closed || globalThis.valthorneError, null, {polling: 50});
    } finally {
        await page.keyboard.up('q');
    }
    assert.equal(await page.evaluate(() => globalThis.valthorneError), undefined, messages.join('\n'));
    assert.deepEqual(errors, [], messages.join('\n'));
    await message('COMMON_GESTURE_RETURNED');
    const validated = messages.find(value => value.startsWith('COMMON_GESTURE_VALIDATED checks='));
    assert(Number(validated?.split('=')[1]) >= 30, 'All Java lifecycle and input assertions must execute');
    result.status = 'passed';
    console.log('COMMON_GESTURE_BROWSER_VALIDATED ' + JSON.stringify(result.suspended));
} catch (error) {
    result.status = 'failed';
    result.failure = error.stack || String(error);
    if (page) result.browser = await page.evaluate(() => ({
        error: globalThis.valthorneError ?? null, requests: globalThis.gestureProbe?.requests,
        delivered: globalThis.gestureProbe?.delivered, closed: globalThis.valthorneHost?.closed
    })).catch(error => ({probeError: String(error)}));
    throw error;
} finally {
    result.messages = messages;
    result.errors = errors;
    await mkdir(new URL('./build/verification/', import.meta.url), {recursive: true});
    await writeFile(output, JSON.stringify(result, null, 2));
    await browser.close();
}
