import { BrowserPhysics } from "./physics.mjs";
import createBgfx from "./native/bgfx.mjs";
import { BrowserYoga } from "./yoga.mjs";
import { BrowserAudio } from "./audio-backend.mjs";

let launching = false;

const keyNames = new Map(
    Object.entries({
        Space: 32,
        Quote: 39,
        Comma: 44,
        Minus: 45,
        Period: 46,
        Slash: 47,
        Semicolon: 59,
        Equal: 61,
        BracketLeft: 91,
        Backslash: 92,
        BracketRight: 93,
        Backquote: 96,
        IntlBackslash: 161,
        IntlRo: 162,
        Escape: 256,
        Enter: 257,
        Tab: 258,
        Backspace: 259,
        Insert: 260,
        Delete: 261,
        ArrowRight: 262,
        ArrowLeft: 263,
        ArrowDown: 264,
        ArrowUp: 265,
        PageUp: 266,
        PageDown: 267,
        Home: 268,
        End: 269,
        CapsLock: 280,
        ScrollLock: 281,
        NumLock: 282,
        PrintScreen: 283,
        Pause: 284,
        NumpadDecimal: 330,
        NumpadDivide: 331,
        NumpadMultiply: 332,
        NumpadSubtract: 333,
        NumpadAdd: 334,
        NumpadEnter: 335,
        NumpadEqual: 336,
        ShiftLeft: 340,
        ControlLeft: 341,
        AltLeft: 342,
        MetaLeft: 343,
        ShiftRight: 344,
        ControlRight: 345,
        AltRight: 346,
        MetaRight: 347,
        ContextMenu: 348,
    }),
);
for (let i = 0; i < 26; i++) keyNames.set("Key" + String.fromCharCode(65 + i), 65 + i);
for (let i = 0; i < 10; i++) {
    keyNames.set("Digit" + i, 48 + i);
    keyNames.set("Numpad" + i, 320 + i);
}
for (let i = 1; i <= 25; i++) keyNames.set("F" + i, 289 + i);

/** Owns DOM lifetime and scratch transfers to the real bgfx WebAssembly module. */
export class BrowserHost {
    constructor(canvas, native) {
        if (typeof FinalizationRegistry !== "function")
            throw new Error("This browser runtime requires native-allocation finalization support");
        this.yoga = new BrowserYoga();
        this.physics = new BrowserPhysics();
        this.audioBackend = new BrowserAudio(this);
        this.assets = new Map();
        this.fontOwners = new FinalizationRegistry((pointer) => native._valthorne_font_free(pointer));
        this.canvas = canvas;
        if (!canvas.id) {
            let id = "valthorne-canvas",
                suffix = 0;
            while (document.getElementById(id)) id = "valthorne-canvas-" + ++suffix;
            canvas.id = id;
        }
        if (document.getElementById(canvas.id) !== canvas)
            throw new Error("The application canvas must be attached with a unique ID");
        this.canvasSelector = "#" + CSS.escape(canvas.id);
        this.native = native;
        this.width = canvas.clientWidth || 960;
        this.height = canvas.clientHeight || 600;
        this.closing = false;
        this.visible = false;
        this.cursorX = 0;
        this.cursorY = 0;
        this.capsLock = false;
        this.hints = new Map();
        this.attributes = new Map();
        this.cursors = new Map();
        this.nextCursor = 0x1000000;
        this.dropBatches = new Map();
        this.nextDrop = 1;
        this.keys = new Set();
        this.buttons = new Set();
        this.frameRequest = 0;
        this.contextLost = false;
        this.frameWake = null;
        this.receiver = null;
        this.events = [];
        this.abort = null;
        this.observer = null;
        this.encoder = new TextEncoder();
        this.upload = 0;
        this.uploadCapacity = 0;
        this.words = native._malloc(128);
        this.values = native._malloc(65536);
        if (!this.words || !this.values) throw new Error("Could not allocate browser command staging");
        this.expectingPaste = false;
        this.pasteWaiters = [];
        this.lastRequestError = null;
        this.captures = new Map();
    }

