import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";

const root = path.resolve(process.argv[2]);
const revisions = {
    bgfx: ["https://github.com/LWJGL-CI/bgfx.git", "f564ae8bf92cd2ac27d95b12408bd68be3ddf955"],
    bx: ["https://github.com/bkaradzic/bx.git", "f3a9adb603c5677731e7cf10622fa16e1dfe9ea5"],
    bimg: ["https://github.com/bkaradzic/bimg.git", "9bfbd6493b48a15238f2d1374a718ec026524515"],
    freetype: ["https://github.com/freetype/freetype.git", "526ec5c47b9ebccc4754c85ac0c0cdf7c85a5e9b"],
    harfbuzz: ["https://github.com/harfbuzz/harfbuzz.git", "bf8929fbfb623703cf1522b372cab80002c17c95"],
    msdfgen: ["https://github.com/Chlumsky/msdfgen.git", "6574da1310df433c97ca0fddcab7e463c31e58f8"],
    yoga: ["https://github.com/facebook/yoga.git", "042f5013152eb81c1552dec945b88f7b95ca350f"],
    openal: ["https://github.com/kcat/openal-soft.git", "dc7d7054a5b4f3bec1dc23a42fd616a0847af948"],
};
function run(args, cwd) {
    const result = spawnSync("git", args, { cwd, encoding: "utf8" });
    if (result.error) throw result.error;
    if (result.status !== 0) throw new Error(result.stderr || "git failed");
    return result.stdout.trim();
}
mkdirSync(root, { recursive: true });
for (const [name, [url, revision]] of Object.entries(revisions)) {
    const directory = path.join(root, name);
    if (!existsSync(path.join(directory, ".git"))) {
        run(["clone", "--filter=blob:none", "--no-checkout", url, directory], root);
        run(["fetch", "origin", revision, "--depth=1"], directory);
        run(["checkout", "--detach", revision], directory);
    }
    if (run(["status", "--porcelain"], directory)) throw new Error("Refusing modified native source: " + directory);
    if (run(["rev-parse", "HEAD"], directory) !== revision) {
        run(["fetch", "origin", revision, "--depth=1"], directory);
        run(["checkout", "--detach", revision], directory);
    }
}
const licenses = path.join(root, "valthorne-android-licenses");
mkdirSync(licenses, { recursive: true });
for (const name of ["bgfx", "bx", "bimg", "yoga"])
    writeFileSync(path.join(licenses, name + ".txt"), readFileSync(path.join(root, name, "LICENSE")));
for (const [name, file] of [["freetype", "freetype/docs/FTL.TXT"], ["harfbuzz", "harfbuzz/COPYING"], ["msdfgen", "msdfgen/LICENSE.txt"]])
    writeFileSync(path.join(licenses, name + ".txt"), readFileSync(path.join(root, file)));
writeFileSync(path.join(licenses, "openal-soft.txt"), readFileSync(path.join(root, "openal/COPYING")));
writeFileSync(path.join(licenses, "openal-soft-source.txt"), "OpenAL Soft is dynamically linked and unmodified.\nCorresponding source: https://github.com/kcat/openal-soft/tree/" + revisions.openal[1] + "\nAndroid build integration: android/src/main/cpp/CMakeLists.txt in the Valthorne source distribution.\n");
const stb = readFileSync(path.join(root, "bimg/3rdparty/stb/stb_image_resize2.h"), "utf8");
const licenseStart = stb.lastIndexOf("ALTERNATIVE A - MIT License");
if (licenseStart < 0) throw new Error("Review STB resize licensing before updating Android dependencies");
writeFileSync(path.join(licenses, "stb-image-resize.txt"), stb.slice(licenseStart, stb.lastIndexOf("*/")).trim() + "\n");
writeFileSync(path.join(root, "valthorne-android-sources.json"), JSON.stringify(revisions, null, 2) + "\n");
