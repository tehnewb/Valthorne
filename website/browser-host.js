/**
 * Web-port entry point and browser drawing primitives.
 *
 * Website behavior lives in Java. This adapter only connects the compiled
 * application to the portable graphics backends and browser-only resources.
 */
/** Startup failures leave the generated HTML available without a renderer. */
function bootstrapFailure(error) {
  document.querySelector('#status').textContent = 'Engine unavailable. Text version is ready.';
  globalThis.valthorneError = String(error.stack || error);
  console.error(error);
}

function createHost(BrowserGraphics, BrowserPlatform, BrowserNano, BrowserYoga, BrowserFonts) {
  /** Native browser controls own keyboard focus; audio is not activated implicitly. */
  class NativeControlPlatform extends BrowserPlatform {
    unlockAudio() { }
  }
  const platform = new NativeControlPlatform(document.querySelector('#scene'));
  platform.canvas.tabIndex = -1;
  Object.defineProperty(platform, 'legacyKeyEvent', { get: () => null, set: () => {} });
  const host = globalThis.valthorneHost = {
    platform,
    frameCallback: null,
    shutdownCallback: null,
    onConnect: null,
    onFailure: null,
    fontResolver: null,
    opacity: 1,
    offset: 0,
    pixelRatio: 1,
    closed: false,

    connectApplication(frame, shutdown) {
      this.frameCallback = frame;
      this.shutdownCallback = shutdown;
      this.onConnect?.();
    },

    frame(delta) { this.frameCallback?.(delta); },
    shutdownApplication() { this.shutdownCallback?.(); },
    face(handle) { return this.nano.get(handle).state.face; },

    fontSpec(context, size) {
      const family = context.fonts.get(context.state.face)?.family || '';
      return this.fontResolver
        ? this.fontResolver(context.state.face, family, size)
        : `${size}px "${family || context.state.face}"`;
    },

    measure(handle, text, size) {
      const context = this.nano.get(handle);
      context.ctx.font = this.fontSpec(context, size);
      return context.ctx.measureText(text).width;
    },

    /** Draw a decoded image with generic cover/contain fitting and clipping. */
    drawImage(handle, image, x, y, width, height, contain, radius, focalX = .5) {
      if (width <= 0 || height <= 0 || !image.naturalWidth || !image.naturalHeight) return;
      const context = this.nano.get(handle), ctx = this.nano.prepare(context);
      try {
        const scale = (contain ? Math.min : Math.max)(width / image.naturalWidth, height / image.naturalHeight);
        ctx.beginPath();
        if (radius > 0) ctx.roundRect(x, y, width, height, radius);
        else ctx.rect(x, y, width, height);
        ctx.clip();
        ctx.drawImage(image,
          x + (width - image.naturalWidth * scale) * focalX,
          y + (height - image.naturalHeight * scale) / 2,
          image.naturalWidth * scale, image.naturalHeight * scale);
        context.dirty = true;
      } finally { ctx.restore(); }
    },

    surface(handle) { return this.nano.get(handle).canvas; },

    /** Native gradient primitive; the Java caller owns its geometry and color. */
    fillGradient(handle, x, y, width, height, color, from, to) {
      const context = this.nano.get(handle), ctx = this.nano.prepare(context);
      try {
        const rgb = [(color >> 16) & 255, (color >> 8) & 255, color & 255].join(',');
        const gradient = ctx.createLinearGradient(x, y, x, y + height);
        gradient.addColorStop(0, 'rgba(' + rgb + ',' + from + ')');
        gradient.addColorStop(1, 'rgba(' + rgb + ',' + to + ')');
        ctx.fillStyle = gradient;
        ctx.fillRect(x, y, width, height);
        context.dirty = true;
      } finally { ctx.restore(); }
    },
    resize(ratio) { this.pixelRatio = ratio; this.graphics.resize(); },

    close() {
      if (this.closed) return;
      this.closed = true;
      this.graphics.close();
      this.nano.close();
      this.yoga.close();
      this.fonts.close();
      this.platform.close();
    }
  };
  host.graphics = new BrowserGraphics(host);
  host.nano = new BrowserNano(host);
  host.yoga = new BrowserYoga();
  host.fonts = new BrowserFonts();

  const font = host.nano.font.bind(host.nano);
  host.nano.font = context => {
    font(context);
    context.ctx.font = host.fontSpec(context, context.state.size);
  };
  const prepare = host.nano.prepare.bind(host.nano);
  host.nano.prepare = context => {
    const ctx = prepare(context);
    ctx.globalAlpha *= host.opacity;
    ctx.translate(0, host.offset);
    return ctx;
  };
  host.graphics.resize = function () {
    if (!this.gl) return;
    const width = Math.round(this.host.platform.window.width * host.pixelRatio);
    const height = Math.round(this.host.platform.window.height * host.pixelRatio);
    if (this.canvas.width !== width || this.canvas.height !== height) {
      this.canvas.width = width;
      this.canvas.height = height;
      this.gl.viewport(0, 0, width, height);
    }
  };
  host.graphics.context();
  host.graphics.canvas.addEventListener('webglcontextlost', event => {
    event.preventDefault();
    if (!host.closed) host.onFailure?.('Graphics context lost');
  });
  return host;
}

/** Java chooses whether and when the browser graphics backends are needed. */
globalThis.valthornePort = {
  async initialize() {
    const [graphics, platform, nano, yoga, fonts] = await Promise.all([
      import('./runtime/graphics.js'), import('./runtime/platform.js'),
      import('./runtime/nano-backend.js'), import('./runtime/yoga-backend.js'),
      import('./runtime/fonts.js')
    ]);
    createHost(graphics.BrowserGraphics, platform.BrowserPlatform,
      nano.BrowserNano, yoga.BrowserYoga, fonts.BrowserFonts);
    return null;
  }
};

// Only compiled-program loading happens before the Java entry point takes over.
const runtime = document.createElement('script');
const revision = document.querySelector('meta[name="valthorne-build"]').content;
runtime.src = 'runtime/valthorne.js?v=' + encodeURIComponent(revision);
runtime.onerror = () => bootstrapFailure(new Error('Compiled runtime could not load'));
runtime.onload = () => {
  try { main(); }
  catch (error) { bootstrapFailure(error); }
};
document.head.append(runtime);
