'use strict';

const assert = require('node:assert/strict');
const { test } = require('node:test');
const { validateRelease } = require('./release-policy.cjs');

test('accepts only a matching release snapshot and known reason', () => {
    const request = { version: '1.0.0.1', reason: 'urgent-fix', pullRequests: [42] };
    assert.equal(validateRelease('version=1.0.0.1\r\n', request), '1.0.0.1');
    assert.throws(() => validateRelease('version=1.0.0.2\n', request), /exact/);
    assert.throws(() => validateRelease('version=1.0.0.1\n', { ...request, reason: 'routine' }));
    assert.throws(() => validateRelease('version=1.0.0.1\n', { ...request, pullRequests: [] }));
    assert.equal(validateRelease('version=2.0.0.0\n', {
        version: '2.0.0.0', reason: 'major-or-feature-version', pullRequests: []
    }), '2.0.0.0');
});