    reserveUpload(length) {
        if (length <= this.uploadCapacity) return;
        const capacity = Math.max(length, this.uploadCapacity * 2, 65536);
        const pointer = this.native._malloc(capacity);
        if (!pointer) throw new Error("Could not allocate browser upload staging");
        if (this.upload) this.native._free(this.upload);
        this.upload = pointer;
        this.uploadCapacity = capacity;
    }
    nativeStringCommand(operation, words, values, text, count) {
        this.reserveUpload(text.length * 3 + 1);
        const destination = this.native.HEAPU8.subarray(this.upload, this.upload + this.uploadCapacity - 1);
        const encoded = this.encoder.encodeInto(text, destination);
        this.native.HEAPU8[this.upload + encoded.written] = 0;
        return this.nativeCommand(operation, words, values, null, 0, encoded.written + 1, count);
    }
    nativeCommand(operation, words, values, bytes, offset, length, count) {
        const native = this.native;
        this.reserveUpload(length);
        native.HEAPU32.set(words, this.words >>> 2);
        if (count) native.HEAPF32.set(values.subarray(0, count), this.values >>> 2);
        if (length && bytes) native.HEAPU8.set(bytes.subarray(offset, offset + length), this.upload);
        const result = native._valthorne_command(operation, this.words, this.values, this.upload, length);
        words.set(native.HEAPU32.subarray(this.words >>> 2, (this.words >>> 2) + words.length));
        return result;
    }

