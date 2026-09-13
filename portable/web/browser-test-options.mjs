// Software GPU selection is restricted to the isolated CI test browser.
// https://chromium.googlesource.com/chromium/src/+/HEAD/docs/gpu/swiftshader.md
export const browserTestOptions = {
 channel: process.env.BROWSER_CHANNEL || 'chrome', headless: true,
 args: process.env.WEBGPU_SOFTWARE === '1' ? [
  '--enable-unsafe-webgpu', '--enable-unsafe-swiftshader',
  '--use-gl=angle', '--use-angle=swiftshader', '--use-webgpu-adapter=swiftshader',
  '--enable-features=Vulkan', '--use-vulkan=swiftshader'
 ] : []
};
