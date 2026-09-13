package compatibility;
import valthorne.*;
import valthorne.graphics.Color;
import valthorne.graphics.map.tiled.*;
import java.nio.charset.StandardCharsets;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureFilter;
import valthorne.io.file.ValthorneFiles;
import java.util.*;
public final class CommonTiledApplication implements Application {
 private int frames,checks;
 private TiledMap map; private TextureBatch batch;
 private final long[] samples=new long[80]; private int sampleCount;
 public static void main(String[] args){try{JGL.init(new CommonTiledApplication(),JGLConfiguration.defaults().title("Shared Tiled maps").size(640,480).visible(false));System.out.println("COMMON_TILED_RETURNED");}catch(Throwable error){error.printStackTrace();throw error;}}
 private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
 public void init(){
  Window.setSwapInterval(SwapInterval.OFF);
  byte[] xml=("<map orientation='orthogonal' width='2' height='2' tilewidth='16' tileheight='16'><properties><property name='label' value='Map &amp; world'/></properties><layer name='ground' width='2' height='2'><data encoding='csv'>0,0,0,0</data></layer></map>").getBytes(StandardCharsets.UTF_8);
  TiledMapData data=TiledMapData.load(xml,"maps/test.tmx",new FileSystemResolver());
  check(data.getWidth()==2&&data.getHeight()==2,"Map dimensions");check("Map & world".equals(data.getProperties().get("label")),"XML text and entity decoding");check(data.getMapLayers().size()==1,"Tile layer");data.dispose();
  var loader=new TiledMapLoader();
  data=loader.load(TiledMapParameters.fromPath("portable/compatibility/assets/maps/arena.tmx"));
  check(data.getTileSetData().size()==1,"External TSX");check(data.getTileSetData().get(0).textureData().width()==2,"Nested relative image");
  map=data.asTiledMap();data.dispose();batch=new TextureBatch(64);
  check(map.getWidth()==4&&map.getHeight()==3,"Filesystem map dimensions");
  check(((TiledObjectMapLayer)map.getLayer("objects")).getObjects().size()==1,"Object layer");
  check(map.getTile("ground",3,0).rawGid()==(int)2147483649L&&map.getTileGid("ground",3,0)==1,"Unsigned flip bit preserved");
  check("grass".equals(map.getTileDefinition("ground",3,0).properties().get("surface")),"Tile properties");
  var set=map.getTileSets().get(0);set.getTexture().setFilter(TextureFilter.NEAREST);check(set.resolveAnimatedLocalId(0,.15f)==1,"Animated tile frame");
  data=loader.load(TiledMapParameters.fromClasspath("maps/arena.tmx"));check(data.getWidth()==4&&data.getTileSetData().size()==1,"Recursive classpath dependencies");data.dispose();
  Map<String,byte[]> files=Map.of("maps/terrain.tsx",ValthorneFiles.readBytes("maps/terrain.tsx"),"colors.png",ValthorneFiles.readBytes("colors.png"));
  data=loader.load(TiledMapParameters.fromBytes(ValthorneFiles.readBytes("maps/arena.tmx"),"maps/arena.tmx",files));check(data.getWidth()==4,"In-memory dependencies");data.dispose();
  try{
   int[] expected={1,2,3,(int)2147483649L};java.nio.ByteBuffer ids=java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.LITTLE_ENDIAN);for(int id:expected)ids.putInt(id);
   for(String compression:new String[]{"","gzip","zlib"}){
    var output=new java.io.ByteArrayOutputStream();
    if(compression.isEmpty())output.write(ids.array());else{java.io.OutputStream compressed=compression.equals("gzip")?new java.util.zip.GZIPOutputStream(output):new java.util.zip.DeflaterOutputStream(output);compressed.write(ids.array());compressed.close();}
    String encoded=Base64.getEncoder().encodeToString(output.toByteArray());encoded=encoded.substring(0,4)+" \n"+encoded.substring(4);
    check(Arrays.equals(expected,TiledDecoding.decodeLayerData(encoded,"base64",compression,4)),"MIME Base64 "+compression);
   }
  }catch(java.io.IOException e){throw new RuntimeException(e);}
  String infinite="<map infinite='1' tilewidth='16' tileheight='16'><layer name='chunks'><data encoding='csv'><chunk x='-2' y='-1' width='2' height='1'>1,2</chunk></data></layer></map>";
  data=TiledMapData.load(infinite.getBytes(StandardCharsets.UTF_8),"chunks.tmx",new FileSystemResolver());check(data.isInfinite()&&((TiledTileMapLayer)data.getMapLayers().get(0)).getChunks().size()==1,"Infinite map chunks");data.dispose();
  boolean rejected=false;try{TiledMapData.load("<map><broken></map>".getBytes(StandardCharsets.UTF_8),"bad.tmx",new FileSystemResolver());}catch(RuntimeException expected){rejected=true;}check(rejected,"Malformed XML rejected");
  System.out.println("COMMON_TILED_READY");
 }
 public void update(float dt){if(++frames>=180)Window.requestClose();}
 public void render(){long start=System.nanoTime();Window.clear(Color.NAVY);batch.begin();batch.pushTranslation(40,80);map.render(batch);batch.popTranslation();batch.end();if(frames>20&&sampleCount<samples.length)samples[sampleCount++]=System.nanoTime()-start;}
 public void dispose(){if(map!=null)map.dispose();if(batch!=null)batch.dispose();if(sampleCount>0){Arrays.sort(samples,0,sampleCount);long total=0;for(int i=0;i<sampleCount;i++)total+=samples[i];System.out.println("TILED_BENCHMARK samples="+sampleCount+" meanSubmitMs="+(total/1e6/sampleCount)+" p95SubmitMs="+(samples[(int)(sampleCount*.95)]/1e6));}System.out.println("COMMON_TILED_VALIDATED checks="+checks);}
}
