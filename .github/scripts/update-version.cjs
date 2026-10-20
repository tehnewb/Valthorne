'use strict';

const { syncDocumentation } = require('./sync-documentation-version.cjs');

/** Selects one explicit version label; ambiguous labels fail before any write. */
function bumpKind(pull) {
    const labels = new Set(pull.labels.map(label => label.name));
    const patch = labels.has('version:patch');
    const hotfix = labels.has('version:hotfix');
    const feature = labels.has('version:feature');
    if (Number(patch) + Number(hotfix) + Number(feature) > 1)
        throw new Error(`PR #${pull.number} has conflicting version labels.`);
    return feature ? 'feature' : patch ? 'patch' : hotfix ? 'hotfix' : null;
}

/** Advances the selected component while preserving the first version number. */
function bumpVersion(version, kind) {
    if (!/^\d+\.\d+\.\d+\.\d+$/.test(version))
        throw new Error(`Expected a four-part version, received ${version}.`);
    const parts = version.split('.').map(part => BigInt(part));
    if (kind === 'feature') {
        parts[1] += 1n;
        parts[2] = 0n;
        parts[3] = 0n;
    } else if (kind === 'patch') {
        parts[2] += 1n;
        parts[3] = 0n;
    } else if (kind === 'hotfix') {
        parts[3] += 1n;
    } else {
        throw new Error(`Unknown bump kind: ${kind}.`);
    }
    return parts.join('.');
}

/** Plans all unseen merges in chronological order, including unlabeled no-ops. */
function planUpdate(properties, state, pulls) {
    if (!Number.isFinite(Date.parse(state.since)) || !Array.isArray(state.processed)
            || !state.processed.every(Number.isSafeInteger))
        throw new Error('Invalid version processing state.');
    const matches = [...properties.matchAll(/^version=(\d+\.\d+\.\d+\.\d+)\r?$/gm)];
    if (matches.length !== 1) throw new Error('Expected exactly one four-part version property.');
    const initial = matches[0][1];
    const previous = state.version || initial;
    if (!/^\d+\.\d+\.\d+\.\d+$/.test(previous)) throw new Error('Invalid recorded version.');
    const previousParts = previous.split('.').map(BigInt);
    const currentParts = initial.split('.').map(BigInt);
    let majorRelease = currentParts[0] > previousParts[0]
            || (currentParts[0] === previousParts[0] && currentParts[1] > previousParts[1]);
    const firstDifference = currentParts.findIndex((part, index) => part !== previousParts[index]);
    if (firstDifference !== -1 && currentParts[firstDifference] < previousParts[firstDifference])
        throw new Error('The project version cannot move backwards.');
    let version = initial;
    const processed = new Set(state.processed);
    const pending = pulls.filter(pull => pull.merged_at && pull.base.ref === 'main'
            && Date.parse(pull.merged_at) >= Date.parse(state.since) && !processed.has(pull.number));
    pending.sort((left, right) => Date.parse(left.merged_at) - Date.parse(right.merged_at) || left.number - right.number);
    const bumps = [];
    const urgent = [];
    for (const pull of pending) {
        const kind = bumpKind(pull);
        if (pull.labels.some(label => label.name === 'release:urgent')) {
            if (!kind) throw new Error(`Urgent PR #${pull.number} needs a version:feature, version:patch, or version:hotfix label.`);
            urgent.push(pull.number);
        }
        if (kind) {
            version = bumpVersion(version, kind);
            if (kind === 'feature') majorRelease = true;
            bumps.push(pull.number);
        }
        processed.add(pull.number);
    }
    return {
        properties: properties.replace(/^version=\d+\.\d+\.\d+\.\d+/m, `version=${version}`),
        state: { since: state.since, version, processed: [...processed].sort((left, right) => left - right) },
        changed: pending.length > 0 || version !== previous || !state.version,
        version,
        bumps,
        release: majorRelease || urgent.length > 0 ? {
            version,
            reason: majorRelease ? 'major-or-feature-version' : 'urgent-fix',
            pullRequests: bumps
        } : null
    };
}

/** Reconciles merged PRs and atomically commits the version and processing ledger. */
async function updateVersion({ github, context, core }) {
    const repo = context.repo;
    // Reconcile every merge rather than relying on delivery order or queued runs.
    for (let attempt = 0; attempt < 5; attempt++) {
        const { data: head } = await github.rest.git.getRef({ ...repo, ref: 'heads/main' });
        const parent = head.object.sha;
        const read = async path => {
            const { data } = await github.rest.repos.getContent({ ...repo, path, ref: parent });
            if (Array.isArray(data) || data.encoding !== 'base64') throw new Error(`Cannot read ${path}.`);
            return Buffer.from(data.content, 'base64').toString('utf8');
        };
        const [properties, stateText, pulls, treeResult] = await Promise.all([
            read('gradle.properties'),
            read('.github/version-state.json'),
            github.paginate(github.rest.pulls.list, { ...repo, state: 'closed', base: 'main', per_page: 100 }),
            github.rest.git.getTree({ ...repo, tree_sha: parent, recursive: '1' })
        ]);
        if (treeResult.data.truncated) throw new Error('Repository file listing was truncated.');
        const plan = planUpdate(properties, JSON.parse(stateText), pulls);
        const paths = treeResult.data.tree.filter(entry => entry.type === 'blob' && entry.path.endsWith('.md')
                && !entry.path.startsWith('.github/') && entry.path !== 'AGENTS.md').map(entry => entry.path);
        const documentation = await Promise.all(paths.map(async path => {
            const original = await read(path);
            const content = syncDocumentation(original, plan.version);
            return content === original ? null : { path, content };
        }));
        const updatedDocumentation = documentation.filter(Boolean);
        if (!plan.changed && updatedDocumentation.length === 0) {
            core.info('Version and repository documentation are up to date.');
            return;
        }
        const { data: parentCommit } = await github.rest.git.getCommit({ ...repo, commit_sha: parent });
        const files = [...updatedDocumentation];
        if (plan.changed) files.push({ path: '.github/version-state.json', content: JSON.stringify(plan.state, null, 2) + '\n' });
        if (plan.properties !== properties) files.push({ path: 'gradle.properties', content: plan.properties });
        if (plan.release) files.push({ path: '.github/release-request.json', content: JSON.stringify(plan.release, null, 2) + '\n' });
        const { data: tree } = await github.rest.git.createTree({
            ...repo,
            base_tree: parentCommit.tree.sha,
            tree: files.map(file => ({ ...file, mode: '100644', type: 'blob' }))
        });
        const message = plan.bumps.length
            ? `chore: bump version to ${plan.version}\n\nProcessed PRs: ${plan.bumps.map(number => `#${number}`).join(', ')}`
            : `chore: synchronize version ${plan.version} and documentation`;
        const { data: commit } = await github.rest.git.createCommit({ ...repo, message, tree: tree.sha, parents: [parent] });
        try {
            await github.rest.git.updateRef({ ...repo, ref: 'heads/main', sha: commit.sha, force: false });
            core.info(`Version ${plan.version}; recorded ${plan.state.processed.length} merged PRs.`);
            return;
        } catch (failure) {
            // Retry only an actual competing main update; permission failures must surface.
            if (failure.status !== 422) throw failure;
            const { data: latest } = await github.rest.git.getRef({ ...repo, ref: 'heads/main' });
            if (latest.object.sha === parent) throw failure;
        }
    }
    throw new Error('Main changed repeatedly; rerun the workflow to reconcile pending merges.');
}

module.exports = { bumpKind, bumpVersion, planUpdate, updateVersion };
