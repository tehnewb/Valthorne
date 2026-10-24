import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

// Rebuild only the release runtime: no examples, tests, npm scripts or docs.
const root = path.dirname(fileURLToPath(import.meta.url));
const workspace = path.resolve(root, "../build/browser-tools");
const sdk = path.resolve(process.argv[2] || process.env.EMSDK || "");
if (!existsSync(path.join(sdk, "upstream/emscripten/emscripten-version.txt")))
    throw new Error("Pass the Emscripten 4.0.23 SDK directory.");
if (JSON.parse(readFileSync(path.join(sdk, "upstream/emscripten/emscripten-version.txt"), "utf8")) !== "4.0.23")
    throw new Error("Rebuild browser physics with pinned Emscripten 4.0.23.");
const python =
    process.env.EMSDK_PYTHON ||
    (process.platform === "win32"
        ? path.join(
              sdk,
              "python",
              readdirSync(path.join(sdk, "python")).find((name) =>
                  existsSync(path.join(sdk, "python", name, "python.exe")),
              ),
              "python.exe",
          )
        : "python3");
const environment = { ...process.env, EMSDK: sdk, EMSDK_PYTHON: python };
function run(command, args, cwd = root) {
    const result = spawnSync(command, args, { cwd, env: environment, stdio: "inherit" });
    if (result.error) throw result.error;
    if (result.status !== 0) throw new Error(`${command} failed (${result.status})`);
}
mkdirSync(workspace, { recursive: true });
for (const [name, url, revision] of [
    ["jolt-js", "https://github.com/jrouwe/JoltPhysics.js.git", "cccf9d568e8cbae1569ba8e1a24de731591ba0a8"],
    ["jolt-core", "https://github.com/jrouwe/JoltPhysics.git", "6f8bc72fd5fc6a58a52a7fbfe49ca7b5170b4990"],
]) {
    const destination = path.join(workspace, name);
    if (!existsSync(path.join(destination, ".git")))
        run("git", ["clone", "--filter=blob:none", "--no-checkout", url, destination]);
    const revisionResult = spawnSync("git", ["rev-parse", "HEAD"], { cwd: destination, encoding: "utf8" });
    if (revisionResult.status !== 0 || revisionResult.stdout.trim() !== revision) {
        run("git", ["fetch", "origin", revision, "--depth=1"], destination);
        run("git", ["checkout", "--detach", revision], destination);
    }
}
const bindings = path.join(workspace, "jolt-js");
const core = path.join(workspace, "jolt-core");
const generated = path.join(workspace, "jolt-generated");
const output = path.join(root, "src/main/resources/native");
mkdirSync(generated, { recursive: true });
mkdirSync(output, { recursive: true });
function replace(source, before, after) {
    if (!source.includes(before)) throw new Error("Review the pinned Jolt browser adaptation: " + before);
    return source.replace(before, after);
}
let header = readFileSync(path.join(bindings, "JoltJS.h"), "utf8").replaceAll("\r\n", "\n");
header = header.replace(
    '#include "Jolt/Core/JobSystemThreadPool.h"',
    '#include "Jolt/Core/JobSystemSingleThreaded.h"\n#include "Jolt/Physics/Constraints/ContactConstraintManager.h"',
);
header = replace(
    header,
    `\t\t// Create a factory
\t\tFactory::sInstance = new Factory();

\t\t// Register all Jolt physics types
\t\tRegisterTypes();`,
    `        // Worlds share one registration table; only the final owner unregisters.
        if (sInstances++ == 0) {
            Factory::sInstance = new Factory();
            RegisterTypes();
        }`,
);
header = header.replaceAll("TempAllocatorImpl *", "TempAllocatorImplWithMallocFallback *");
header = replace(
    header,
    "new TempAllocatorImpl(inSettings.mTempAllocatorSize)",
    "new TempAllocatorImplWithMallocFallback(inSettings.mTempAllocatorSize)",
);
const schedulerStart = header.indexOf("\t\t// Limit to 16 threads");
const schedulerEnd = header.indexOf("\t\t// Check required objects", schedulerStart);
if (schedulerStart < 0 || schedulerEnd < 0) throw new Error("Review Jolt job system initialization.");
header =
    header.slice(0, schedulerStart) +
    "        mJobSystem = new JobSystemSingleThreaded(cMaxPhysicsJobs);\n\n" +
    header.slice(schedulerEnd);
