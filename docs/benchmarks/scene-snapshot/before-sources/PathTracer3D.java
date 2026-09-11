package valthorne.graphics.model;

import valthorne.graphics.shader.ShaderSources;

import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import valthorne.camera.Camera3D;
import valthorne.camera.PerspectiveCamera;
import valthorne.graphics.texture.Texture;
import java.nio.*;
import java.util.*;
import static org.lwjgl.opengl.GL43.*;

/**
 * Multi-bounce GPU path tracer for triangle scenes. Requires OpenGL 4.3.
 * Uses shader cores with a balanced BVH, not hardware ray-tracing extensions.
 * Call on the context thread. Scene changes automatically restart accumulation;
 * call {@link #invalidate()} after editing the pixels of a borrowed texture.
 * Owns its GPU resources, but never the scene's models or textures.
 * Realtime mode is the default: it renders at viewport resolution, reprojects
 * matching surfaces across camera changes, and rejects history after scene edits.
 * Progressive mode provides stationary accumulation at the selected quality scale.
 * <pre>{@code
 * try (PathTracer3D lighting = new PathTracer3D()
 *         .setQuality(PathTracer3D.Quality.INTERACTIVE)) {
 *     lighting.render(scene, camera); // Current framebuffer and viewport; GL thread.
 * }
 * }</pre>
 *
 * @author Albert Beaupre
 */
public final class PathTracer3D implements AutoCloseable {
    private final float[] matrixUpload = new float[16], inverseUpload = new float[16]; // Reusable column-major camera and inverse-camera upload arrays.

