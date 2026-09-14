import test from 'node:test';
import assert from 'node:assert/strict';
import {loadPhysicsRuntime} from './public/physics-runtime.js';

test('disabled exports neither import nor initialize physics', async () => {
    assert.equal(await loadPhysicsRuntime(false, () => { throw new Error('Must not import'); }), null);
});
test('physics remains enabled by default', async () => {
    let initialized = 0;
    const runtime = {};
    assert.equal(await loadPhysicsRuntime(undefined, async () => ({default: () => {initialized++; return runtime;}})), runtime);
    assert.equal(initialized, 1);
});
test('enabled runtime startup errors propagate', async () => {
    await assert.rejects(loadPhysicsRuntime(true, async () => {throw new Error('startup');}), /startup/);
});
