'use strict';

/** Validates the exact development snapshot requested for automatic publication. */
function validateRelease(properties, request) {
    const matches = [...properties.matchAll(/^version=(\d+\.\d+\.\d+\.\d+)\r?$/gm)];
    if (matches.length !== 1 || matches[0][1] !== request.version)
        throw new Error('Release request must match the exact project version.');
    if (!['urgent-fix', 'major-or-feature-version'].includes(request.reason))
        throw new Error('Unrecognized automatic publication reason.');
    if (!Array.isArray(request.pullRequests) || !request.pullRequests.every(Number.isSafeInteger))
        throw new Error('Invalid release PR list.');
    if (request.reason === 'urgent-fix' && request.pullRequests.length === 0)
        throw new Error('Urgent releases require a recorded version-bumping PR.');
    return request.version;
}

module.exports = { validateRelease };

if (require.main === module) {
    const fs = require('node:fs');
    const version = validateRelease(fs.readFileSync('gradle.properties', 'utf8'),
        JSON.parse(fs.readFileSync('.github/release-request.json', 'utf8')));
    fs.appendFileSync(process.env.GITHUB_OUTPUT, `version=${version}\n`);
}
