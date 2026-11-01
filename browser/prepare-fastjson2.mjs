import { readFile, writeFile, mkdir } from "node:fs/promises";
import path from "node:path";

// Adapt the pinned upstream sources, retaining fastjson2's lexer and tree reader.
// This embedded browser variant supports JSON trees; JVM bean binding, JSONPath
// references, and Unsafe access are deliberately outside that platform boundary.
const [input, output] = process.argv.slice(2);
if (!input || !output) throw new Error("Supply fastjson2 source and output directories");
const prefix = "com/alibaba/fastjson2/";

function replace(source, before, after, expected = 1) {
    if (source.split(before).length - 1 !== expected)
        throw new Error("Review the pinned fastjson2 browser adaptation: " + before);
    return source.replaceAll(before, after);
}

function method(source, signature, body) {
    const start = source.indexOf(signature);
    if (start < 0 || source.indexOf(signature, start + signature.length) >= 0)
        throw new Error("Review the fastjson2 method boundary: " + signature);
    const opening = source.indexOf("{", start);
    const closing = source.indexOf("\n    }", opening);
    if (opening < 0 || closing < 0) throw new Error("Missing fastjson2 method body: " + signature);
    return source.slice(0, opening + 1) + "\n" + body + "\n    }" + source.slice(closing + 6);
}

for (const type of ["JSONReader", "JSONReaderUTF16", "util/JDKUtils"]) {
    let source = (await readFile(path.join(input, prefix, type + ".java"), "utf8")).replaceAll("\r\n", "\n");
    if (type === "util/JDKUtils") {
        // Retain the upstream signatures for linkage, without probing JVM memory.
        const start = source.indexOf("    static {");
        if (start < 0 || !source.includes("unsafe.arrayBaseOffset(char[].class)"))
            throw new Error("Review fastjson2 platform initialization");
        source = source.slice(0, start) + `    static {
        UNSAFE = null;
        ARRAY_BYTE_BASE_OFFSET = -1;
        ARRAY_CHAR_BASE_OFFSET = -1;
        ANDROID_SDK_INT = -1;
    }
}
`;
    } else if (type === "JSONReaderUTF16") {
        source = replace(source, "BIG_ENDIAN ?", "false ?");
        source = method(source, "static int getInt(char[] chars, int offset)", `        int value = 0;
        for (int i = 0; i < 4; i++) {
            int c = chars[offset + i];
            if (c > 255) return 0;
            value |= c << (i * 8);
        }
        return value;`);
        source = method(source, "static long getLong(char[] chars, int offset)", `        long value = 0;
        for (int i = 0; i < 8; i++) {
            long c = chars[offset + i];
            if (c > 255) return 0;
            value |= c << (i * 8);
        }
        return value;`);
    } else {
        source = method(source, "protected final String readStringNotMatch()", '        throw new JSONException(info("expected string"));');
        source = method(source, "public <T> T read(Class<T> type)", '        throw new JSONException("Typed object binding is unavailable in the browser tree reader");');
        source = replace(source, "innerMap = TypeUtils.getInnerMap(object);", "innerMap = object;");
        source = replace(source, "                object = new JSONObject();", "                object = new LinkedHashMap();");
        source = replace(source, "list = new JSONArray();", "list = new ArrayList();");
        source = replace(source, "new JSONArray(2) : new JSONArray(1)", "new ArrayList(2) : new ArrayList(1)");
        source = replace(source, "addResolveTask(object, name, JSONPath.of(readReference()));\n                        val = null;", 'throw new JSONException("Object references are disabled");');
        source = replace(source, "val = ObjectReaderImplObject.INSTANCE.readObject(this, null, null, 0);", "val = readObject();");
        source = replace(source, "val = JSONPath.of(readReference());", 'throw new JSONException("Object references are disabled");');
    }
    const destination = path.join(output, prefix, type + ".java");
    await mkdir(path.dirname(destination), { recursive: true });
    await writeFile(destination, "// Derived from Alibaba fastjson2 (Apache-2.0); browser adaptations: Valthorne.\n" + source);
}