    /**
     * REALTIME reprojects validated lighting history during camera movement;
     * PROGRESSIVE accumulates a stationary view without temporal reprojection.
     * Both modes retain the same stochastic multi-bounce light transport. Realtime
     * history is bounded during movement to limit lag in view-dependent reflections.
     *
     * @author Albert Beaupre
     */
    public enum RenderMode {
        /**
         * Reprojects validated history while the camera moves and uses full viewport resolution.
         */
        REALTIME,
        /**
         * Accumulates a stationary view at the selected quality's resolution scale.
         */
        PROGRESSIVE
    }
    private RenderMode renderMode=RenderMode.REALTIME; // Explicit rendering contract; retained across camera changes.
    private int historyRadiance,historyGuide,sequence; // Previous frame images and nonrepeating sample sequence.
    private int specularRadiance,historySpecular,materialGuide; // Separate reflected/transmitted radiance and diffuse reconstruction factors.
    private final int[] filteredSpecular=new int[2]; // Ping-pong storage for rough specular filtering.
    private float previousJitterX,previousJitterY; // Previous primary-ray subpixel position shared with its surface guides.
    private boolean historyValid,previousPerspective; // History is reusable only after a completed compatible frame.
    private float[] previousMatrix,previousInverse; // Previous rendered camera matrices, copied before camera updates.
    private final float[] previousPosition=new float[3]; // Previous perspective ray origin in world space.
    /**
     * Preset tracing cost: resolution scale in progressive mode, nominal samples
     * per rendered frame, and maximum path bounces. Realtime mode always uses full
     * viewport resolution while retaining each preset's sample and bounce counts.
     * @author Albert Beaupre
     */
    public enum Quality {
        /**
         * Half-resolution progressive rendering with one nominal sample and up to five bounces.
         */
        INTERACTIVE(.5f,1,5),
        /**
         * Three-quarter-resolution progressive rendering with two nominal samples and up to eight bounces.
         */
        HIGH(.75f,2,8),
        /**
         * Full-resolution progressive rendering with four nominal samples and up to twelve bounces.
         */
        ULTRA(1,4,12);
        final float scale; final int samples,bounces; // Progressive resolution multiplier, nominal frame samples, and maximum path depth.
        /**
         * Stores a preset's progressive scale and per-frame tracing limits.
         *
         * @param scale progressive resolution multiplier
         * @param samples nominal paths per pixel added per rendered frame
         * @param bounces maximum path depth
         */
        Quality(float scale,int samples,int bounces){this.scale=scale;this.samples=samples;this.bounces=bounces;}
    }
    private Quality quality=Quality.HIGH; // Current quality preset controlling sampling cost.
    private int compute,present,denoise,vao,accumulation,atlas,guide; // Owned program, vertex-array, radiance, texture-array, and geometry-guide names.
    private final int[] filtered=new int[2]; // Owned ping-pong diffuse radiance textures for spatial filtering.
    private boolean denoising=true; // Whether presentation selects spatially filtered radiance.
    private boolean filterDirty=true; // Whether newly traced samples require another spatial filter pass.
    private final Thread owner=Thread.currentThread(); // Creating GL thread required by render.
    private final int[] buffers=new int[3]; // Owned triangle, BVH-node, and emitter shader-storage buffers.
    private int width,height,samples,maxSamples=4096,triangleCount,nodeCount,emitterCount; // Target pixel dimensions, nominal sampling budget, and uploaded scene counts.
    private long signature=Long.MIN_VALUE,buildCount; // Last uploaded scene signature and successful scene rebuild count.
    private final PathTracingScene.Collector sceneCollector=new PathTracingScene.Collector();
    private float[] cameraMatrix; // Copied combined matrix used to detect camera movement.
    private float exposure=1,skyR=.025f,skyG=.035f,skyB=.055f; // Presentation exposure and constant linear HDR sky radiance.
    private boolean invalid=true,closed; // Forced scene-rebuild flag and GPU resource lifetime flag.
    /**
     * Creates compute, denoising, and presentation programs plus scene storage
     * buffers on the current OpenGL thread. Render-target textures are allocated
     * lazily on the first nonempty viewport. Runtime construction failures release
     * resources allocated so far.
     *
     * @throws IllegalStateException if OpenGL 4.3 is unavailable or shader compilation/linking fails
     */
    public PathTracer3D(){
        if(!GL.getCapabilities().OpenGL43)throw new IllegalStateException("PathTracer3D requires an OpenGL 4.3 context; request contextVersion(4,3), or use Lighting3D.");
        try {
            compute=program(new int[]{GL_COMPUTE_SHADER},new String[]{ShaderSources.load("pathtrace/trace.comp")});
            denoise=program(new int[]{GL_COMPUTE_SHADER},new String[]{ShaderSources.load("pathtrace/denoise.comp")});
            present=program(new int[]{GL_VERTEX_SHADER,GL_FRAGMENT_SHADER},new String[]{ShaderSources.load("pathtrace/present.vert"),ShaderSources.load("pathtrace/present.frag")});
            vao=glGenVertexArrays();for(int i=0;i<3;i++)buffers[i]=glGenBuffers();
        } catch(RuntimeException e){close();throw e;}
    }
    /**
     * Changes the tracing preset, resetting the nominal sample count and temporal
     * history when the value differs. Progressive resolution changes are applied
     * on the next render; realtime resolution remains the full viewport.
     *
     * @param quality nonnull tracing preset
     * @return this renderer
     * @throws NullPointerException if quality is null
     */
    public PathTracer3D setQuality(Quality quality){Objects.requireNonNull(quality);if(this.quality!=quality){this.quality=quality;samples=0;historyValid=false;}return this;}
    /**
     * Returns the current tracing preset, initially HIGH.
     *
     * @return active quality setting
     */
    public Quality getQuality(){return quality;}
    /**
     * Switches rendering algorithms and invalidates their history. Realtime mode
     * renders at viewport resolution and validates reprojected surface history.
     *
     * @param mode nonnull rendering mode
     * @return this renderer
     */
    public PathTracer3D setRenderMode(RenderMode mode){Objects.requireNonNull(mode);if(renderMode!=mode){renderMode=mode;samples=0;historyValid=false;}return this;}
    /**
     * Returns whether the renderer uses temporal reprojection or stationary
     * progressive accumulation.
     *
     * @return active rendering mode
     */
    public RenderMode getRenderMode(){return renderMode;}
    /**
     * Enables or disables edge-aware spatial filtering of the presentation images.
     * The raw accumulated radiance is preserved, so toggling this does not restart
     * tracing or change the nominal sample count.
     *
     * @param enabled whether to present denoised radiance
     * @return this renderer
     */
    public PathTracer3D setDenoising(boolean enabled){denoising=enabled;return this;}
    /**
     * Reports whether presentation uses edge-aware filtered radiance.
     *
     * @return current denoising flag, initially true
     */
    public boolean isDenoising(){return denoising;}
    /**
     * Sets the positive presentation exposure multiplier without resetting accumulated
     * radiance. The tone-mapping presentation shader applies it on the next render.
     *
     * @param value finite positive exposure
     * @return this renderer
     * @throws IllegalArgumentException if value is nonfinite or nonpositive
     */
    public PathTracer3D setExposure(float value){if(!Float.isFinite(value)||value<=0)throw new IllegalArgumentException("Exposure must be positive");exposure=value;return this;}
    /**
     * Sets constant linear HDR environment radiance. Changed components reset
     * sampling and temporal history because they alter light transport. Emissive
     * scene triangles supply area-light contributions separately.
     *
     * @param r nonnegative red radiance
     * @param g nonnegative green radiance
     * @param b nonnegative blue radiance
     * @return this renderer
     * @throws IllegalArgumentException if a component is negative or the component sum is nonfinite
     */
    public PathTracer3D setSky(float r,float g,float b){if(!Float.isFinite(r+g+b)||Math.min(r,Math.min(g,b))<0)throw new IllegalArgumentException("Sky must be finite and nonnegative");if(r!=skyR||g!=skyG||b!=skyB)samples=0;if(r!=skyR||g!=skyG||b!=skyB)historyValid=false;skyR=r;skyG=g;skyB=b;return this;}
    /**
     * Sets the nominal accumulation/history budget without discarding existing
     * samples. Lowering it below the current count stops new tracing until another
     * change resets accumulation; raising it permits tracing to resume.
     *
     * @param value sample limit from 1 through 1,000,000
     * @return this renderer
     * @throws IllegalArgumentException if value is outside the supported range
     */
    public PathTracer3D setMaxSamples(int value){if(value<1||value>1000000)throw new IllegalArgumentException("Samples must be in [1,1000000]");maxSamples=value;return this;}
    /**
     * Forces scene data and texture-atlas reconstruction on the next render and
     * clears accumulation/history eligibility. Use after in-place model geometry
     * or borrowed texture-pixel edits, which scene identity hashing cannot detect.
     */
    public void invalidate(){invalid=true;samples=0;historyValid=false;}
    /**
     * Returns the nominal sample count for the current view. It resets on camera,
     * scene, mode, or relevant setting changes. Realtime reprojection may reuse
     * history independently, so this is not a total path or ray counter.
     *
     * @return current nominal accumulated samples
     */
    public int getAccumulatedSamples(){return samples;}
    /**
     * Returns the stationary accumulation budget. Realtime glossy pixels may use
     * additional paths per nominal sample; this is not a total ray counter.
     * @return configured positive nominal sample limit
     */
    public int getMaxSamples(){return maxSamples;}
    /**
     * Returns the number of nondegenerate world triangles in the last uploaded
     * scene snapshot, including off-camera geometry.
     *
     * @return uploaded triangle count
     */
    public int getTriangleCount(){return triangleCount;}
    /**
     * Returns the node count of the last uploaded CPU-built bounding-volume hierarchy.
     *
     * @return uploaded BVH node count
     */
    public int getBvhNodeCount(){return nodeCount;}
    /**
     * Counts successful scene-data and texture-atlas rebuilds. Camera-only changes
     * normally reset samples without rebuilding scene geometry.
     *
     * @return cumulative scene rebuild count
     */
    public long getSceneBuildCount(){return buildCount;}
    /**
     * Traces and presents the scene into the current draw framebuffer and viewport.
     * Returns immediately for an empty viewport; otherwise rebuilds the camera,
     * checks the scene signature, and refreshes packed geometry and textures when
     * needed. Visible direct renderables must be supported triangle-model instances.
     * <p>
     * Adds up to the quality preset's nominal samples while below the configured
     * limit. Realtime mode validates reprojected history at full viewport resolution;
     * progressive mode uses the preset scale. Optional spatial filtering precedes
     * the fullscreen presentation draw. Existing depth contents are preserved.
     * </p>
     * <p>
     * Restores the bindings and enable flags captured by the internal state guard,
     * including on failure. The current scissor and color-write mask remain in effect
     * during presentation. All calls belong on the creating thread with its compatible
     * OpenGL context current. Scene models and textures remain borrowed.
     * </p>
     *
     * @param scene nonnull scene to sample
     * @param camera nonnull camera, rebuilt for the current viewport
     * @throws NullPointerException if scene or camera is null
     * @throws IllegalStateException if called off the creating thread, after closure, or with an unusable transform
     * @throws IllegalArgumentException if scene renderables or texture storage cannot be represented
     */
    public void render(Scene3D scene,Camera3D camera){
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Use path tracer on its creating GL thread");
        if(closed)throw new IllegalStateException("Path tracer is closed");Objects.requireNonNull(scene);Objects.requireNonNull(camera);
        try(State saved=new State()){
            int[] viewport=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);if(viewport[2]<1||viewport[3]<1)return;
            camera.rebuild(viewport[2],viewport[3]);float[] matrix=camera.getCombined().get(matrixUpload);
            boolean cameraMoving=!Arrays.equals(cameraMatrix,matrix);
            if(cameraMoving){cameraMatrix=matrix.clone();samples=0;}
            if(previousPerspective!=(camera instanceof PerspectiveCamera))historyValid=false;
            PathTracingScene snapshot=sceneCollector.capture(scene);
            if(invalid||signature!=snapshot.signature()){
                snapshot.build();glBindBuffer(GL_SHADER_STORAGE_BUFFER,buffers[0]);glBufferData(GL_SHADER_STORAGE_BUFFER,snapshot.triangleData(),GL_STATIC_DRAW);
                glBindBuffer(GL_SHADER_STORAGE_BUFFER,buffers[1]);glBufferData(GL_SHADER_STORAGE_BUFFER,snapshot.nodeData(),GL_STATIC_DRAW);
                glBindBuffer(GL_SHADER_STORAGE_BUFFER,buffers[2]);glBufferData(GL_SHADER_STORAGE_BUFFER,snapshot.emitterData(),GL_STATIC_DRAW);
                uploadAtlas(snapshot.textures);triangleCount=snapshot.triangles.size();nodeCount=snapshot.nodes.size();emitterCount=snapshot.emitters.size();
                signature=snapshot.signature();invalid=false;samples=0;historyValid=false;buildCount++;
            }
            boolean live=renderMode==RenderMode.REALTIME;
            float scale=live?1:quality.scale;
            int w=Math.max(1,Math.round(viewport[2]*scale)),h=Math.max(1,Math.round(viewport[3]*scale));
            if(w!=width||h!=height){
                if(accumulation!=0)glDeleteTextures(accumulation);accumulation=glGenTextures();glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,accumulation);
                glTexStorage2D(GL_TEXTURE_2D,1,GL_RGBA32F,w,h);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
                width=w;height=h;samples=0;historyValid=false;
                glDeleteTextures(guide);guide=floatTexture(w,h);
                for(int i=0;i<2;i++){glDeleteTextures(filtered[i]);filtered[i]=floatTexture(w,h);}
                glDeleteTextures(specularRadiance);specularRadiance=floatTexture(w,h);glDeleteTextures(materialGuide);materialGuide=floatTexture(w,h);
                for(int i=0;i<2;i++){glDeleteTextures(filteredSpecular[i]);filteredSpecular[i]=floatTexture(w,h);}
                glDeleteTextures(historySpecular);historySpecular=0;
                glDeleteTextures(historyRadiance);historyRadiance=0;glDeleteTextures(historyGuide);historyGuide=0;
            }
            if(live&&historyRadiance==0){historyRadiance=floatTexture(w,h);historyGuide=floatTexture(w,h);historySpecular=floatTexture(w,h);}
            if(samples<maxSamples){
                if(live&&historyValid){int swap=accumulation;accumulation=historyRadiance;historyRadiance=swap;swap=guide;guide=historyGuide;historyGuide=swap;}
                if(live&&historyValid){int swap=specularRadiance;specularRadiance=historySpecular;historySpecular=swap;}
                glUseProgram(compute);for(int i=0;i<3;i++)glBindBufferBase(GL_SHADER_STORAGE_BUFFER,i,buffers[i]);
                glBindImageTexture(0,accumulation,0,false,0,GL_READ_WRITE,GL_RGBA32F);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D_ARRAY,atlas);glBindSampler(0,0);
                glBindImageTexture(1,guide,0,false,0,GL_WRITE_ONLY,GL_RGBA32F);
                glBindImageTexture(2,specularRadiance,0,false,0,GL_READ_WRITE,GL_RGBA32F);glBindImageTexture(6,materialGuide,0,false,0,GL_READ_WRITE,GL_RGBA32F);
                uniform("atlas",0);uniform("triangleCount",triangleCount);uniform("emitterCount",emitterCount);uniform("sampleOffset",samples);
                int frameSequence=sequence++;float jitterX=live?radicalInverse((frameSequence&1023)+1,2):.5f,jitterY=live?radicalInverse((frameSequence&1023)+1,3):.5f;
                glUniform2f(glGetUniformLocation(compute,"pixelJitter"),jitterX,jitterY);glUniform2f(glGetUniformLocation(compute,"previousJitter"),previousJitterX,previousJitterY);
                uniform("realtime",live?1:0);uniform("historyValid",live&&historyValid?1:0);uniform("sequence",frameSequence);uniform("cameraMoving",cameraMoving?1:0);uniform("maxHistory",maxSamples);
                if(live){
                    glBindImageTexture(3,historyRadiance,0,false,0,GL_READ_ONLY,GL_RGBA32F);glBindImageTexture(4,historyGuide,0,false,0,GL_READ_ONLY,GL_RGBA32F);
                    glBindImageTexture(5,historySpecular,0,false,0,GL_READ_ONLY,GL_RGBA32F);
                    if(historyValid){glUniformMatrix4fv(glGetUniformLocation(compute,"previousCamera"),false,previousMatrix);glUniformMatrix4fv(glGetUniformLocation(compute,"previousInverse"),false,previousInverse);
                        glUniform3fv(glGetUniformLocation(compute,"previousPosition"),previousPosition);uniform("previousPerspective",previousPerspective?1:0);}
                }
                int count=Math.min(quality.samples,maxSamples-samples);uniform("samplesPerFrame",count);uniform("maxBounces",quality.bounces);uniform("perspective",camera instanceof PerspectiveCamera?1:0);
                glUniformMatrix4fv(glGetUniformLocation(compute,"inverseCamera"),false,camera.getInverseCombined().get(inverseUpload));
                var p=camera.getPosition();glUniform3f(glGetUniformLocation(compute,"cameraPosition"),p.x(),p.y(),p.z());
                glUniform3f(glGetUniformLocation(compute,"sky"),skyR,skyG,skyB);
                glDispatchCompute((width+7)/8,(height+7)/8,1);glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT|GL_TEXTURE_FETCH_BARRIER_BIT);samples+=count;filterDirty=true;
                if(live){previousMatrix=matrix.clone();previousInverse=camera.getInverseCombined().get(inverseUpload).clone();previousPosition[0]=p.x();previousPosition[1]=p.y();previousPosition[2]=p.z();previousPerspective=camera instanceof PerspectiveCamera;historyValid=true;}
                previousJitterX=jitterX;previousJitterY=jitterY;
            }
            int output=accumulation,specularOutput=specularRadiance;
            if(denoising&&filterDirty){glUseProgram(denoise);glBindImageTexture(1,guide,0,false,0,GL_READ_ONLY,GL_RGBA32F);
                glUniform1i(glGetUniformLocation(denoise,"realtime"),live?1:0);
                glBindImageTexture(3,materialGuide,0,false,0,GL_READ_ONLY,GL_RGBA32F);
                glUniform1f(glGetUniformLocation(denoise,"colorSigma"),Math.max(.15f,3f/(float)Math.sqrt(samples)));
                for(int pass=0;pass<3;pass++){glBindImageTexture(0,output,0,false,0,GL_READ_ONLY,GL_RGBA32F);output=filtered[pass%2];glBindImageTexture(2,output,0,false,0,GL_WRITE_ONLY,GL_RGBA32F);
                    glBindImageTexture(4,specularOutput,0,false,0,GL_READ_ONLY,GL_RGBA32F);specularOutput=filteredSpecular[pass%2];glBindImageTexture(5,specularOutput,0,false,0,GL_WRITE_ONLY,GL_RGBA32F);
                    glUniform1i(glGetUniformLocation(denoise,"stepWidth"),1<<pass);glDispatchCompute((width+7)/8,(height+7)/8,1);glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT|GL_TEXTURE_FETCH_BARRIER_BIT);}
                filterDirty=false;
            }
            if(denoising){output=filtered[0];specularOutput=filteredSpecular[0];}
            glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);glDisable(GL_CULL_FACE);glDisable(GL_FRAMEBUFFER_SRGB);
            glUseProgram(present);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,output);glBindSampler(0,0);
            glUniform1i(glGetUniformLocation(present,"resultImage"),0);glUniform1f(glGetUniformLocation(present,"exposure"),exposure);
            glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,specularOutput);glBindSampler(1,0);glUniform1i(glGetUniformLocation(present,"specularImage"),1);
            glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,materialGuide);glBindSampler(2,0);glUniform1i(glGetUniformLocation(present,"materialImage"),2);
            glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,3);
        }
    }
    /**
     * Sets one integer uniform on the bound tracing compute program. Looks up the
     * location by name for each call; an inactive uniform is ignored by OpenGL.
     *
     * @param name compute-shader uniform name
     * @param value integer value
     */
    private void uniform(String name,int value){glUniform1i(glGetUniformLocation(compute,name),value);}
    /**
     * Reverses base-N digits into a subpixel fraction for the common primary ray
     * and geometry guide. The bounded frame sequence prevents precision drift.
     * @param index positive sample index
     * @param base digit radix, two or three for the camera sequence
     * @return fraction in the half-open interval [0,1)
     */
    private static float radicalInverse(int index,int base){float value=0,weight=1f/base;while(index>0){value+=(index%base)*weight;index/=base;weight/=base;}return value;}
    /**
     * Allocates one immutable RGBA32F 2D texture with linear filtering and edge
     * clamping. Binds it on the current active texture unit; the caller owns deletion
     * and restoration of the binding.
     *
     * @param w positive width in pixels
     * @param h positive height in pixels
     * @return owned OpenGL texture name
     */
    private static int floatTexture(int w,int h){int id=glGenTextures();glBindTexture(GL_TEXTURE_2D,id);glTexStorage2D(GL_TEXTURE_2D,1,GL_RGBA32F,w,h);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);return id;}
    /**
     * Replaces the owned texture array with 512-by-512 RGBA8 layers. Reads each
     * borrowed GPU texture's base level and bilinearly resamples with repeating edge
     * coordinates, so retained decoder pixels are unnecessary. Empty texture lists
     * still allocate one layer. The surrounding state guard supplies neutral pixel
     * transfer settings and restores bindings.
     *
     * @param textures borrowed source textures in snapshot layer order
     * @throws IllegalArgumentException if layer count exceeds the GL limit or a texture has no storage
     * @throws ArithmeticException if a source readback allocation size overflows
     */
    private void uploadAtlas(List<Texture> textures){
        if(textures.size()>glGetInteger(GL_MAX_ARRAY_TEXTURE_LAYERS))throw new IllegalArgumentException("Too many path tracing textures");
        if(atlas!=0)glDeleteTextures(atlas);atlas=glGenTextures();glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D_ARRAY,atlas);
        final int side=512;glTexStorage3D(GL_TEXTURE_2D_ARRAY,1,GL_RGBA8,side,side,Math.max(1,textures.size()));
        glTexParameteri(GL_TEXTURE_2D_ARRAY,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D_ARRAY,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY,GL_TEXTURE_WRAP_S,GL_REPEAT);glTexParameteri(GL_TEXTURE_2D_ARRAY,GL_TEXTURE_WRAP_T,GL_REPEAT);
        // Read only on scene changes. GPU-owned textures work too; no dependency on retained decoder buffers.
        for(int layer=0;layer<textures.size();layer++){
            glBindTexture(GL_TEXTURE_2D,textures.get(layer).getTextureID());int w=glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_WIDTH),h=glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_HEIGHT);
            if(w<1||h<1)throw new IllegalArgumentException("Texture has no storage");
            ByteBuffer source=MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(w,h),4)),target=MemoryUtil.memAlloc(side*side*4);
            try{glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_UNSIGNED_BYTE,source);
                for(int y=0;y<side;y++)for(int x=0;x<side;x++){
                    float sx=(x+.5f)*w/side-.5f,sy=(y+.5f)*h/side-.5f;int x0=(int)Math.floor(sx),y0=(int)Math.floor(sy);float fx=sx-x0,fy=sy-y0;
                    for(int c=0;c<4;c++){float a=source.get((Math.floorMod(y0,h)*w+Math.floorMod(x0,w))*4+c)&255,b=source.get((Math.floorMod(y0,h)*w+Math.floorMod(x0+1,w))*4+c)&255;
                        float d=source.get((Math.floorMod(y0+1,h)*w+Math.floorMod(x0,w))*4+c)&255,e=source.get((Math.floorMod(y0+1,h)*w+Math.floorMod(x0+1,w))*4+c)&255;
                        target.put((byte)Math.round((a+(b-a)*fx)*(1-fy)+(d+(e-d)*fx)*fy));}
                }target.flip();glTexSubImage3D(GL_TEXTURE_2D_ARRAY,0,0,0,layer,side,side,1,GL_RGBA,GL_UNSIGNED_BYTE,target);
            }finally{MemoryUtil.memFree(source);MemoryUtil.memFree(target);}
        }
    }
    /**
     * Compiles the supplied shader stages and links an owned OpenGL program.
     * Deletes temporary shader objects on every exit and deletes the program on
     * runtime failure.
     *
     * @param types shader-stage enums, one per source
     * @param sources matching GLSL source strings
     * @return linked program name owned by the caller
     * @throws IllegalStateException if compilation or linking fails
     */
    private static int program(int[] types,String[] sources){int p=glCreateProgram();List<Integer> shaders=new ArrayList<>();try{
        for(int i=0;i<types.length;i++){int s=glCreateShader(types[i]);shaders.add(s);glShaderSource(s,sources[i]);glCompileShader(s);if(glGetShaderi(s,GL_COMPILE_STATUS)==GL_FALSE)throw new IllegalStateException(glGetShaderInfoLog(s));glAttachShader(p,s);}
        glLinkProgram(p);if(glGetProgrami(p,GL_LINK_STATUS)==GL_FALSE)throw new IllegalStateException(glGetProgramInfoLog(p));return p;
    }catch(RuntimeException e){glDeleteProgram(p);throw e;}finally{for(int s:shaders)glDeleteShader(s);}}
    /**
     * Deletes owned programs, vertex array, scene buffers, atlas, accumulation,
     * history, guide, and filtering textures. Repeated calls have no effect.
     * Call with the creating OpenGL context current; this method does not enforce
     * the render method's thread check. Borrowed models and textures are untouched.
     */
    @Override public void close(){if(closed)return;closed=true;glDeleteProgram(compute);glDeleteProgram(present);glDeleteProgram(denoise);glDeleteVertexArrays(vao);glDeleteTextures(specularRadiance);glDeleteTextures(historySpecular);glDeleteTextures(materialGuide);glDeleteTextures(filteredSpecular);glDeleteTextures(accumulation);glDeleteTextures(historyRadiance);glDeleteTextures(historyGuide);glDeleteTextures(atlas);glDeleteTextures(guide);glDeleteTextures(filtered);for(int b:buffers)glDeleteBuffers(b);}
    /**
     * Captures the GL bindings and enable flags modified by path tracing, including
     * seven image units, three shader-storage ranges, texture/sampler units zero
     * through two, and pixel-transfer configuration. Establishes tightly packed CPU
     * pixel transfers for atlas readback/upload. Used as a lexical restoration guard;
     * it does not own the resources whose names it records.
     * @author Albert Beaupre
     */
    private static final class State implements AutoCloseable{
        final int program=glGetInteger(GL_CURRENT_PROGRAM),vao=glGetInteger(GL_VERTEX_ARRAY_BINDING),active=glGetInteger(GL_ACTIVE_TEXTURE),buffer=glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING); // Captured program, vertex array, active texture unit, and generic storage-buffer binding.
        final int pack=glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING),unpack=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING); // Captured pixel readback and upload buffer bindings.
        final int[] pixelNames={GL_PACK_ALIGNMENT,GL_PACK_ROW_LENGTH,GL_PACK_SKIP_PIXELS,GL_PACK_SKIP_ROWS,GL_UNPACK_ALIGNMENT,GL_UNPACK_ROW_LENGTH,GL_UNPACK_SKIP_PIXELS,GL_UNPACK_SKIP_ROWS,GL_UNPACK_IMAGE_HEIGHT,GL_UNPACK_SKIP_IMAGES}; // Pixel-transfer settings temporarily normalized by this guard.
        final int[] pixelValues=new int[pixelNames.length],indexed=new int[3];final long[] starts=new long[3],sizes=new long[3]; // Captured pixel settings and indexed shader-storage buffer names, offsets, and sizes.
        final int[] imageNames={GL_IMAGE_BINDING_NAME,GL_IMAGE_BINDING_LEVEL,GL_IMAGE_BINDING_LAYERED,GL_IMAGE_BINDING_LAYER,GL_IMAGE_BINDING_ACCESS,GL_IMAGE_BINDING_FORMAT};final int[][] images=new int[7][6]; // Image binding property enums and saved values for image units zero through six.
        final int[] otherTextures=new int[2],otherSamplers=new int[2]; // Captured 2D texture and sampler bindings for units one and two.
        final int texture,array,sampler;final boolean depth=glIsEnabled(GL_DEPTH_TEST),blend=glIsEnabled(GL_BLEND),cull=glIsEnabled(GL_CULL_FACE),srgb=glIsEnabled(GL_FRAMEBUFFER_SRGB); // Captured unit-zero texture/sampler bindings and rendering capability flags.
        /**
         * Captures tracing-related bindings and flags, then unbinds pixel-transfer
         * buffers and selects tightly packed pixel storage. Does not restore until close;
         * construction and closure require the same current GL context.
         */
        State(){for(int i=0;i<2;i++){glActiveTexture(GL_TEXTURE1+i);otherTextures[i]=glGetInteger(GL_TEXTURE_BINDING_2D);otherSamplers[i]=glGetIntegeri(GL_SAMPLER_BINDING,i+1);}glActiveTexture(GL_TEXTURE0);texture=glGetInteger(GL_TEXTURE_BINDING_2D);array=glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);sampler=glGetIntegeri(GL_SAMPLER_BINDING,0);
            for(int i=0;i<7;i++)for(int j=0;j<6;j++)images[i][j]=glGetIntegeri(imageNames[j],i);
            for(int i=0;i<3;i++){indexed[i]=glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING,i);starts[i]=glGetInteger64i(GL_SHADER_STORAGE_BUFFER_START,i);sizes[i]=glGetInteger64i(GL_SHADER_STORAGE_BUFFER_SIZE,i);}
            glBindBuffer(GL_PIXEL_PACK_BUFFER,0);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
            for(int i=0;i<pixelNames.length;i++){pixelValues[i]=glGetInteger(pixelNames[i]);glPixelStorei(pixelNames[i],i==0||i==4?1:0);}
        }
        /**
         * Restores indexed buffer ranges, image bindings, pixel-transfer configuration,
         * textures, samplers, program, vertex array, and captured enable flags. Does not
         * delete any recorded resource or restore framebuffer/viewport values, which
         * the guarded renderer does not change.
         */
        public void close(){for(int i=0;i<3;i++){if(indexed[i]!=0&&sizes[i]>0)glBindBufferRange(GL_SHADER_STORAGE_BUFFER,i,indexed[i],starts[i],sizes[i]);else glBindBufferBase(GL_SHADER_STORAGE_BUFFER,i,indexed[i]);}
            glBindBuffer(GL_SHADER_STORAGE_BUFFER,buffer);for(int i=0;i<7;i++){int[] im=images[i];glBindImageTexture(i,im[0],im[1],im[2]!=0,im[3],im[4],im[5]);}
            for(int i=0;i<pixelNames.length;i++)glPixelStorei(pixelNames[i],pixelValues[i]);glBindBuffer(GL_PIXEL_PACK_BUFFER,pack);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpack);
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);glBindTexture(GL_TEXTURE_2D_ARRAY,array);glBindSampler(0,sampler);for(int i=0;i<2;i++){glActiveTexture(GL_TEXTURE1+i);glBindTexture(GL_TEXTURE_2D,otherTextures[i]);glBindSampler(i+1,otherSamplers[i]);}glActiveTexture(active);glUseProgram(program);glBindVertexArray(vao);
            enable(GL_DEPTH_TEST,depth);enable(GL_BLEND,blend);enable(GL_CULL_FACE,cull);enable(GL_FRAMEBUFFER_SRGB,srgb);
        }
        /**
         * Restores one boolean OpenGL capability to a captured value.
         *
         * @param flag capability enum
         * @param value desired enabled state
         */
        private static void enable(int flag,boolean value){if(value)glEnable(flag);else glDisable(flag);}
    }
}
