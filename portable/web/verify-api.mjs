import {execFileSync} from 'node:child_process';
import assert from 'node:assert/strict';
import {readdirSync,mkdirSync,writeFileSync} from 'node:fs';
import path from 'node:path';
const [desktopJar,webClasses,javap]=process.argv.slice(2);
const coverage=[];
const generatedUI=readdirSync(path.join(webClasses,'valthorne/ui'),{recursive:true}).filter(name=>name.endsWith('.class')&&!name.includes('$')).map(name=>'ui.'+name.slice(0,-6).replaceAll('\\','.').replaceAll('/','.'));
generatedUI.push('graphics.lighting2d.Lighting2D','graphics.lighting2d.GroundShadowRenderer2D','io.file.ValthorneFiles');
generatedUI.push('asset.Assets','PlatformTools');
generatedUI.push('graphics.shader.ComputeShader','graphics.model.PathTracer3D','graphics.model.PathTracer3D$Quality','graphics.model.PathTracer3D$RenderMode');
generatedUI.push('graphics.debug.PerformanceOverlay','graphics.radiance.RadianceCascades','graphics.radiance.RadianceTexture');
generatedUI.push('graphics.texture.TexturePacker','graphics.particle.ParticleSystem','graphics.shader.ShapeShader','graphics.lighting2d.SpriteVolumeRenderer2D');
generatedUI.push(...['DynamicMesh2D','LightMapRenderer','LightMesh','LightTexture','RayHandler','SoftShadowMesh'].map(name=>'graphics.lighting.'+name));
generatedUI.push('graphics.font.slug.SlugFont','graphics.font.slug.SlugBatch');
generatedUI.push('graphics.GraphicsCapabilities','graphics.model.MeshBatch3D','graphics.model.BillboardBatch3D','graphics.model.ShadowMap3D','graphics.lighting3d.Lighting3D');
generatedUI.push(...['TiledXML','TiledMapParameters','TiledMapData','TiledMapLoader','FileSystemResolver','TiledResolvers','TiledResolvers$InMemoryResolver','TiledDecoding'].map(name=>'graphics.map.tiled.'+name));
const exactClasses=[...generatedUI,'viewport.Viewport','viewport.PerspectiveViewport','graphics.model.ObjModel3D','graphics.model.ObjModel3D$Part','graphics.model.ObjModel3D$Resolver','graphics.model.ModelParameters','graphics.model.RenderStateSnapshot3D','graphics.font.FontData','Audio','Audio$ListenerPosition','audio.sound.SoundData','audio.sound.SoundPlayer','audio.sound.WaveSoundDecoder','audio.sound.OggSoundDecoder','audio.sound.Mp3SoundDecoder','audio.sound.WaveSoundStream','audio.sound.OggSoundStream','audio.sound.Mp3SoundStream','graphics.texture.Texture','graphics.texture.TextureData','graphics.texture.TextureBatch','graphics.texture.FrameBuffer','graphics.texture.NinePatchTexture','graphics.Sprite','graphics.ImmediateTextureRenderer','graphics.shader.Shader','graphics.shader.ShaderSources','math.physics.PhysicsWorld3D','math.physics.PhysicsWorld3D$Settings','math.physics.RigidBody3D','math.physics.CollisionShape3D','math.physics.DistanceJoint3D','graphics.model.FilamentRenderer3D','graphics.model.FilamentRenderer3D$Quality','graphics.model.SceneRenderer3D','graphics.model.SceneRenderer3D$Backend'];
const classNames=[...exactClasses,'JGL','Keyboard','Mouse','Window'];
const listings=new Map();
function inspect(classpath){
 const output=execFileSync(javap,['-public','-s','-constants','-classpath',classpath,...classNames.map(name=>'valthorne.'+name)],{encoding:'utf8',maxBuffer:8*1024*1024});
 const classes=new Map();let current;
 for(const line of output.split(/\r?\n/)){
  const declaration=line.match(/^(?:public |protected )?(?:(?:final|abstract|static) )*(?:class|interface) valthorne\.([^\s<{]+)/);
  if(declaration){current=[];classes.set(declaration[1],current);}if(current)current.push(line);
 }
 assert.equal(classes.size,classNames.length,'Missing batched class listings');return classes;
}
listings.set(desktopJar,inspect(desktopJar));listings.set(webClasses,inspect(webClasses));
function api(path,name,all=false){
 const lines=listings.get(path).get(name),members=new Map();
 assert(lines,'Missing class listing: '+name);
 for(let i=0;i<lines.length;i++){
  const line=lines[i].trim();if(!line.startsWith(all?'public ':'public static ')||line.includes(' class ')||line.includes(' interface '))continue;
  const descriptor=lines[i+1]?.trim();if(!descriptor?.startsWith('descriptor:'))continue;
  const member=line.includes('(')?line.match(/([\w$]+)\(/)?.[1]:line.match(/([\w$]+)(?: = [^;]+)?;/)?.[1];
  const value=line.includes('(')?'method':line.match(/ = ([^;]+);/)?.[1]||'field';
  members.set(`${member}:${descriptor}`,`${line.includes(' static ')?'static':'instance'}:${value}`);
 }
 return members;
}
for(const name of exactClasses){
 const desktop=api(desktopJar,name,true),web=api(webClasses,name,true);
 assert(web.size>0,`Missing ${name} backend`);
 assert.deepEqual(web,desktop,`Browser public API differs: ${name}`);
 coverage.push({name,webMembers:web.size,desktopMembers:desktop.size,exact:true});
 console.log(`API_VALIDATED ${name}: ${web.size}/${desktop.size} public members`);
}
for(const name of ['JGL','Keyboard','Mouse','Window']){
 const desktop=api(desktopJar,name),web=api(webClasses,name);
 assert(web.size>0,`Missing ${name} backend`);
 for(const [member,value] of web)assert.equal(desktop.get(member),value,`Browser API differs: ${name}.${member}`);
 assert.equal(web.size,desktop.size,`Incomplete public API: ${name}`);
 coverage.push({name,webMembers:web.size,desktopMembers:desktop.size,exact:web.size===desktop.size});
 console.log(`API_VALIDATED ${name}: ${web.size}/${desktop.size} public static members`);
}
const directory=new URL('./build/verification/',import.meta.url);mkdirSync(directory,{recursive:true});writeFileSync(new URL('backend-api.json',directory),JSON.stringify({note:'Declared backend API comparison; this is not whole-engine behavioral coverage.',classes:coverage},null,2));
