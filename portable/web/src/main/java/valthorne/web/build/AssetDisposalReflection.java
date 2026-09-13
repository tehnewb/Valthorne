package valthorne.web.build;
import java.util.*;
import org.teavm.classlib.*;
import org.teavm.model.*;
/** Preserve the original Assets public dispose() convention for user classes. */
public final class AssetDisposalReflection implements ReflectionSupplier {
 public Collection<MethodDescriptor> getAccessibleMethods(ReflectionContext context,String name){
  ClassReader type=context.getClassSource().get(name);if(type==null)return List.of();
  List<MethodDescriptor> methods=new ArrayList<>();
  for(MethodReader method:type.getMethods())if(method.getName().equals("dispose")&&method.parameterCount()==0&&method.getLevel()==AccessLevel.PUBLIC)methods.add(method.getDescriptor());
  return methods;
 }
}
