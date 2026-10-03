'use strict';

const assert = require('node:assert/strict');
const { test } = require('node:test');
const { syncDocumentation } = require('./sync-documentation-version.cjs');

test('synchronizes badges, prose, Gradle coordinates, and only Valthorne Maven dependencies', () => {
    const source = 'Valthorne 1.0.0.0\r\nCurrent Valthorne version: 1.0.0.0\r\nThese examples target **1.0.0.0**. In 1.0.0.0, use this workaround.\r\n'
        + 'Version 1.0.0.0 https://img.shields.io/badge/version-1.0.0.0-blue\r\n'
        + "implementation 'io.github.tehnewb:Valthorne:1.0.0.0'\r\n"
        + '<dependency><groupId>io.github.tehnewb</groupId><artifactId>Valthorne</artifactId><version>1.0.0.0</version></dependency>\r\n'
        + '<dependency><groupId>other</groupId><artifactId>Other</artifactId><version>1.0.0.0</version></dependency>\r\n'
        + '127.0.0.1 and Java 25 and plugin 3.14.1 and 1.0-SNAPSHOT\r\n';
    const result = syncDocumentation(source, '1.0.0.3');
    assert.equal((result.match(/1\.0\.0\.3/g) || []).length, 8);
    assert.ok(result.includes('<artifactId>Other</artifactId><version>1.0.0.0</version>'));
    assert.ok(result.includes('127.0.0.1 and Java 25 and plugin 3.14.1 and 1.0-SNAPSHOT\r\n'));
    assert.equal(syncDocumentation(result, '1.0.0.3'), result);
    assert.throws(() => syncDocumentation(source, 'invalid'));
});
