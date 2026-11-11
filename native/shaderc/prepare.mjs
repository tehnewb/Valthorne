import { createHash } from "node:crypto";
import { chmodSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { gunzipSync } from "node:zlib";

const directory = path.dirname(fileURLToPath(import.meta.url));
const output = path.resolve(directory, "../../build/generated/shaderc-resources");
const manifest = JSON.parse(readFileSync(path.join(directory, "provenance.json"), "utf8"));
const digest = (bytes) => createHash("sha256").update(bytes).digest("hex");
for (const resource of manifest.resources) {
    const destination = path.join(output, resource.path);
    if (existsSync(destination) && digest(readFileSync(destination)) === resource.sha256) continue;
    let bytes;
    if (resource.url) {
        const response = await fetch(resource.url, { signal: AbortSignal.timeout(120000) });
        if (!response.ok) throw new Error(`Could not fetch ${resource.path}: HTTP ${response.status}`);
        bytes = Buffer.from(await response.arrayBuffer());
    } else {
        bytes = gunzipSync(readFileSync(path.join(directory, resource.archive)));
    }
    if (digest(bytes) !== resource.sha256) throw new Error("Shader compiler checksum mismatch: " + resource.path);
    mkdirSync(path.dirname(destination), { recursive: true });
    writeFileSync(destination, bytes);
}
const metadata = path.join(output, "META-INF/valthorne-shaderc.json");
mkdirSync(path.dirname(metadata), { recursive: true });
writeFileSync(metadata, JSON.stringify(manifest, null, 2) + "\n");
const os = { win32: "windows", linux: "linux", darwin: "macosx" }[process.platform];
const architecture = { x64: "x64", arm64: "arm64", arm: "arm32" }[process.arch];
if (!os || !architecture) throw new Error("No packaged shader compiler for this build host");
const executable = path.join(output, "valthorne/shaderc", os, architecture, process.platform === "win32" ? "shaderc.exe" : "shaderc");
if (process.platform !== "win32") chmodSync(executable, 0o700);
const version = spawnSync(executable, ["--version"], { encoding: "utf8", timeout: 10000, windowsHide: true });
if (version.error || version.status !== 0 || !version.stdout.includes("version " + manifest.compilerVersion + "."))
    throw new Error("The pinned shader compiler cannot run on this host: " + (version.error || version.stderr || version.stdout));