header = header.replaceAll("JobSystemThreadPool *", "JobSystemSingleThreaded *");
header = replace(
    header,
    `\t\tdelete Factory::sInstance;
\t\tFactory::sInstance = nullptr;
\t\tUnregisterTypes();`,
    `        if (--sInstances == 0) {
            UnregisterTypes();
            delete Factory::sInstance;
            Factory::sInstance = nullptr;
        }`,
);
header = replace(
    header,
    "void\t\t\t\t\tStep(float inDeltaTime, int inCollisionSteps)",
    "uint32 Step(float inDeltaTime, int inCollisionSteps)",
);
header = replace(
    header,
    "\t\tmPhysicsSystem->Update(inDeltaTime, inCollisionSteps, mTempAllocator, mJobSystem);",
    "        return static_cast<uint32>(mPhysicsSystem->Update(inDeltaTime, inCollisionSteps, mTempAllocator, mJobSystem));",
);
header = replace(
    header,
    "\tTempAllocatorImplWithMallocFallback *\t\tmTempAllocator = nullptr;",
    "    inline static uint32 sInstances = 0;\n\tTempAllocatorImplWithMallocFallback *\t\tmTempAllocator = nullptr;",
);
header = replace(
    header,
    "\t/// Access to the physics system",
    `    static uint32 sGetMaxContacts() { return PhysicsSystem::cMaxContactConstraintsLimit; }
    static uint32 sGetMaxPairs() { return ContactConstraintManager::cMaxBodyPairsLimit; }

\t/// Access to the physics system`,
);
let idl = readFileSync(path.join(bindings, "JoltJS.idl"), "utf8");
idl = replace(
    idl,
    "\tvoid Step(float inDeltaTime, long inCollisionSteps);",
    "\tunsigned long Step(float inDeltaTime, long inCollisionSteps);\n\tstatic unsigned long sGetMaxContacts();\n\tstatic unsigned long sGetMaxPairs();",
);
idl = replace(
    idl,
    "\tattribute unsigned long mMaxWorkerThreads;",
    "\tattribute unsigned long mMaxWorkerThreads;\n\tattribute unsigned long mTempAllocatorSize;",
);
writeFileSync(path.join(generated, "JoltJS.h"), header);
writeFileSync(path.join(generated, "jolt.idl"), idl);
run(python, [path.join(sdk, "upstream/emscripten/tools/webidl_binder.py"), "jolt.idl", "glue"], generated);
const glue = path.join(generated, "glue.cpp");
writeFileSync(glue, readFileSync(glue, "utf8").replaceAll("thread_local", ""));
const cmake = process.env.CMAKE || "cmake";
const build = path.join(workspace, "jolt-build");
const configure = [
    "-S",
    path.join(root, "native/physics"),
    "-B",
    build,
    "-G",
    "Ninja",
    "-DCMAKE_TOOLCHAIN_FILE=" + path.join(sdk, "upstream/emscripten/cmake/Modules/Platform/Emscripten.cmake"),
    "-DCMAKE_BUILD_TYPE=Distribution",
    "-DJOLT_SOURCE=" + core,
    "-DJOLT_GENERATED=" + generated,
    "-DJOLT_BINDINGS=" + bindings,
    "-DJOLT_OUTPUT=" + output,
];
if (process.env.NINJA) configure.push("-DCMAKE_MAKE_PROGRAM=" + process.env.NINJA);
run(
    cmake,
    configure.map((value) => value.replaceAll("\\", "/")),
);
run(cmake, ["--build", build, "--target", "valthorne-jolt", "--parallel", process.env.JOLT_BUILD_JOBS || "4"]);
const license = [bindings, core]
    .map((directory) => readFileSync(path.join(directory, "LICENSE"), "utf8").replaceAll("\r\n", "\n").trimEnd())
    .join("\n\n");
writeFileSync(path.join(root, "src/main/resources/META-INF/licenses/jolt.txt"), license + "\n");
