'use strict';

const assert = require('node:assert/strict');
const { test } = require('node:test');
const { bumpVersion, planUpdate, updateVersion } = require('./update-version.cjs');

const state = { since: '2026-10-03T20:53:41Z', version: '1.0.0.0', processed: [] };
const pull = (number, label, merged = '2026-10-04T00:00:00Z') => ({
    number, merged_at: merged, base: { ref: 'main' }, labels: label ? [{ name: label }] : []
});

test('hotfix increments the fourth component; patch resets it', () => {
    assert.equal(bumpVersion('1.2.3.9', 'hotfix'), '1.2.3.10');
    assert.equal(bumpVersion('1.2.3.9', 'patch'), '1.2.4.0');
    assert.throws(() => bumpVersion('1.2.3-SNAPSHOT', 'patch'));
});

test('reconciles out-of-order merges, preserves CRLF, and is idempotent', () => {
    const pulls = [pull(3, 'version:hotfix', '2026-10-04T00:00:03Z'), pull(1, 'version:hotfix'),
        pull(2, 'version:patch', '2026-10-04T00:00:02Z'), pull(4, null)];
    const plan = planUpdate('# comment\r\nversion=1.0.0.0\r\n', state, pulls);
    assert.equal(plan.version, '1.0.1.1');
    assert.equal(plan.properties, '# comment\r\nversion=1.0.1.1\r\n');
    assert.deepEqual(plan.state.processed, [1, 2, 3, 4]);
    assert.equal(planUpdate(plan.properties, plan.state, pulls).changed, false);
});

test('ignores old merges, unmerged PRs, other branches, and already processed PRs', () => {
    const other = pull(4, 'version:patch');
    other.base.ref = 'development';
    const plan = planUpdate('version=1.0.0.0\n', { ...state, processed: [1] }, [
        pull(1, 'version:patch'), pull(2, 'version:patch', '2026-01-01T00:00:00Z'),
        pull(3, 'version:patch', null), other
    ]);
    assert.equal(plan.changed, false);
});

test('rejects conflicting labels and malformed properties before committing', () => {
    const conflict = pull(1, 'version:patch');
    conflict.labels.push({ name: 'version:hotfix' });
    assert.throws(() => planUpdate('version=1.0.0.0\n', state, [conflict]), /conflicting/);
    assert.throws(() => planUpdate('version=1.0.0\n', state, []));
    assert.throws(() => planUpdate('version=1.0.0.0\nversion=1.0.0.1\n', state, []));
});

test('routine patches and hotfixes do not publish; urgent fixes do', () => {
    assert.equal(planUpdate('version=1.0.0.0\n', state, [pull(1, 'version:hotfix')]).release, null);
    assert.equal(planUpdate('version=1.0.0.0\n', state, [pull(1, 'version:patch')]).release, null);
    const urgent = pull(2, 'version:hotfix');
    urgent.labels.push({ name: 'release:urgent' });
    assert.equal(planUpdate('version=1.0.0.0\n', state, [urgent]).release.version, '1.0.0.1');
    urgent.labels = [{ name: 'release:urgent' }];
    assert.throws(() => planUpdate('version=1.0.0.0\n', state, [urgent]), /needs a version/);
});

test('first or second component increases publish even without pending PRs', () => {
    for (const version of ['2.0.0.0', '1.1.0.0']) {
        const plan = planUpdate(`version=${version}\n`, state, []);
        assert.equal(plan.release.reason, 'major-or-feature-version');
        assert.equal(plan.changed, true);
        assert.equal(planUpdate(plan.properties, plan.state, []).release, null);
    }
    assert.equal(planUpdate('version=1.0.1.0\n', state, []).release, null);
    assert.throws(() => planUpdate('version=0.9.0.0\n', state, []), /backwards/);
});

test('commits both files atomically and retries concurrent main updates', async () => {
    let attempts = 0;
    let commits = 0;
    const github = {
        paginate: async () => [pull(1, 'version:hotfix')],
        rest: {
            pulls: { list() {} },
            repos: { getContent: async ({ path }) => ({ data: { encoding: 'base64', content: Buffer.from(
                path === 'gradle.properties' ? 'version=1.0.0.0\n' : JSON.stringify(state)
            ).toString('base64') } }) },
            git: {
                getTree: async () => ({ data: { tree: [], truncated: false } }),
                getRef: async () => ({ data: { object: { sha: attempts ? 'new-head' : 'head' } } }),
                getCommit: async () => ({ data: { tree: { sha: 'tree' } } }),
                createTree: async ({ tree }) => {
                    assert.deepEqual(tree.map(file => file.path), ['.github/version-state.json', 'gradle.properties']);
                    return { data: { sha: 'updated-tree' } };
                },
                createCommit: async () => ({ data: { sha: `commit-${++commits}` } }),
                updateRef: async ({ force }) => {
                    assert.equal(force, false);
                    if (attempts++ === 0) throw Object.assign(new Error('race'), { status: 422 });
                }
            }
        }
    };
    await updateVersion({ github, context: { repo: { owner: 'owner', repo: 'repo' } }, core: { info() {} } });
    assert.equal(attempts, 2);
});

test('repairs stale documentation without bumping the version or processing a merge', async () => {
    let writes = 0;
    const contents = { 'gradle.properties': 'version=1.0.0.0\n', '.github/version-state.json': JSON.stringify(state),
        'README.md': 'Valthorne 0.9.0.0\n' };
    const github = {
        paginate: async () => [],
        rest: {
            pulls: { list() {} },
            repos: { getContent: async ({ path }) => ({ data: { encoding: 'base64',
                content: Buffer.from(contents[path]).toString('base64') } }) },
            git: {
                getRef: async () => ({ data: { object: { sha: 'head' } } }),
                getTree: async () => ({ data: { tree: [{ type: 'blob', path: 'README.md' }], truncated: false } }),
                getCommit: async () => ({ data: { tree: { sha: 'tree' } } }),
                createTree: async ({ tree }) => {
                    assert.deepEqual(tree, [{ path: 'README.md', content: 'Valthorne 1.0.0.0\n', mode: '100644', type: 'blob' }]);
                    return { data: { sha: 'new-tree' } };
                },
                createCommit: async () => ({ data: { sha: 'new-commit' } }),
                updateRef: async () => { writes++; }
            }
        }
    };
    await updateVersion({ github, context: { repo: { owner: 'owner', repo: 'repo' } }, core: { info() {} } });
    assert.equal(writes, 1);
});