    async writeClipboard(text) {
        if (!navigator.clipboard?.writeText) throw new Error("Clipboard writing is unavailable");
        await navigator.clipboard.writeText(text);
    }
    readClipboard() {
        if (this.expectingPaste)
            return new Promise((resolve, reject) => {
                const waiter = { resolve, reject, timer: 0 };
                waiter.timer = setTimeout(() => {
                    const index = this.pasteWaiters.indexOf(waiter);
                    if (index >= 0) this.pasteWaiters.splice(index, 1);
                    if (navigator.clipboard?.readText) navigator.clipboard.readText().then(resolve, reject);
                    else reject(new Error("No browser paste event was delivered"));
                }, 1000);
                this.pasteWaiters.push(waiter);
            });
        if (!navigator.clipboard?.readText) return Promise.reject(new Error("Clipboard reading is unavailable"));
        return navigator.clipboard.readText();
    }
    async readAsset(path) {
        if (this.assets.has(path)) {
            const value = this.assets.get(path);
            return value instanceof Blob ? value.arrayBuffer() : value.slice(0);
        }
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(new Error("Asset request timed out")), 30000);
        const abort = () => controller.abort(new Error("Application session closed"));
        this.abort?.signal.addEventListener("abort", abort, { once: true });
        try {
            const response = await fetch(path, { signal: controller.signal });
            if (!response.ok) throw new Error("HTTP " + response.status + ": " + path);
            return await response.arrayBuffer();
        } finally {
            clearTimeout(timer);
            this.abort?.signal.removeEventListener("abort", abort);
        }
    }
    ownFont(pointer) {
        const owner = { pointer };
        this.fontOwners.register(owner, pointer, owner);
        return owner;
    }
    hint(hint, value) {
        if (hint === 0) this.hints.clear();
        else this.hints.set(hint, value);
    }
    createWindow(width, height, title, fullscreen) {
        if (this.visible) throw new Error("The browser runtime already owns a canvas presentation");
        document.title = title;
        this.closing = false;
        this.width = this.canvas.clientWidth || width;
        this.height = this.canvas.clientHeight || height;
        this.resize();
        if (fullscreen) this.fullscreen(true);
    }
    destroyWindow() {
        this.hide();
    }
    show() {
        this.visible = true;
        this.canvas.style.visibility = "visible";
    }
    hide() {
        this.visible = false;
        this.canvas.style.visibility = "hidden";
    }
    attribute(attribute) {
        if (attribute === 0x00022001) return 0; // GLFW_NO_API: bgfx owns the context.
        if (attribute === 0x00020004) return this.visible ? 1 : 0;
        if (attribute === 0x00020001) return document.activeElement === this.canvas ? 1 : 0;
        return this.attributes.get(attribute) ?? this.hints.get(attribute) ?? 0;
    }
    setAttribute(attribute, value) {
        this.attributes.set(attribute, value);
        if (attribute === 0x00020005) this.canvas.style.border = value ? "" : "none";
    }
    size(width, height) {
        if (!Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1)
            throw new RangeError("Canvas dimensions must be positive integers");
        this.canvas.style.width = width + "px";
        this.canvas.style.height = height + "px";
        this.resize();
    }
    resize() {
        const box = this.canvas.getBoundingClientRect();
        const width = Math.max(1, Math.round(box.width || this.width));
        const height = Math.max(1, Math.round(box.height || this.height));
        const ratio = Math.max(0.25, window.devicePixelRatio || 1);
        const pixelsX = Math.max(1, Math.round(width * ratio));
        const pixelsY = Math.max(1, Math.round(height * ratio));
        const changed =
            this.width !== width ||
            this.height !== height ||
            this.canvas.width !== pixelsX ||
            this.canvas.height !== pixelsY;
        this.width = width;
        this.height = height;
        if (this.canvas.width !== pixelsX) this.canvas.width = pixelsX;
        if (this.canvas.height !== pixelsY) this.canvas.height = pixelsY;
        if (changed) this.emit(6, width, height, pixelsX, pixelsY);
    }
    position(x, y) {
        this.canvas.style.position = "relative";
        this.canvas.style.left = x + "px";
        this.canvas.style.top = y + "px";
        this.emit(9, x, y);
    }
    limits(minWidth, minHeight, maxWidth, maxHeight) {
        const style = this.canvas.style;
        style.minWidth = minWidth > 0 ? minWidth + "px" : "";
        style.minHeight = minHeight > 0 ? minHeight + "px" : "";
        style.maxWidth = maxWidth > 0 ? maxWidth + "px" : "";
        style.maxHeight = maxHeight > 0 ? maxHeight + "px" : "";
    }
    track(promise) {
        Promise.resolve(promise).catch((error) => {
            this.lastRequestError = error;
            this.canvas.dispatchEvent(new CustomEvent("valthorneerror", { detail: error }));
            console.error(error);
        });
    }
    fullscreen(value) {
        if (value) this.track(this.canvas.requestFullscreen());
        else if (document.fullscreenElement) this.track(document.exitFullscreen());
    }
    inputMode(mode, value) {
        if (mode !== 0x00033001) return; // Raw movement is supplied by browser pointer lock.
        if (value === 0x00034003) this.track(this.canvas.requestPointerLock());
        else {
            if (document.pointerLockElement === this.canvas) document.exitPointerLock();
            this.canvas.style.cursor = value === 0x00034002 ? "none" : "default";
        }
    }
    setCursor(cursor) {
        const standard = [
            "default",
            "text",
            "crosshair",
            "pointer",
            "ew-resize",
            "ns-resize",
            "nwse-resize",
            "nesw-resize",
            "all-scroll",
            "not-allowed",
        ];
        this.canvas.style.cursor = this.cursors.get(cursor) || standard[cursor - 0x00036001] || "default";
    }
    releaseCursor(cursor) {
        this.cursors.delete(cursor);
    }
    image(pixels, width, height) {
        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;
        const context = canvas.getContext("2d");
        context.putImageData(
            new ImageData(new Uint8ClampedArray(pixels.buffer, pixels.byteOffset, width * height * 4), width, height),
            0,
            0,
        );
        return canvas.toDataURL("image/png");
    }
    createCursor(pixels, width, height, x, y) {
        const id = this.nextCursor++;
        this.cursors.set(id, `url("${this.image(pixels, width, height)}") ${x} ${y}, auto`);
        return id;
    }
    icon(pixels, width, height) {
        let link = document.querySelector('link[rel="icon"]');
        if (!link) {
            link = document.createElement("link");
            link.rel = "icon";
            document.head.append(link);
        }
        link.href = this.image(pixels, width, height);
    }
    emit(kind, a = 0, b = 0, c = 0, d = 0, x = 0, y = 0, text = null) {
        if (this.receiver) this.receiver(kind, a, b, c, d, x, y, text);
        else this.events.push([kind, a, b, c, d, x, y, text]);
    }
    installInput(receiver) {
        this.receiver = receiver;
        if (this.abort) return;
        this.abort = new AbortController();
        const options = { signal: this.abort.signal };
        const on = (target, name, listener, extra = {}) =>
            target.addEventListener(name, listener, { ...options, ...extra });
        const mods = (event) =>
            (event.shiftKey ? 1 : 0) |
            (event.ctrlKey ? 2 : 0) |
            (event.altKey ? 4 : 0) |
            (event.metaKey ? 8 : 0) |
            (event.getModifierState?.("CapsLock") ? 16 : 0) |
            (event.getModifierState?.("NumLock") ? 32 : 0);
        const key = (event, action) => {
            const code = keyNames.get(event.code) ?? -1;
            const paste = action && (event.ctrlKey || event.metaKey) && event.code === "KeyV";
            this.expectingPaste = !!paste;
            this.capsLock = !!event.getModifierState?.("CapsLock");
            if (code >= 0) {
                if (action) this.keys.add(code);
                else this.keys.delete(code);
                this.emit(0, code, code, action, mods(event));
            }
            if (
                action &&
                event.key.length > 0 &&
                !event.ctrlKey &&
                !event.metaKey &&
                !event.altKey &&
                [...event.key].length === 1 &&
                !event.isComposing
            )
                this.emit(1, event.key.codePointAt(0));
            if (code >= 0 && !paste) event.preventDefault();
        };
        on(this.canvas, "keydown", (event) => {
            this.audioBackend.activate();
            key(event, event.repeat ? 2 : 1);
        });
        on(this.canvas, "keyup", (event) => key(event, 0));
        on(this.canvas, "paste", (event) => {
            const text = event.clipboardData?.getData("text/plain");
            if (text === undefined) return;
            this.expectingPaste = false;
            for (const waiter of this.pasteWaiters.splice(0)) {
                clearTimeout(waiter.timer);
                waiter.resolve(text);
            }
            event.preventDefault();
        });
        on(this.canvas, "compositionend", (event) => {
            for (const value of event.data) this.emit(1, value.codePointAt(0));
        });
        on(this.canvas, "focus", () => this.emit(5, 1));
        on(this.canvas, "blur", () => {
            for (const code of this.keys) this.emit(0, code, code, 0, 0);
            for (const button of this.buttons) this.emit(3, button, 0, 0);
            this.keys.clear();
            this.buttons.clear();
            this.emit(5, 0);
        });
        const position = (event) => {
            const box = this.canvas.getBoundingClientRect();
            if (document.pointerLockElement === this.canvas) {
                this.cursorX += event.movementX;
                this.cursorY += event.movementY;
            } else {
                this.cursorX = ((event.clientX - box.left) * this.width) / box.width;
                this.cursorY = ((event.clientY - box.top) * this.height) / box.height;
            }
            this.emit(2, 0, 0, 0, 0, this.cursorX, this.cursorY);
        };
        on(this.canvas, "pointermove", (event) => {
            if (event.isPrimary) position(event);
        });
        on(this.canvas, "pointerdown", (event) => {
            if (!event.isPrimary) return;
            this.audioBackend.activate();
            this.canvas.focus();
            this.canvas.setPointerCapture(event.pointerId);
            position(event);
            const button = [0, 2, 1][event.button] ?? event.button;
            this.buttons.add(button);
            this.emit(3, button, 1, mods(event));
            event.preventDefault();
        });
        on(this.canvas, "pointerup", (event) => {
            if (!event.isPrimary) return;
            position(event);
            const button = [0, 2, 1][event.button] ?? event.button;
            this.buttons.delete(button);
            this.emit(3, button, 0, mods(event));
        });
        on(this.canvas, "pointercancel", () => {
            for (const button of this.buttons) this.emit(3, button, 0, 0);
            this.buttons.clear();
        });
        on(this.canvas, "contextmenu", (event) => event.preventDefault());
        on(this.canvas, "dragover", (event) => {
            event.preventDefault();
            event.dataTransfer.dropEffect = "copy";
        });
        on(this.canvas, "drop", (event) => {
            event.preventDefault();
            const token = this.nextDrop++,
                paths = [];
            for (const file of event.dataTransfer.files) {
                const path = `drop/${token}/${file.name.replaceAll("\\", "_").replaceAll("/", "_")}`;
                this.assets.set(path, file);
                paths.push(path);
            }
            if (paths.length) {
                this.dropBatches.set(token, paths);
                this.emit(7, paths.length, token);
            }
        });
        on(
            this.canvas,
            "wheel",
            (event) => {
                const scale = event.deltaMode === 1 ? 1 : event.deltaMode === 2 ? this.height / 40 : 1 / 40;
                this.emit(4, 0, 0, 0, 0, -event.deltaX * scale, -event.deltaY * scale);
                event.preventDefault();
            },
            { passive: false },
        );
        on(this.canvas, "webglcontextlost", (event) => {
            event.preventDefault();
            this.contextLost = true;
            if (this.frameRequest) cancelAnimationFrame(this.frameRequest);
            this.frameRequest = 0;
        });
        on(this.canvas, "webglcontextrestored", () => {
            this.contextLost = false;
            this.emit(10);
            if (this.frameWake) this.requestFrame(this.frameWake);
        });
        on(window, "resize", () => this.resize());
        on(document, "fullscreenchange", () => this.resize());
        on(window, "pagehide", () => this.emit(8));
        this.observer = new ResizeObserver(() => this.resize());
        this.observer.observe(this.canvas);
        for (const event of this.events.splice(0)) this.receiver(...event);
    }
    requestFrame(wake) {
        if (this.frameRequest) cancelAnimationFrame(this.frameRequest);
        this.frameWake = wake;
        if (this.contextLost) return;
        this.frameRequest = requestAnimationFrame(() => {
            this.frameRequest = 0;
            this.frameWake = null;
            this.resize();
            wake();
        });
    }
    capture(path) {
        const url = this.canvas.toDataURL("image/png");
        const encoded = atob(url.slice(url.indexOf(",") + 1)),
            bytes = new Uint8Array(encoded.length);
        for (let i = 0; i < encoded.length; i++) bytes[i] = encoded.charCodeAt(i);
        this.assets.set(path, bytes.buffer);
        this.captures.set(path, url);
        this.canvas.dispatchEvent(new CustomEvent("valthornecapture", { detail: { path, url }, bubbles: true }));
    }
    close() {
        if (document.pointerLockElement === this.canvas) document.exitPointerLock();
        this.canvas.style.cursor = "";
        this.cursors.clear();
        if (this.frameRequest) cancelAnimationFrame(this.frameRequest);
        this.frameRequest = 0;
        this.frameWake = null;
        this.abort?.abort();
        this.abort = null;
        this.observer?.disconnect();
        this.observer = null;
        for (const waiter of this.pasteWaiters.splice(0)) {
            clearTimeout(waiter.timer);
            waiter.reject(new Error("Application session closed"));
        }
        this.receiver = null;
        this.events.length = 0;
        this.keys.clear();
        this.buttons.clear();
        this.dropBatches.clear();
        this.assets.clear();
        this.captures.clear();
    }

    /** Requests the ordinary JGL close path and returns actual teardown completion. */
    requestClose() {
        this.closing = true;
        this.emit(8);
        return this.completion;
    }

    /** Releases host-owned memory after the Java application has finished all sessions. */
    async dispose() {
        this.close();
        try {
            await this.audioBackend.close();
        } finally {
            try {
                try {
                    this.physics.close();
                } finally {
                    this.yoga.close();
                }
            } finally {
                if (this.upload) this.native._free(this.upload);
                if (this.words) this.native._free(this.words);
                if (this.values) this.native._free(this.values);
                this.upload = this.words = this.values = 0;
                this.uploadCapacity = 0;
                if (globalThis.valthorneHost === this) {
                    delete globalThis.valthorneHost;
                    delete globalThis.valthorneNative;
                    delete globalThis.main;
                }
            }
        }
    }
}

