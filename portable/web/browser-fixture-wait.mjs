import {mkdir, writeFile} from 'node:fs/promises';

const reports = new URL('./build/verification/', import.meta.url);

/** Read observable progress without advancing, pausing or altering the application. */
export async function fixtureState(page, timeoutMs = 10000) {
    return browserProbe(page.evaluate(() => {
        const host = globalThis.valthorneHost;
        return {
            ready: Boolean(globalThis.valthorneReady),
            closed: Boolean(host?.closed),
            renderedFrames: host?.frames ?? 0,
            animationSeconds: host?.time ?? 0,
            contextLost: Boolean(host?.contextLost),
            engineError: globalThis.valthorneError ?? null
        };
    }), timeoutMs);
}

async function browserProbe(operation, timeoutMs = 10000) {
    let timer;
    try {
        return await Promise.race([
            operation,
            new Promise((_, reject) => {
                timer = setTimeout(() => reject(new Error(`Browser did not respond to the progress probe within ${Math.ceil(timeoutMs)} ms`)), timeoutMs);
            })
        ]);
    } finally {
        clearTimeout(timer);
    }
}

/**
 * Wait for a fixture condition while requiring observable frame-loop progress.
 * Software rendering has a bounded two-minute budget; a stalled loop still fails
 * after ten seconds. Completion never substitutes for the caller's assertions.
 * A JSON report distinguishes application errors, inactivity and total timeout.
 */
export async function waitForFixtureCondition(page, condition, {
    fixture, phase = 'completion', messages = [], errors = [],
    timeoutMs = process.env.WEBGPU_SOFTWARE === '1' ? 120000 : 30000,
    idleTimeoutMs = 10000
}) {
    if (!/^[a-z0-9-]+$/.test(fixture) || !/^[a-z0-9-]+$/.test(phase)) {
        throw new Error('Fixture and phase need stable report names');
    }
    const started = performance.now();
    const deadline = started + timeoutMs;
    const report = {fixture, phase, softwareGpu: process.env.WEBGPU_SOFTWARE === '1',
        timeoutMs, idleTimeoutMs, status: 'waiting', samples: []};
    let progressedAt = started;
    let previous;
    const probeBudget = () => {
        const remaining = deadline - performance.now();
        if (remaining <= 0) {
            report.status = 'timeout-with-progress';
            throw new Error(`Fixture did not finish within ${timeoutMs} ms`);
        }
        return Math.min(10000, remaining);
    };
    try {
        while (true) {
            const now = performance.now();
            const state = await fixtureState(page, probeBudget());
            report.elapsedMs = Math.round(performance.now() - started);
            report.lastState = state;
            // Native Filament exposes rendered frames. The shared 2D backend uses
            // the host's animation clock; both remain unchanged after loop failure.
            const progress = state.renderedFrames > 0
                ? `render/${state.renderedFrames}` : `animation/${state.animationSeconds}`;
            if (progress !== previous) {
                previous = progress;
                progressedAt = now;
            }
            if (!report.samples.length || now - started - report.samples.at(-1).elapsedMs >= 1000) {
                report.samples.push({elapsedMs: report.elapsedMs, ...state});
            }
            if (errors.length || state.engineError || state.contextLost) {
                report.status = 'application-error';
                throw new Error(state.engineError || errors.join('\n') || 'Graphics context lost');
            }
            const conditionBudget = probeBudget();
            if (await browserProbe(page.evaluate(condition), conditionBudget)) {
                // The condition may become true after the preceding sample.
                // Capture completion itself, especially the final rendered frame.
                report.lastState = await fixtureState(page, probeBudget());
                report.elapsedMs = Math.round(performance.now() - started);
                probeBudget();
                if (errors.length || report.lastState.engineError || report.lastState.contextLost) {
                    report.status = 'application-error';
                    throw new Error(report.lastState.engineError || errors.join('\n') || 'Graphics context lost');
                }
                report.status = 'reached';
                return report.lastState;
            }
            if (state.closed) {
                report.status = 'closed-before-condition';
                throw new Error('Application closed before the required condition');
            }
            if (performance.now() - progressedAt >= idleTimeoutMs) {
                report.status = 'stalled';
                throw new Error(`No frame-loop progress for ${idleTimeoutMs} ms`);
            }
            if (performance.now() - started >= timeoutMs) {
                report.status = 'timeout-with-progress';
                throw new Error(`Fixture did not finish within ${timeoutMs} ms`);
            }
            await page.waitForTimeout(Math.min(500, probeBudget()));
        }
    } catch (error) {
        if (report.status === 'waiting') report.status = performance.now() >= deadline
            ? 'timeout-with-progress' : 'browser-error';
        report.failure = error.stack || String(error);
        throw new Error(`${fixture}/${phase}: ${report.status}; elapsed=${report.elapsedMs ?? 0}ms; `
            + `state=${JSON.stringify(report.lastState)}; ${error.message}`, {cause: error});
    } finally {
        report.messages = [...messages];
        report.errors = [...errors];
        await mkdir(reports, {recursive: true});
        await writeFile(new URL(`${fixture}-${phase}-timing.json`, reports), JSON.stringify(report, null, 2));
    }
}

/** Wait for normal application shutdown without shortening its frame sequence. */
export function waitForFixtureCompletion(page, options) {
    return waitForFixtureCondition(page, () => Boolean(globalThis.valthorneHost?.closed), options);
}
