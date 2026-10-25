import { readFile, writeFile, mkdir } from "node:fs/promises";
import path from "node:path";

// Shared algorithms remain the source of truth; only platform boundaries change.
export async function prepareBrowserLoaders(root, output) {
    const types = [
        "graphics/map/tiled/TiledXML", "graphics/map/tiled/TiledResolvers", "graphics/map/tiled/TileAnimationFrame",
        "graphics/map/tiled/TileDefinition", "graphics/map/tiled/TileSetData",
        "graphics/map/tiled/TiledMapData", "graphics/map/tiled/TiledTileMapLayer",
        "graphics/map/tiled/TiledObjectMapLayer", "graphics/map/tiled/TiledImageMapLayerData",
        "graphics/map/tiled/TiledObject", "graphics/map/tiled/FileSystemResolver",
        "graphics/map/ldtk/LdtkProject", "graphics/map/ldtk/LdtkResolvers", "graphics/map/ldtk/LdtkLoader", "graphics/map/tiled/TiledMapLoader",
        "asset/Assets", "graphics/font/FontData",
        "ui/behavior/file/FileOperations", "ui/behavior/file/FileCopyVisitor", "ui/behavior/DirectoryBrowserModel", "graphics/map/tiled/TiledDecoding", "ui/nodes/DirectoryTree",
        "ui/nodes/nano/NanoDirectoryTree", "ui/nodes/FileChooser",
        "ui/nodes/nano/NanoFileChooser", "ui/nodes/nano/NanoFileContextMenu",
    ];
    for (const type of types) {
        let source = (await readFile(path.join(root, "src/main/java/valthorne", type + ".java"), "utf8")).replaceAll("\r\n", "\n");
        source = source.replaceAll("javax.xml.stream.", "valthorne.browser.xml.");
        if (type.startsWith("graphics/map/")) {
            source = source.replace("import java.nio.file.Files;", "import java.nio.file.Files;\nimport valthorne.browser.BrowserAssets;");
            source = source.replaceAll("Files.readAllBytes(", "BrowserAssets.read(");
            source = source.replaceAll("BrowserAssets.read(Paths.get(path))", "BrowserAssets.read(path)");
            source = source.replaceAll("BrowserAssets.read(Path.of(path))", "BrowserAssets.read(path)");
            source = source.replaceAll("BrowserAssets.read(Paths.get(tmxFilePath))", "BrowserAssets.read(tmxFilePath)");
        }
        if (type === "graphics/map/tiled/FileSystemResolver") {
            const start = source.indexOf("        Objects.requireNonNull(dependencyPath");
            const end = source.lastIndexOf("\n    }");
            if (start < 0 || end < start) throw new Error("Review browser Tiled dependency resolution");
            source = source.slice(0, start) + "        return BrowserAssets.read(BrowserAssets.resolve(parentPath, dependencyPath));" + source.slice(end);
        }
        if (type === "graphics/map/ldtk/LdtkResolvers")
            source = source.replace("BrowserAssets.read(resolve(parentPath, dependencyPath))", "BrowserAssets.read(BrowserAssets.resolve(parentPath, dependencyPath))");
        if (type === "graphics/map/ldtk/LdtkProject") {
            source = source.replace("LdtkResolvers.resolve(parentPath, external).toString()", "BrowserAssets.resolve(parentPath, external)");
            source = source.replace("import java.nio.file.Files;", "import java.nio.charset.StandardCharsets;\nimport java.nio.file.Files;");
            source = source.replace("JSONReader.Context context =", "String json = new String(bytes, StandardCharsets.UTF_8);\n        JSONReader.Context context =");
            source = source.replaceAll("JSONReader.of(bytes, context)", "JSONReader.of(json, context)");
        }
        if (type === "graphics/map/tiled/TiledResolvers") {
            source = source.replace("import java.nio.file.Path;", "import java.nio.file.Path;\nimport valthorne.browser.BrowserAssets;");
            const start = source.indexOf("            Path dep = Paths.get(dependencyPath);");
            const end = source.indexOf("\n        }", start);
            if (start < 0 || end < start) throw new Error("Review browser map resolver adaptation: " + type);
            source = source.slice(0, start) + "            return BrowserAssets.resolve(basePath, dependencyPath);" + source.slice(end);
        }
        if (type === "graphics/map/ldtk/LdtkResolvers") {
            source = source.replace("resolve(parentPath, dependencyPath).toString()", "BrowserAssets.resolve(parentPath, dependencyPath)");
            const start = source.indexOf("        Path child = Path.of(dependencyPath);");
            const end = source.indexOf("\n    }", start);
            if (start < 0 || end < start) throw new Error("Review browser map resolver adaptation: " + type);
            source = source.slice(0, start) + "        return Path.of(BrowserAssets.resolve(parentPath, dependencyPath));" + source.slice(end);
        }
        if (type === "graphics/map/tiled/TiledXML") {
            source = source.replace("import java.nio.file.Path;", "import java.nio.file.Path;\nimport valthorne.browser.BrowserAssets;");
            const start = source.indexOf("        if (rel == null) return null;");
            const end = source.indexOf("\n    }", start);
            if (start < 0 || end < start) throw new Error("Review browser map resolver adaptation: " + type);
            source = source.slice(0, start) + "        return rel == null ? null : BrowserAssets.resolve(basePath, rel);" + source.slice(end);
        }
        if (type === "graphics/font/FontData") {
            source = source.replace(/import org\.lwjgl\.stb\.STBTTPack(?:Context|edchar);\n/g, "");
            source = source.replace("import java.nio.file.Files;", "import valthorne.browser.BrowserAssets;\nimport valthorne.browser.BrowserFontBaker;");
            const method = source.indexOf("    public static FontData load(byte[] fontBytes");
            const end = source.indexOf("\n    /**", method);
            if (method < 0 || end < 0) throw new Error("Review browser FontData loader adaptation");
            source = source.slice(0, method) + `    public static FontData load(byte[] fontBytes, int fontSize, int startChar, int numChars) {
        /*
         * One native packing operation produces the atlas and metrics. The baker
         * releases temporary native storage before exposing Java-owned results.
         */
        return BrowserFontBaker.bake(fontBytes, fontSize, startChar, numChars);
    }
` + source.slice(end);
            source = source.replaceAll("Files.readAllBytes(Path.of(path))", "BrowserAssets.read(path)");
        }
        if (type === "asset/Assets") {
            source = source.replace("import java.util.ArrayList;", "import java.util.ArrayList;\nimport java.util.Map;\nimport java.util.HashMap;\nimport java.util.HashSet;\nimport valthorne.browser.BrowserExecutor;\nimport valthorne.browser.BrowserAssetDisposal;");
            source = source.replaceAll("ConcurrentMap<", "Map<").replaceAll("new ConcurrentHashMap<>", "new HashMap<>");
            source = source.replaceAll("ConcurrentHashMap.newKeySet()", "new HashSet<>()");
            source = source.replaceAll("ConcurrentLinkedQueue<CompletableFuture<?>> futures = new ConcurrentLinkedQueue<>()", "ArrayList<CompletableFuture<?>> futures = new ArrayList<>()");
            source = source.replaceAll("ExecutorService", "BrowserExecutor");
            source = source.replaceAll("Executors.newVirtualThreadPerTaskExecutor()", "new BrowserExecutor()");
            source = source.replaceAll("for (AssetParameters parameters : prepared)", "for (AssetParameters parameters : new ArrayList<>(prepared))");
            source = source.replace("private static volatile BrowserExecutor service", "private static BrowserExecutor service");
            const lock = source.indexOf("    private static final Object SERVICE_LOCK");
            if (lock < 0) throw new Error("Review browser asset executor ownership");
            source = source.slice(0, source.lastIndexOf("    /**", lock)) + source.slice(source.indexOf("\n", lock) + 1);
            const ensure = source.indexOf("    private static BrowserExecutor ensureService() {");
            const ensureEnd = source.indexOf("\n    /**", ensure);
            if (ensure < 0 || ensureEnd < ensure) throw new Error("Review browser executor scheduling");
            source = source.slice(0, ensure) + `    private static BrowserExecutor ensureService() {
        /*
         * No browser coroutine can interleave this non-suspending state update.
         * Recreate scheduling after shutdown without JVM locks or volatile reads.
         */
        BrowserExecutor executor = service;
        if (executor == null || executor.isShutdown()) service = executor = createBrowserExecutor();
        return executor;
    }
` + source.slice(ensureEnd);
            source = source.replace("recreating it under the service lock when", "recreating it on the UI coroutine when");
            source = source.replace("absent, shut down, or terminated. Uses a volatile fast path for normal access.", "absent or shut down. This non-suspending operation needs no synchronization.");
            source = source.replace("Volatile owned virtual-thread executor", "Owned cooperative executor");
            source = source.replace(" * @author Albert Beaupre", " * <p>Browser loaders execute on cooperative UI coroutines. Custom resource-owning assets\n * implement AutoCloseable for cleanup; ordinary managed values need no cleanup\n * contract. Built-in assets use direct disposal calls. Arbitrary reflective dispose-method discovery is unavailable.</p>\n *\n * @author Albert Beaupre");

            const start = source.indexOf("    private static Throwable disposeAssetValue(Object asset) {");
            const end = source.indexOf("\n    /**", start);
            if (start < 0 || end < 0) throw new Error("Review browser asset disposal adaptation");
            source = source.slice(0, start) + `    private static Throwable disposeAssetValue(Object asset) {
        /*
         * Known asset resources use direct calls; custom browser resource owners
         * implement AutoCloseable. Managed values need no cleanup contract; arbitrary
         * reflective method discovery is absent.
         */
        return BrowserAssetDisposal.dispose(asset);
    }
` + source.slice(end);
            source = source.replaceAll("import java.lang.reflect.InvocationTargetException;\n", "").replaceAll("import java.lang.reflect.Method;\n", "");
            source = source.replaceAll("virtual-thread", "cooperative-coroutine").replaceAll("virtual thread", "cooperative coroutine");
            source = source.replaceAll("Concurrent registry", "UI-coroutine registry").replaceAll("Concurrent cache", "UI-coroutine cache").replaceAll("Concurrent set", "UI-coroutine set");
            source = source.replaceAll("thread-safe cache", "UI-thread-confined cache");
            source = source.replaceAll("Captures failures and unwraps reflective invocation causes so other assets can", "Captures cleanup failures so other assets can");
            source = source.replaceAll("otherwise a public no-argument\n     * dispose method discovered by reflection", "otherwise direct built-in asset lifecycle calls");
            source = source.replaceAll("All loading work runs on", "Loading is cooperatively scheduled on the browser UI thread through");
            source = source.replace("Releases a loaded value using AutoCloseable first, otherwise a public no-argument\n     * dispose method discovered by reflection. Values with neither contract are ignored.", "Releases AutoCloseable values and built-in engine asset types through direct calls.\n     * Custom resource-owning browser assets implement AutoCloseable for disposal.");
        }
        if (type === "ui/behavior/file/FileOperations") {
            source = source.replace("import java.io.IOException;", "import java.io.IOException;\nimport valthorne.browser.BrowserTrash;");
            source = source.replaceAll("import java.awt.Desktop;\n", "").replaceAll("import java.awt.Desktop.Action;\n", "");
            const start = source.indexOf("    public boolean isTrashSupported() {");
            const end = source.indexOf("    /**\n     * Permanently deletes", start);
            if (start < 0 || end < 0) throw new Error("Review browser trash adaptation");
            source = source.slice(0, start) + `    public boolean isTrashSupported() {
        return true;
    }

    /**
     * Moves a selection into recoverable browser workspace trash. This is a virtual
     * directory, not the operating system trash; BrowserTrash can restore its entries.
     * @param sources requested virtual file or directory selection
     * @throws IOException if an entry is protected or cannot be moved
     */
    public void moveToTrash(List<Path> sources) throws IOException {
        for (Path source : effectiveSelection(sources)) {
            requireRemovableEntry(source);
            BrowserTrash.move(source);
        }
    }

` + source.slice(end);
        }
        if (type.endsWith("DirectoryTree") || type.endsWith("DirectoryBrowserModel")) source = source.replaceAll("throw failure.getCause();", "throw new IOException(\"Could not enumerate browser directory\", failure);");
        if (type === "ui/behavior/file/FileOperations") {
            source = source.replace("whether the desktop advertises MOVE_TO_TRASH support", "whether recoverable browser workspace trash is available");
            source = source.replace("Files.getFileStore(source.getParent()).equals(Files.getFileStore(target.getParent()))", "source.getFileSystem().equals(target.getFileSystem())");
        }
        if (type === "ui/behavior/file/FileCopyVisitor") {
            source = source.replace("import java.io.IOException;", "import java.io.IOException;\nimport valthorne.browser.BrowserFiles;");
            source = source.replace("Files.setLastModifiedTime(", "BrowserFiles.setLastModifiedTime(");
        }
        if (type === "graphics/map/tiled/TiledDecoding") {
            source = source.replace("import java.util.Base64;", "import valthorne.browser.BrowserBase64;");
            source = source.replace("Base64.getMimeDecoder().decode(trimmed)", "BrowserBase64.decodeMime(trimmed)");
        }
        if (source.includes("java.nio.file.DirectoryIteratorException")) {
            source = source.replaceAll("import java.nio.file.DirectoryIteratorException;\n", "").replaceAll("java.nio.file.DirectoryIteratorException", "DirectoryIteratorException");
            source = source.replace("import java.io.IOException;", "import java.io.IOException;\nimport java.nio.file.DirectoryIteratorException;");
        }
        if (type.endsWith("FileChooser")) {
            source = source.replace("import java.nio.file.Path;", "import java.nio.file.Path;\nimport valthorne.browser.BrowserFiles;");
            source = source.replaceAll('Path.of(System.getProperty("user.home"))', 'BrowserFiles.home()');
            source = source.replaceAll('"This PC', '"Workspace');
        }
        if (type.endsWith("NanoFileContextMenu")) {
            source = source.replace("import java.io.IOException;", "import java.io.IOException;\nimport valthorne.browser.BrowserExecutor;");
            source = source.replace(/Thread\.ofVirtual\(\)\s*\.name\("ui-file-command"\)\s*\.start\(/, "new BrowserExecutor().execute(");
        }
        const destination = path.join(output, "valthorne", type + ".java");
        await mkdir(path.dirname(destination), { recursive: true });
        await writeFile(destination, source);
    }
}
