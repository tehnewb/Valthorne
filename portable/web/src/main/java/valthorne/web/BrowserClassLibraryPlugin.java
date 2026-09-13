package valthorne.web;
import org.teavm.vm.spi.*;
import org.teavm.model.*;
import org.teavm.model.instructions.*;
/** Supplies Reader.transferTo, which TeaVM 0.15 Files.readString already invokes. */
public final class BrowserClassLibraryPlugin implements TeaVMPlugin {
 public void install(TeaVMHost host){host.add((ClassHolder cls,ClassHolderTransformerContext context)->{
  if(cls.getName().equals("org.teavm.runtime.fs.VirtualFileSystemProvider")){
   for(MethodHolder method:cls.getMethods())if(method.getName().equals("getInstance")||method.getName().equals("setInstance")){
    Program program=new Program();program.createVariable();BasicBlock block=program.createBasicBlock();InvokeInstruction call=new InvokeInstruction();call.setType(InvocationType.SPECIAL);call.setMethod(new MethodReference(BrowserFileSystem.class.getName(),method.getDescriptor()));
    ExitInstruction exit=new ExitInstruction();if(method.getName().equals("setInstance"))call.setArguments(program.createVariable());else{Variable result=program.createVariable();call.setReceiver(result);exit.setValueToReturn(result);}block.add(call);block.add(exit);method.setProgram(program);
   }return;
  }
  if(cls.getName().equals("java.nio.file.Files")){
   ValueType path=ValueType.object("java.nio.file.Path"),options=ValueType.arrayOf(ValueType.object("java.nio.file.CopyOption"));
   MethodHolder method=cls.getMethod(new MethodDescriptor("move",path,path,options,path));
   if(method==null)throw new IllegalStateException("Review TeaVM Files.move adaptation");
   Program program=new Program();program.createVariable();Variable source=program.createVariable(),target=program.createVariable(),flags=program.createVariable(),result=program.createVariable();BasicBlock block=program.createBasicBlock();
   InvokeInstruction call=new InvokeInstruction();call.setType(InvocationType.SPECIAL);call.setMethod(new MethodReference(BrowserFileSystem.class.getName(),"move",path,path,options,path));call.setArguments(source,target,flags);call.setReceiver(result);block.add(call);ExitInstruction exit=new ExitInstruction();exit.setValueToReturn(result);block.add(exit);method.setProgram(program);return;
  }
  if(!cls.getName().equals("java.io.Reader"))return;
  MethodDescriptor descriptor=new MethodDescriptor("transferTo",ValueType.object("java.io.Writer"),ValueType.LONG);
  if(cls.getMethod(descriptor)!=null)return;
  MethodHolder method=new MethodHolder(descriptor);method.setLevel(AccessLevel.PUBLIC);Program program=new Program();
  Variable input=program.createVariable(),output=program.createVariable(),result=program.createVariable();BasicBlock block=program.createBasicBlock();
  InvokeInstruction call=new InvokeInstruction();call.setType(InvocationType.SPECIAL);call.setMethod(new MethodReference(ReaderTransfers.class.getName(),"transfer",ValueType.object("java.io.Reader"),ValueType.object("java.io.Writer"),ValueType.LONG));call.setArguments(input,output);call.setReceiver(result);block.add(call);
  ExitInstruction exit=new ExitInstruction();exit.setValueToReturn(result);block.add(exit);method.setProgram(program);cls.addMethod(method);
 });}
}
