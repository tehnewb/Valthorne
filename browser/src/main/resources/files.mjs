let nextSession = 0;

/** Owns browser file grants, picker UI, and bounded workspace export lifetimes. */
export class BrowserFiles {
    constructor() {
        this.session = ++nextSession;
        this.serial = 0;
        this.dialogs = new Set();
        this.destinations = new Map();
        this.closed = false;
        this.downloads = new Map();
    }

    /** Explicit user selection imports files; the browser never exposes OS paths. */
    choose(mode, title, initial, patterns) {
        if (this.closed) return Promise.reject(new Error("Application session closed"));
        return new Promise((resolve, reject) => {
            const dialog = document.createElement("dialog");
            const heading = document.createElement("h2");
            heading.textContent = title || (mode === 0 ? "Open file" : mode === 1 ? "Save file" : "Open folder");
            dialog.setAttribute("aria-label", heading.textContent);
            const description = document.createElement("p");
            description.textContent = mode === 1 ? "Choose a save destination or download the file after the application writes it." : "Choose files to import into this application's workspace.";
            const action = document.createElement("button");
            action.type = "button";
            action.textContent = mode === 0 ? "Choose file" : mode === 1 ? "Choose destination" : "Choose folder";
            const cancel = document.createElement("button");
            cancel.type = "button";
            cancel.textContent = "Cancel";
            const filename = document.createElement("input");
            filename.type = "text";
            filename.setAttribute("aria-label", "File name");
            filename.value = (initial || "untitled").split(/[\\/]/).pop() || "untitled";
            let settled = false;
            const finish = (value, error) => {
                if (settled) return;
                settled = true;
                this.dialogs.delete(abort);
                dialog.remove();
                if (error) reject(error); else resolve(value);
            };
            const abort = () => finish(null);
            this.dialogs.add(abort);
            const cleanName = name => name && name !== "." && name !== ".." && !/[\\/\x00-\x1f]/.test(name);
            const base = `/workspace/imports/${++this.serial}`;
            const fileInput = document.createElement("input");
            fileInput.type = "file";
            fileInput.hidden = true;
            const extensions = patterns.map(pattern => /^\*\.[A-Za-z0-9._-]+$/.test(pattern) ? pattern.slice(1) : "").filter(Boolean);
            fileInput.accept = extensions.join(",");
            if (mode === 2) { fileInput.webkitdirectory = true; fileInput.multiple = true; fileInput.accept = ""; }
            fileInput.addEventListener("cancel", () => finish(null));
            fileInput.addEventListener("change", async () => {
                try {
                    const files = Array.from(fileInput.files || []);
                    if (!files.length) { finish(null); return; }
                    const entries = [];
                    for (const file of files) {
                        const parts = (mode === 2 ? file.webkitRelativePath : file.name).split("/");
                        if (!parts.every(cleanName)) throw new Error("Invalid imported file path");
                        entries.push({ path: base + "/" + parts.join("/"), bytes: new Int8Array(await file.arrayBuffer()) });
                    }
                    if (this.closed) throw new Error("Application session closed");
                    const selected = mode === 2 ? base + "/" + files[0].webkitRelativePath.split("/")[0] : entries[0].path;
                    finish({ entries, selected });
                } catch (error) { finish(null, error); }
            });
            const download = () => {
                if (!cleanName(filename.value)) { filename.setCustomValidity("Enter a file name without a directory path"); filename.reportValidity(); return; }
                const selected = `/workspace/exports/${++this.serial}/${filename.value}`;
                this.destinations.set(selected, { name: filename.value, handle: null });
                finish({ entries: [], selected, save: true });
            };
            action.addEventListener("click", async () => {
                action.disabled = true;
                try {
                    if (mode === 1) {
                        if (!window.showSaveFilePicker) { download(); return; }
                        const handle = await window.showSaveFilePicker({ suggestedName: filename.value || "untitled" });
                        if (settled || this.closed) return;
                        const selected = `/workspace/exports/${++this.serial}/${handle.name}`;
                        this.destinations.set(selected, { name: handle.name, handle });
                        finish({ entries: [], selected, save: true });
                    } else if (mode === 2 && window.showDirectoryPicker) {
                        const handle = await window.showDirectoryPicker({ mode: "read" });
                        if (settled || this.closed) return;
                        const entries = [];
                        const visit = async (directory, path) => {
                            entries.push({ path, bytes: null });
                            for await (const child of directory.values()) {
                                if (!cleanName(child.name)) throw new Error("Invalid imported file name");
                                if (child.kind === "directory") await visit(child, path + "/" + child.name);
                                else entries.push({ path: path + "/" + child.name, bytes: new Int8Array(await (await child.getFile()).arrayBuffer()) });
                            }
                        };
                        const selected = base + "/" + handle.name;
                        await visit(handle, selected);
                        if (this.closed) throw new Error("Application session closed");
                        finish({ entries, selected });
                    } else fileInput.click();
                } catch (error) {
                    if (error.name === "AbortError") finish(null); else finish(null, error);
                } finally { action.disabled = false; }
            });
            cancel.addEventListener("click", () => finish(null));
            dialog.addEventListener("cancel", event => { event.preventDefault(); finish(null); });
            dialog.append(heading, description);
            if (mode === 1) {
                dialog.append(filename);
                const fallback = document.createElement("button");
                fallback.type = "button";
                fallback.textContent = "Download";
                fallback.addEventListener("click", download);
                dialog.append(fallback);
            }
            if (mode === 2 && window.showDirectoryPicker) {
                const importFolder = document.createElement("button");
                importFolder.type = "button";
                importFolder.textContent = "Import folder";
                importFolder.addEventListener("click", () => fileInput.click());
                dialog.append(importFolder);
            }
            dialog.append(action, cancel, fileInput);
            document.body.append(dialog);
            dialog.showModal();
        });
    }

    /** Writes only to a destination explicitly selected by this session. */
    async write(path, bytes) {
        if (this.closed) throw new Error("Application session closed");
        const destination = this.destinations.get(path);
        if (!destination) throw new Error("No browser save destination was selected for " + path);
        if (destination.handle) {
            const writer = await destination.handle.createWritable();
            try { await writer.write(bytes); await writer.close(); }
            catch (error) { try { await writer.abort(); } catch (abortFailure) { error.cause ??= abortFailure; } throw error; }
        } else {
            this.download(destination.name, bytes);
            this.destinations.delete(path);
        }
    }

    /** Downloads a snapshot without exposing a local path or executable launcher. */
    download(name, bytes) {
        if (this.closed) throw new Error("Application session closed");
        const url = URL.createObjectURL(new Blob([bytes], { type: "application/octet-stream" }));
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = name;
        document.body.append(anchor);
        anchor.click();
        anchor.remove();
        this.downloads.set(url, setTimeout(() => {
            URL.revokeObjectURL(url);
            this.downloads.delete(url);
        }, 60000));
    }

    /** Cancels open picker UI so an application close cannot await user input forever. */
    cancelDialogs() {
        for (const abort of Array.from(this.dialogs)) abort();
    }

    /** Cancels pending picker UI and forgets granted handles at session shutdown. */
    close() {
        if (this.closed) return;
        this.closed = true;
        this.cancelDialogs();
        this.destinations.clear();
        for (const [url, timer] of this.downloads) {
            clearTimeout(timer);
            URL.revokeObjectURL(url);
        }
        this.downloads.clear();
    }
}
