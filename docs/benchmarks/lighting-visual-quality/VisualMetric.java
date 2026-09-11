import javax.imageio.ImageIO;
import java.nio.file.Path;
class VisualMetric {
 public static void main(String[] args)throws Exception{
 var ref=ImageIO.read(Path.of("build/visual-baseline/reference-raw.png").toFile());
 int[][] regions={{273,205,368,298},{150,210,242,290},{170,65,455,180}};String[] labels={"glass","metal","wall"};
 for(String folder:args){var img=ImageIO.read(Path.of("build",folder,"motion-75.png").toFile());System.out.print(folder);for(int k=0;k<3;k++){double sum=0;int n=0;var r=regions[k];for(int y=r[1];y<r[3];y++)for(int x=r[0];x<r[2];x++)for(int c=0;c<3;c++){int a=(ref.getRGB(x,y)>>(c*8))&255,b=(img.getRGB(x,y)>>(c*8))&255;sum+=(a-b)*(a-b);n++;}System.out.printf(" %s=%.4f",labels[k],Math.sqrt(sum/n));}System.out.println();}
 }
}