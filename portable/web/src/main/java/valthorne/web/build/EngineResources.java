package valthorne.web.build;
import org.teavm.classlib.ResourceSupplier;
import org.teavm.classlib.ResourceSupplierContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
/** Build-time resource registration for unchanged Class.getResourceAsStream callers. */
@SuppressWarnings("deprecation")
public final class EngineResources implements ResourceSupplier {
 public String[] supplyResources(ResourceSupplierContext context){
  try(var input=context.getClassLoader().getResourceAsStream("META-INF/valthorne-resources.list")){
   if(input==null)throw new IllegalStateException("Missing browser resource manifest");
   return new String(input.readAllBytes(),StandardCharsets.UTF_8).lines().filter(name->!name.isBlank()).toArray(String[]::new);
  }catch(IOException error){throw new UncheckedIOException(error);}
 }
}
