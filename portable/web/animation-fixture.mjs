// Generated, deterministic glTF fixture: a triangle translated by a linear animation.
export function animatedGlb(){
 const values=new Float32Array([-1,0,0,1,0,0,0,1,0,0,1,0,0,0,2,0,0]);
 const json={asset:{version:'2.0'},scene:0,scenes:[{nodes:[0]}],nodes:[{mesh:0}],meshes:[{primitives:[{attributes:{POSITION:0}}]}],buffers:[{byteLength:values.byteLength}],bufferViews:[{buffer:0,byteOffset:0,byteLength:36},{buffer:0,byteOffset:36,byteLength:8},{buffer:0,byteOffset:44,byteLength:24}],accessors:[{bufferView:0,componentType:5126,count:3,type:'VEC3',min:[-1,0,0],max:[1,1,0]},{bufferView:1,componentType:5126,count:2,type:'SCALAR',min:[0],max:[1]},{bufferView:2,componentType:5126,count:2,type:'VEC3'}],animations:[{samplers:[{input:1,output:2,interpolation:'LINEAR'}],channels:[{sampler:0,target:{node:0,path:'translation'}}]}]};
 const text=Buffer.from(JSON.stringify(json)),length=Math.ceil(text.length/4)*4,buffer=Buffer.alloc(28+length+values.byteLength);
 buffer.writeUInt32LE(0x46546c67,0);buffer.writeUInt32LE(2,4);buffer.writeUInt32LE(buffer.length,8);buffer.writeUInt32LE(length,12);buffer.writeUInt32LE(0x4e4f534a,16);buffer.fill(32,20,20+length);text.copy(buffer,20);
 buffer.writeUInt32LE(values.byteLength,20+length);buffer.writeUInt32LE(0x004e4942,24+length);Buffer.from(values.buffer).copy(buffer,28+length);return buffer;
}

// The mesh node stays stationary. Only its upper vertices follow the second bone
// (or the morph target), so a transform-only animation implementation cannot pass.
export function deformingGlb(skeletal=true){
 const chunks=[],views=[],accessors=[];let offset=0;
 function data(values,type,componentType=5126,min,max){
  const bytes=Buffer.from(values.buffer,values.byteOffset,values.byteLength),index=accessors.length;
  views.push({buffer:0,byteOffset:offset,byteLength:bytes.length});chunks.push(bytes);offset+=bytes.length;
  if(offset%4){const pad=Buffer.alloc(4-offset%4);chunks.push(pad);offset+=pad.length;}
  const components={SCALAR:1,VEC3:3,VEC4:4,MAT4:16}[type];accessors.push({bufferView:views.length-1,componentType,count:values.length/components,type,...(min?{min,max}:{})});return index;
 }
 const pos=data(new Float32Array([-1,0,0,1,0,0,-1,2,0,1,0,0,1,2,0,-1,2,0]),'VEC3',5126,[-1,0,0],[1,2,0]);
 const primitive={attributes:{POSITION:pos},material:0};
 const mesh={primitives:[primitive]},nodes=[{mesh:0,name:'stationary_mesh'}];
 const times=data(new Float32Array([0,1]),'SCALAR',5126,[0],[1]);let output,target,skins;
 if(skeletal){
  primitive.attributes.JOINTS_0=data(new Uint16Array([0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0]),'VEC4',5123);
  primitive.attributes.WEIGHTS_0=data(new Float32Array(Array.from({length:24},(_,i)=>i%4===0?1:0)),'VEC4');
  const inverse=data(new Float32Array([1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,1,0,0,0,0,1,0,0,0,0,1,0,0,-1,0,1]),'MAT4');
  nodes[0].skin=0;nodes.push({name:'root_joint',children:[2]},{name:'upper_joint',translation:[0,1,0]});
  skins=[{joints:[1,2],skeleton:1,inverseBindMatrices:inverse}];
  output=data(new Float32Array([0,1,0,1,1,0]),'VEC3');target={node:2,path:'translation'};
 }else{
  const delta=data(new Float32Array([0,0,0,0,0,0,1,0,0,0,0,0,1,0,0,1,0,0]),'VEC3',5126,[0,0,0],[1,0,0]);
  primitive.targets=[{POSITION:delta}];mesh.weights=[0];output=data(new Float32Array([0,1]),'SCALAR');target={node:0,path:'weights'};
 }
 const json={asset:{version:'2.0'},extensionsUsed:['KHR_materials_unlit'],scene:0,scenes:[{nodes:skeletal?[0,1]:[0]}],nodes,meshes:[mesh],...(skins?{skins}:{}),materials:[{extensions:{KHR_materials_unlit:{}},doubleSided:true,pbrMetallicRoughness:{baseColorFactor:[1,0,1,1],metallicFactor:0,roughnessFactor:1}}],buffers:[{byteLength:offset}],bufferViews:views,accessors,animations:[{samplers:[{input:times,output,interpolation:'LINEAR'}],channels:[{sampler:0,target}]}]};
 const text=Buffer.from(JSON.stringify(json)),length=Math.ceil(text.length/4)*4,binary=Buffer.concat(chunks),buffer=Buffer.alloc(28+length+binary.length);
 buffer.writeUInt32LE(0x46546c67,0);buffer.writeUInt32LE(2,4);buffer.writeUInt32LE(buffer.length,8);buffer.writeUInt32LE(length,12);buffer.writeUInt32LE(0x4e4f534a,16);buffer.fill(32,20,20+length);text.copy(buffer,20);
 buffer.writeUInt32LE(binary.length,20+length);buffer.writeUInt32LE(0x004e4942,24+length);binary.copy(buffer,28+length);return buffer;
}
