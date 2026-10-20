'use strict';

/** Updates explicit Valthorne references without changing other dependency versions. */
function syncDocumentation(text, version) {
    if (!/^\d+\.\d+\.\d+\.\d+$/.test(version)) throw new Error('Invalid Valthorne documentation version.');
    const number = '\\d+\\.\\d+\\.\\d+\\.\\d+';
    const patterns = [
        `((?:io\\.github\\.tehnewb:)?Valthorne[: ]+)${number}`,
        `(Valthorne\\s+\\*\\*)${number}`,
        `(\\*\\*)${number}(?=\\*\\*)`,
        `((?:\\b[Ii]n|\\breleased|\\b[Vv]ersion)\\s*:?\\s+)${number}`,
        `(shields\\.io/badge/version-)${number}`
    ];
    for (const pattern of patterns)
        text = text.replace(new RegExp(pattern, 'g'), (_, prefix) => prefix + version);
    return text.replace(/<dependency>[\s\S]*?<\/dependency>/g, dependency => {
        if (!/<groupId>\s*io\.github\.tehnewb\s*<\/groupId>/.test(dependency)
                || !/<artifactId>\s*Valthorne\s*<\/artifactId>/.test(dependency)) return dependency;
        return dependency.replace(/(<version>)\d+\.\d+\.\d+\.\d+(<\/version>)/g, `$1${version}$2`);
    });
}

/** Updates tracked wiki Markdown files in place, preserving their line endings. */
function syncWiki(directory, version) {
    const fs = require('node:fs');
    const path = require('node:path');
    const { execFileSync } = require('node:child_process');
    const files = execFileSync('git', ['ls-files', '-z', '*.md'], { cwd: directory, encoding: 'utf8' }).split('\0').filter(Boolean);
    let changed = 0;
    for (const file of files) {
        const location = path.join(directory, file);
        const original = fs.readFileSync(location, 'utf8');
        const updated = syncDocumentation(original, version);
        if (updated === original) continue;
        fs.writeFileSync(location, updated);
        changed++;
    }
    return changed;
}

module.exports = { syncDocumentation, syncWiki };

if (require.main === module) {
    const fs = require('node:fs');
    const properties = fs.readFileSync('gradle.properties', 'utf8');
    const match = properties.match(/^version=(\d+\.\d+\.\d+\.\d+)\r?$/m);
    if (!match) throw new Error('Missing canonical project version.');
    console.log(`Updated ${syncWiki(process.argv[2], match[1])} wiki pages to ${match[1]}.`);
}
