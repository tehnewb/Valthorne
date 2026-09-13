import {readFile,writeFile,mkdir,readdir} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../../',import.meta.url));
const output=path.join(root,'portable/web/build/generated/graphics');
export const sharedGraphics=['viewport/Viewport','viewport/PerspectiveViewport','graphics/texture/Texture','graphics/texture/TextureBatch','graphics/texture/FrameBuffer','graphics/texture/NinePatchTexture','graphics/Sprite','graphics/ImmediateTextureRenderer','graphics/shader/Shader','audio/sound/SoundPlayer'];
async function collectUI(dir,prefix='ui'){
    for(const item of await readdir(dir,{withFileTypes:true})){
        const name=prefix+'/'+item.name;
        if(item.isDirectory())await collectUI(path.join(dir,item.name),name);
        else if(item.name.endsWith('.java')&&item.name!=='SlugLabel.java'&&/org\.lwjgl/.test(await readFile(path.join(dir,item.name),'utf8')))sharedGraphics.push(name.slice(0,-5));
    }
}
await collectUI(path.join(root,'src/main/java/valthorne/ui'));
sharedGraphics.push('ui/behavior/TextEditing','ui/behavior/TextEditModel','graphics/model/ObjModel3D','graphics/model/ModelParameters');
sharedGraphics.push('graphics/lighting2d/Lighting2D','graphics/lighting2d/GroundShadowRenderer2D');
sharedGraphics.push('io/file/ValthorneFiles');
sharedGraphics.push('asset/Assets');
sharedGraphics.push('graphics/debug/PerformanceOverlay','graphics/radiance/RadianceCascades','graphics/radiance/RadianceRenderTarget','graphics/radiance/RadianceTexture');
sharedGraphics.push('graphics/font/slug/SlugFont','graphics/font/slug/SlugBatch','graphics/font/slug/SlugShader','ui/nodes/SlugLabel');
sharedGraphics.push('graphics/model/MeshBatch3D','graphics/model/BillboardBatch3D','graphics/model/ShadowMap3D','graphics/lighting3d/Lighting3D','graphics/GraphicsCapabilities');
sharedGraphics.push('graphics/model/SceneRenderer3D');
sharedGraphics.push('graphics/model/PathTracer3D');
sharedGraphics.push('graphics/texture/TexturePacker','graphics/particle/ParticleSystem','graphics/shader/ShapeShader','graphics/lighting/DynamicMesh2D','graphics/lighting/LightMapRenderer','graphics/lighting/LightMesh','graphics/lighting/LightTexture','graphics/lighting/RayHandler','graphics/lighting/SoftShadowMesh','graphics/lighting2d/SpriteVolumeRenderer2D');
sharedGraphics.push(...['TiledXML','TiledMapParameters','TiledMapData','TiledMapLoader','FileSystemResolver','TiledResolvers','TiledDecoding'].map(name=>'graphics/map/tiled/'+name));
function replaceMethod(source,signature,body){
    const marker=source.indexOf(signature);if(marker<0)throw new Error('Missing backend adaptation '+signature);
    const start=source.indexOf('{',marker);let end=start+1,depth=1;
    while(depth){const c=source[end++];if(c==='{')depth++;if(c==='}')depth--;}
    return source.slice(0,start+1)+'\n'+body+'\n'+source.slice(end-1);
}
function replaceBody(source,name,body){
    const marker=source.indexOf(name+'() {');if(marker<0)throw new Error('Missing backend adaptation '+name);
    const start=source.indexOf('{',marker);let end=start+1,depth=1;
    while(depth){const c=source[end++];if(c==='{')depth++;if(c==='}')depth--;}
    return source.slice(0,start+1)+body+source.slice(end-1);
}
for(const name of sharedGraphics){
    let source=await readFile(path.join(root,'src/main/java/valthorne',name+'.java'),'utf8');
    if(name==='graphics/model/PathTracer3D'){
        source=source.replace('import org.lwjgl.system.MemoryUtil;','import valthorne.web.graphics.BrowserPathTraceGL;');
        source=source.replace(/if\(!GL.getCapabilities\(\).OpenGL43\)throw new IllegalStateException\([^\r\n]+/,'if(!BrowserPathTraceGL.supported())throw new IllegalStateException("Path tracing requires WebGL2 float rendering and filtering");');
        source=source.replaceAll('glBindBuffer(GL_SHADER_STORAGE_BUFFER,','BrowserPathTraceGL.bindBuffer(')
            .replace(/glBufferData\(GL_SHADER_STORAGE_BUFFER,([^;]+),GL_STATIC_DRAW\);/g,'BrowserPathTraceGL.upload($1);')
            .replaceAll('glBindBufferBase(GL_SHADER_STORAGE_BUFFER,','BrowserPathTraceGL.bindStorage(')
            .replace(/glBindImageTexture\((\d+),([^,]+),0,false,0,GL_\w+,GL_RGBA32F\)/g,'BrowserPathTraceGL.bindImage($1,$2)')
            .replaceAll('glDispatchCompute((width+7)/8,(height+7)/8,1);','BrowserPathTraceGL.dispatch();')
            .replace(/glMemoryBarrier\([^;]+;/g,'')
            .replace(/glTexStorage2D\(GL_TEXTURE_2D,1,GL_RGBA32F,([^,]+),([^\)]+)\)/g,'BrowserPathTraceGL.storage($1,$2)');
        source=source.replace('glUseProgram(present);','BrowserPathTraceGL.present();glUseProgram(present);');
        source=source.replaceAll('glDisable(GL_FRAMEBUFFER_SRGB);','');
        source=replaceMethod(source,'private void uploadAtlas(',`if(atlas!=0)glDeleteTextures(atlas);int[] ids=new int[textures.size()];for(int i=0;i<ids.length;i++)ids[i]=textures.get(i).getTextureID();atlas=BrowserPathTraceGL.atlas(ids);`);
        source=source.replace('private static int program(int[] types,String[] sources){','private static int program(int[] types,String[] sources){if(types.length==1&&types[0]==GL_COMPUTE_SHADER)return BrowserPathTraceGL.program(sources[0]);');
        const state=source.indexOf('    private static final class State implements AutoCloseable{');if(state<0)throw new Error('Review path tracing state guard');
        source=source.slice(0,state)+`    private static final class State implements AutoCloseable{private final org.teavm.jso.JSObject saved=BrowserPathTraceGL.begin();public void close(){BrowserPathTraceGL.end(saved);}}\n}\n`;
    }
    if(name==='graphics/debug/PerformanceOverlay'){
        source=source.replace(/^import (?:java\.awt|javax\.imageio)[^;]+;\r?\n/gm,'');
        source=replaceMethod(source,'public PerformanceOverlay(',`atlas=new Texture(valthorne.web.graphics.BrowserPerformanceAtlas.create());
        for(int i=0;i<glyphs.length;i++)glyphs[i]=new TextureRegion(atlas,i%16*18,168-(i/16+1)*28,18,28);`);
    }
    if(name==='graphics/radiance/RadianceCascades'){
        // These four kernels only write their own pixel and use distinct read/write
        // images. Ordered raster passes preserve those dependencies without compute.
        const dispatch=/glBindImageTexture\(0, ([^\r\n]+), 0, false, 0, GL_WRITE_ONLY, GL_RGBA16F\);\s*(\w+)\.dispatch\(groupCount\((.+)\), groupCount\((.+)\), 1\);\s*ComputeShader\.memoryBarrierAll\(\);/g;
        if([...source.matchAll(dispatch)].length!==4)throw new Error('Review radiance pass adaptation');
        source=source.replace(dispatch,'$2.renderTo($1, $3, $4);');
        source=source.replace('import valthorne.graphics.shader.ComputeShader;','import valthorne.web.graphics.BrowserRadianceShader;').replaceAll('ComputeShader','BrowserRadianceShader');
        source=source.replace(/ShaderSources.load\("radiance\/(trace|extend|merge|resolve)\.comp"\)/g,'ShaderSources.load("radiance-web/$1.frag")');
        const marker=source.indexOf('public void render(RadianceSceneBuffer sceneBuffer)'),start=source.indexOf('{',marker);let end=start+1,depth=1;
        while(depth){const c=source[end++];if(c==='{')depth++;if(c==='}')depth--;}
        const body=source.slice(start+1,end-1);
        source=source.slice(0,start+1)+'try(var rasterState=new BrowserRadianceShader.PassState()){'+body+'}'+source.slice(end-1);
    }
    if(name==='graphics/model/SceneRenderer3D')source=source.replaceAll('org.lwjgl.glfw.GLFW.glfwGetCurrentContext()','valthorne.web.graphics.BrowserGL.contextToken()').replaceAll('with a GLFW context current','with a browser graphics context current');
    if(name==='graphics/GraphicsCapabilities'){
        source=source.replace('import org.lwjgl.opengl.GL;','').replace('import org.lwjgl.system.Platform;','');
        source=replaceBody(source,'current','return new GraphicsCapabilities(valthorne.web.graphics.BrowserGL.glGetString(0x1F00),valthorne.web.graphics.BrowserGL.glGetString(0x1F01),valthorne.web.graphics.BrowserGL.glGetString(0x1F02),true,valthorne.graphics.shader.ComputeShader.isComputeSupported(),valthorne.web.graphics.BrowserPathTraceGL.supported(),true);');
        source=replaceMethod(source,'public static boolean supportsCompute(', 'return valthorne.graphics.shader.ComputeShader.isComputeSupported();');
        source=source.replace('supportsCompute(GLCapabilities gl)','supportsCompute(org.lwjgl.opengl.GLCapabilities gl)');
    }
    if(name==='graphics/font/slug/SlugFont'){
        source=source.replace('import org.lwjgl.stb.STBTTFontinfo;','import valthorne.web.graphics.BrowserOutlineFont;\nimport static valthorne.web.graphics.BrowserOutlineFont.*;').replace('import org.lwjgl.stb.STBTTVertex;','').replace('import static org.lwjgl.stb.STBTruetype.*;','').replaceAll('STBTTFontinfo','BrowserOutlineFont');
        source=source.replace('Files.readAllBytes(Path.of(path))','valthorne.web.BrowserIO.read(path)');
        source=source.replace('catch (IOException e)','catch (RuntimeException e)');
        source=source.replace(/ByteBuffer fontBuffer = BufferUtils.createByteBuffer\(fontBytes.length\);[\s\S]*?throw new RuntimeException\("Failed to initialize STB font\."\);\s*\}/,'ByteBuffer fontBuffer = null;\n        BrowserOutlineFont info = BrowserOutlineFont.load(fontBytes);');
        source=replaceMethod(source,'private static List<SlugCurve> loadCurves(', `List<SlugCurve> curves=new ArrayList<>();float[] commands=info.commands(codepoint);float sx=0,sy=0,x=0,y=0;boolean open=false;
        for(int i=0;i<commands.length;i+=7){int type=(int)commands[i];float nx=commands[i+1]*emScale,ny=commands[i+2]*emScale;
            if(type==0){if(open)addLine(curves,x,y,sx,sy);sx=nx;sy=ny;x=nx;y=ny;open=true;}
            else if(type==1){addLine(curves,x,y,nx,ny);x=nx;y=ny;}
            else if(type==2){addQuadratic(curves,x,y,commands[i+3]*emScale,commands[i+4]*emScale,nx,ny);x=nx;y=ny;}
            else if(type==3){approximateCubic(curves,x,y,commands[i+3]*emScale,commands[i+4]*emScale,commands[i+5]*emScale,commands[i+6]*emScale,nx,ny);x=nx;y=ny;}
            else if(type==4&&open){addLine(curves,x,y,sx,sy);x=sx;y=sy;open=false;}
        }if(open)addLine(curves,x,y,sx,sy);return curves;`);
        // Byte swapping is not a WebGL pixel-store option.
        source=source.replace('glGetInteger(GL_UNPACK_SWAP_BYTES)','0').replaceAll('glPixelStorei(GL_UNPACK_SWAP_BYTES, 0);','').replaceAll('glPixelStorei(GL_UNPACK_SWAP_BYTES, swapBytes);','');
    }
    if(name==='asset/Assets'){
        source=source.replaceAll('ExecutorService','valthorne.web.BrowserAssetExecutor').replaceAll('Executors.newVirtualThreadPerTaskExecutor()','new valthorne.web.BrowserAssetExecutor()');
        source=source.replaceAll('createvalthorne.web.BrowserAssetExecutor','createExecutorService');
        source=source.replaceAll('ConcurrentHashMap.newKeySet()','new java.util.HashSet<>()').replaceAll('ConcurrentLinkedQueue','java.util.ArrayDeque');
        source=source.replaceAll('(_, ex)','(ignoredValue, ex)').replaceAll('(_, _)','(ignoredValue, ignoredFailure)');
        // Removal while iterating is legal on the JVM concurrent set. Iterate a
        // snapshot on the browser's single-threaded cooperative collection.
        source=source.replace('for (AssetParameters parameters : prepared)','for (AssetParameters parameters : new ArrayList<>(prepared))');
    }
    if(name.startsWith('graphics/map/tiled/')){
        source=source.replace('Base64.getMimeDecoder().decode(trimmed)','Base64.getDecoder().decode(trimmed.replaceAll("[^A-Za-z0-9+/=]", ""))');
        source=source.replaceAll('XMLInputFactory.newInstance()','new com.fasterxml.aalto.stax.InputFactoryImpl()').replaceAll('XMLInputFactory.newFactory()','new com.fasterxml.aalto.stax.InputFactoryImpl()');
        source=source.replaceAll('Files.readAllBytes(Paths.get(tmxFilePath))','valthorne.web.BrowserIO.read(tmxFilePath)').replaceAll('Files.readAllBytes(Paths.get(path))','valthorne.web.BrowserIO.read(path)');
        source=source.replace('source instanceof TiledMapSource.PathSource(String path)) {','source instanceof TiledMapSource.PathSource value) { String path=value.path();');
        source=source.replace('source instanceof TiledMapSource.BytesSource(byte[] bytes, String virtualPath)) {','source instanceof TiledMapSource.BytesSource value) { byte[] bytes=value.bytes(); String virtualPath=value.virtualPath();');
        if(name.endsWith('/TiledXML'))source=replaceMethod(source,'public static String resolvePathString(', 'return rel == null ? null : valthorne.web.BrowserIO.resolveSibling(basePath == null ? "" : basePath, rel);');
        if(name.endsWith('/TiledMapParameters'))source=replaceMethod(source,'private static String toAbsoluteAlias(', 'return "/" + normalize(path);');
        if(name.endsWith('/FileSystemResolver'))source=replaceMethod(source,'public byte[] resolve(', 'return valthorne.web.BrowserIO.read(valthorne.web.BrowserIO.resolveSibling(parentPath == null ? "" : parentPath, dependencyPath));');
        if(name.endsWith('/TiledResolvers'))source=replaceMethod(source,'private static String resolvePath(', 'return valthorne.web.BrowserIO.resolveSibling(basePath == null ? "" : basePath, dependencyPath);');
    }
    source=source.replace(/^import org\.lwjgl\.BufferUtils;/m,'import valthorne.web.graphics.Buffers;').replaceAll('BufferUtils.','Buffers.');
    source=source.replace(/^import (?:static )?org\.lwjgl\.opengl\.[^;]+;\r?\n/gm,'');
    source=source.replace(/^(package [^;]+;)/m,'$1\nimport static valthorne.web.graphics.BrowserGL.*;');
    source=source.replace('import org.lwjgl.util.yoga.Yoga;','import valthorne.web.ui.BrowserYoga;').replaceAll('Yoga.','BrowserYoga.');
    source=source.replace(/^import static org\.lwjgl\.nanovg\.NanoVG(?:GL3)?\.[^;]+;\r?\n/gm,'');
    if(name.startsWith('ui/'))source=source.replace(/^(package [^;]+;)/m,'$1\nimport static valthorne.web.ui.BrowserNano.*;');
    source=source.replaceAll('org.lwjgl.opengl.GL11.','').replaceAll('org.lwjgl.opengl.GL20.','').replaceAll('org.lwjgl.opengl.GL14.','').replaceAll('org.lwjgl.opengl.GL13.','');
    source=source.replace(/org\.lwjgl\.opengl\.GL\d+\./g,'');
    if(name==='graphics/model/MeshBatch3D')source=source.replaceAll('glDisable(GL_FRAMEBUFFER_SRGB);','');
    if(name==='graphics/particle/ParticleSystem')source=source.replaceAll('glEnable(GL_PROGRAM_POINT_SIZE);','').replaceAll('glDisable(GL_PROGRAM_POINT_SIZE);','');
    if(name==='graphics/model/ObjModel3D'){
        source=source.replace(/return Path\.of\(source\)\.resolveSibling\(relative\)\.normalize\(\)\.toString\(\)\.replace\([^;]+;/,'return valthorne.web.BrowserIO.resolveSibling(source, relative);');
        source=source.replace(/Path absolute = Path\.of\(path\)\.toAbsolutePath\(\)\.normalize\(\);\s*return load\(absolute\.toString\(\), name -> Files\.readAllBytes\(Path\.of\(name\)\), convertAndGround\);/,'return load(path, valthorne.web.BrowserIO::read, convertAndGround);');
        source=source.replaceAll('split("\\\\R")','split("\\\\r\\\\n|[\\\\n\\\\r\\\\u0085\\\\u2028\\\\u2029]")');
    }
    if(name==='graphics/model/ModelParameters'){
        source=source.replace(/Path\.of\(path\)\.toAbsolutePath\(\)\.normalize\(\)\.toString\(\), key,\s*name -> Files\.readAllBytes\(Path\.of\(name\)\)\, false/,'path, key, valthorne.web.BrowserIO::read, false');
        source=source.replace('ValthorneFiles::readBytes','valthorne.web.BrowserIO::read');
    }
    if(name==='io/file/ValthorneFiles'){
        source=source.replace('return ValthorneFiles.class.getClassLoader().getResource(normalized) != null;', 'try (InputStream stream = ValthorneFiles.class.getClassLoader().getResourceAsStream(normalized)) { return stream != null; } catch (IOException error) { throw new ValthorneFileException("Resource lookup failed: " + normalized, error); }');
        source=source.replace('temp = Files.createTempFile(prefix, suffix);','Path directory = Path.of("/tmp/valthorne"); Files.createDirectories(directory); temp = Files.createTempFile(directory, prefix, suffix);');
    }
    if(name==='graphics/lighting2d/Lighting2D'){
        // WebGL performs sRGB conversion according to the attachment format;
        // it has no desktop FRAMEBUFFER_SRGB capability toggle.
        source=source.replace('srgb = glIsEnabled(GL_FRAMEBUFFER_SRGB)','srgb = false');
        source=source.replace('if (srgb) glEnable(GL_FRAMEBUFFER_SRGB);\n            else glDisable(GL_FRAMEBUFFER_SRGB);','');
        source=source.replace(/if \(srgb\) glEnable\(GL_FRAMEBUFFER_SRGB\);\r?\n\s*else glDisable\(GL_FRAMEBUFFER_SRGB\);/g,'');
        source=source.replaceAll('glDisable(GL_FRAMEBUFFER_SRGB);','');
    }
    if(name==='ui/UIRoot'){
        source=source.replaceAll('nodes.getFirst()','nodes.get(0)').replaceAll('nodes.removeFirst()','nodes.remove(0)').replaceAll('focusScopes.getLast()','focusScopes.get(focusScopes.size()-1)');
        source=replaceBody(source,'registerDefaultNanoFont','\n if(!registerNanoFont("default", "ui/AtkinsonHyperlegible-Regular.ttf"))throw new IllegalStateException("Default UI font could not be loaded");\n');
        source=replaceBody(source,'cleanupExtractedNanoFonts','\n extractedNanoFonts.clear();\n');
    }
    if(name==='ui/theme/ProfessionalTheme')source=source.replace('Math.clamp(.5f - distance, 0, 1)','Math.max(0, Math.min(1, .5f - distance))');
    if(name==='ui/behavior/TextEditModel'){
        source=source.replace(/^    private static final Pattern GRAPHEME[^;]+;/m,'');
        source=source.replaceAll('Math.clamp(index, 0, text.length())','Math.max(0, Math.min(text.length(), index))');
        const start=source.indexOf('        var matcher = GRAPHEME.matcher(text);');const end=source.indexOf('\n    }',start);
        source=source.slice(0,start)+`        int[] values=valthorne.web.ui.BrowserText.boundaries(text);
        if(boundaries.length<values.length)boundaries=new int[values.length];
        System.arraycopy(values,0,boundaries,0,values.length);boundaryCount=values.length;`+source.slice(end);
    }
    if(name==='ui/behavior/TextEditing'){
        source=source.replace(/^import java\.awt[^;]+;\r?\n/gm,'');
        const start=source.indexOf('    public static boolean copy(');
        source=source.slice(0,start)+`public static boolean copy(TextEditModel model,boolean secret){return !secret&&model.hasSelection()&&valthorne.web.ui.BrowserClipboard.write(model.selectedText());}
public static void paste(TextEditModel model){String text=valthorne.web.ui.BrowserClipboard.read();if(text!=null)model.insert(text);}
}`;
    }
    if(name==='audio/sound/SoundPlayer'){
        source=source.replace(/^import static org\.lwjgl\.openal\.[^;]+;\r?\n/gm,'');
        source=source.replace('import static valthorne.web.graphics.BrowserGL.*;','import static valthorne.web.audio.BrowserAL.*;');
    }
    const file=path.join(output,'valthorne',name+'.java');await mkdir(path.dirname(file),{recursive:true});await writeFile(file,source);
}
// Embed bundled shader text at build time. Games still use ShaderSources.load.
const resources=path.join(root,'src/main/resources/valthorne/shaders');
let cases='';
async function visit(dir,prefix=''){
    for(const item of await readdir(dir,{withFileTypes:true})){
        const name=prefix+item.name;
        if(item.isDirectory())await visit(path.join(dir,item.name),name+'/');
        else cases+=`case ${JSON.stringify(name)}: return ${JSON.stringify(await readFile(path.join(dir,item.name),'utf8'))};\n`;
    }
}
await visit(resources);
for(const name of ['trace','extend','merge','resolve']){
    let shader=await readFile(path.join(resources,'radiance',name+'.comp'),'utf8');
    const stores=[...shader.matchAll(/imageStore\(u_outputImage,\s*(texel|pixel),/g)];
    if(!stores.length)throw new Error('Review radiance output addressing: '+name);
    shader=shader.replace('#version 430 core','#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;')
        .replace('layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;','')
        .replace('layout(rgba16f, binding = 0) writeonly uniform image2D u_outputImage;','layout(location=0) out vec4 valthorne_output;\nvoid valthorneStore(ivec2 pixel, vec4 value){valthorne_output=value;}')
        .replaceAll('gl_GlobalInvocationID.xy','gl_FragCoord.xy').replaceAll('imageStore(u_outputImage,','valthorneStore(');
    if(/imageStore|image2D|gl_GlobalInvocationID|\b(?:shared|barrier|atomic\w*|buffer)\b/.test(shader))throw new Error('Radiance kernel requires unsupported compute behavior: '+name);
    cases+=`case "radiance-web/${name}.frag": return ${JSON.stringify(shader)};\n`;
}
const file=path.join(output,'valthorne/graphics/shader/ShaderSources.java');await mkdir(path.dirname(file),{recursive:true});
await writeFile(file,`package valthorne.graphics.shader;
public final class ShaderSources {
 private ShaderSources(){}
 public static String load(String name){java.util.Objects.requireNonNull(name);switch(name){${cases}default:throw new IllegalStateException("Missing shader resource: /valthorne/shaders/"+name);}}
}`);
