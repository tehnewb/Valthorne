/** Lazily owns one isolated portable shader compiler per application session. */
export class BrowserShaders {
    constructor() {
        this.loading = null;
        this.compiler = null;
        this.messages = [];
        this.messageLength = 0;
        this.closed = false;
    }

    async load() {
        if (this.closed) throw new Error("Shader compiler session is closed");
        if (!this.loading) {
            this.loading = (async () => {
                const { default: createCompiler } = await import("./native/shaderc.mjs");
                const message = (text) => {
                    const remaining = 65536 - this.messageLength;
                    if (remaining > 0) {
                        const clipped = String(text).slice(0, remaining);
                        this.messages.push(clipped);
                        this.messageLength += clipped.length;
                    }
                };
                const compiler = await createCompiler({ print: message, printErr: message });
                const headers = await Promise.all(["bgfx_shader.sh", "bgfx_compute.sh"].map(async (name) => {
                    const response = await fetch(new URL("./native/shaderc-include/" + name, import.meta.url));
                    if (!response.ok) throw new Error("Could not load standard shader include " + name);
                    return [name, new Uint8Array(await response.arrayBuffer())];
                }));
                if (this.closed) throw new Error("Shader compiler session closed during loading");
                compiler.FS.mkdir("/include");
                for (const [name, bytes] of headers) compiler.FS.writeFile("/include/" + name, bytes);
                this.compiler = compiler;
                return compiler;
            })().catch((error) => {
                this.loading = null;
                throw error;
            });
        }
        return this.loading;
    }

    async compile(source, varyings, type) {
        const compiler = await this.load();
        if (this.closed) throw new Error("Shader compiler session is closed");
        if (type !== "v" && type !== "f") throw new Error("WebGL 2 does not support compute shaders");
        this.messages.length = 0;
        this.messageLength = 0;
        try {
            compiler.FS.writeFile("/source.sc", source);
            compiler.FS.writeFile("/varying.def.sc", varyings);
            const status = compiler.callMain(["-f", "/source.sc", "-o", "/output.bin", "--type", type,
                "--platform", "asm.js", "--profile", "300_es", "--varyingdef", "/varying.def.sc", "-i", "/include"]);
            if (status !== 0) throw new Error(this.messages.join("\n"));
            return compiler.FS.readFile("/output.bin");
        } finally {
            for (const name of ["/source.sc", "/varying.def.sc", "/output.bin"])
                if (compiler.FS.analyzePath(name).exists) compiler.FS.unlink(name);
            this.messages.length = 0;
            this.messageLength = 0;
        }
    }

    close() {
        this.closed = true;
        this.compiler = null;
        this.loading = null;
        this.messages.length = 0;
        this.messageLength = 0;
    }
}