/** Loads native graphics before the transpiled application enters its main method. */
export async function launch(canvas, applicationUrl = "./application.js", args = []) {
    if (!(canvas instanceof HTMLCanvasElement)) throw new TypeError("Valthorne requires a canvas element");
    if (launching || globalThis.valthorneHost) throw new Error("A Valthorne browser host is already loading or active");
    launching = true;
    try {
        canvas.tabIndex = canvas.tabIndex >= 0 ? canvas.tabIndex : 0;
        const native = await createBgfx({ canvas, printErr: (message) => console.error(message) });
        let host, script;
        try {
            host = new BrowserHost(canvas, native);
            globalThis.valthorneNative = native;
            globalThis.valthorneHost = host;
            await new Promise((resolve, reject) => {
                script = document.createElement("script");
                script.src = new URL(applicationUrl, import.meta.url).href;
                script.onload = resolve;
                script.onerror = () => reject(new Error("Could not load application JavaScript"));
                document.head.append(script);
            });
            if (typeof globalThis.main !== "function")
                throw new Error("The browser export has no application entry point");
            host.completion = new Promise((resolve, reject) =>
                globalThis.main(args, (result) => {
                    host.dispose()
                        .then(
                            () => (result instanceof Error ? reject(result) : resolve()),
                            (cleanup) =>
                                reject(
                                    result instanceof Error
                                        ? new AggregateError(
                                              [result, cleanup],
                                              "Application and browser teardown failed",
                                          )
                                        : cleanup,
                                ),
                        )
                        .finally(() => script.remove());
                }),
            );
            return host;
        } catch (error) {
            script?.remove();
            if (host) {
                try {
                    await host.dispose();
                } catch (cleanup) {
                    throw new AggregateError([error, cleanup], "Browser launch and cleanup failed");
                }
            }
            throw error;
        }
    } finally {
        launching = false;
    }
}
