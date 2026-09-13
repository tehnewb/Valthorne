// Origin-scoped, persistent Java filesystem. Flush/close completes only after the
// IndexedDB transaction commits. /tmp is intentionally session-only.
export class BrowserFiles {
 constructor(){this.nodes=new Map();this.maxFileBytes=16*1024*1024;this.maxResidentBytes=64*1024*1024;this.residentBytes=0;}
 normalize(path){const parts=[];for(const part of path.replaceAll('\\','/').split('/')){if(part==='..')parts.pop();else if(part&&part!=='.')parts.push(part);}return '/'+parts.join('/');}
 async initialize(){
  this.db=await new Promise((resolve,reject)=>{const request=indexedDB.open('valthorne-files',1);request.onupgradeneeded=()=>request.result.createObjectStore('files',{keyPath:'path'});request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);});
  const entries=await new Promise((resolve,reject)=>{const request=this.db.transaction('files').objectStore('files').getAll();request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);});
  for(const item of entries){item.bytes=item.bytes?new Uint8Array(item.bytes):null;item.length=item.bytes?.length||0;item.version=0;this.residentBytes+=item.length;this.nodes.set(item.path,item);}
  if(this.residentBytes>this.maxResidentBytes||entries.some(e=>e.length>this.maxFileBytes))throw new Error('Saved files exceed the browser filesystem memory budget');
  for(const path of ['/','/tmp','/home'])if(!this.nodes.has(path))this.nodes.set(path,{path,dir:true,readonly:false,mtime:Date.now(),version:0,length:0});
  this.db.onversionchange=()=>this.db.close();return this;
 }
 get(path){return this.nodes.get(this.normalize(path));}
 list(path){path=this.normalize(path);if(!this.get(path)?.dir)return null;const prefix=path==='/'?'/':path+'/';return [...this.nodes.keys()].filter(p=>p.startsWith(prefix)&&p!==path&&!p.slice(prefix.length).includes('/')).map(p=>p.slice(prefix.length));}
 persistent(path){return path!=='/tmp'&&!path.startsWith('/tmp/');}
 transaction(puts=[],deletes=[]){
  puts=puts.filter(n=>this.persistent(n.path));deletes=deletes.filter(p=>this.persistent(p));
  if(!puts.length&&!deletes.length)return Promise.resolve();
  return new Promise((resolve,reject)=>{
   const tx=this.db.transaction('files','readwrite'),store=tx.objectStore('files');
   tx.oncomplete=resolve;tx.onabort=tx.onerror=()=>reject(tx.error||new Error('File transaction failed'));
   for(const path of deletes)store.delete(path);
   for(const node of puts)store.put({path:node.path,dir:node.dir,readonly:node.readonly,mtime:node.mtime,bytes:node.dir?null:node.bytes.slice(0,node.length)});
  });
 }
 async create(parent,name,dir){
  parent=this.normalize(parent);const p=this.get(parent),path=this.normalize(parent+'/'+name);
  if(!p?.dir||p.readonly||!name||name.includes('/')||name==='.'||name==='..'||this.nodes.has(path))return false;
  const node={path,dir,readonly:false,mtime:Date.now(),bytes:dir?null:new Uint8Array(0),length:0,version:0};
  await this.transaction([node]);this.nodes.set(path,node);return true;
 }
 async remove(path){
  path=this.normalize(path);const node=this.get(path),parent=this.get(path.slice(0,path.lastIndexOf('/'))||'/');
  if(!node||path==='/'||parent?.readonly||node.dir&&this.list(path).length)return false;
  await this.transaction([],[path]);this.nodes.delete(path);if(!node.openCount)this.residentBytes-=node.bytes?.length||0;return true;
 }
 async move(parent,source,name,replace=false){
  parent=this.normalize(parent);source=this.normalize(source);const node=this.get(source),p=this.get(parent),destination=this.normalize(parent+'/'+name);
  const existing=this.get(destination),sourceParent=this.get(source.slice(0,source.lastIndexOf('/'))||'/');
  if(!node||source==='/'||!p?.dir||p.readonly||sourceParent?.readonly||!name||name.includes('/')||existing&&(!replace||existing.dir!==node.dir||existing.dir&&this.list(destination).length)||parent===source||parent.startsWith(source+'/'))return false;
  const old=[...this.nodes.values()].filter(n=>n.path===source||n.path.startsWith(source+'/'));
  const changed=old.map(n=>({...n,path:destination+n.path.slice(source.length)}));
  await this.transaction(changed,[...old.map(n=>n.path),...(existing?[destination]:[])]);
  if(existing&&!existing.openCount)this.residentBytes-=existing.bytes?.length||0;
  for(let i=0;i<old.length;i++){this.nodes.delete(old[i].path);old[i].path=changed[i].path;this.nodes.set(old[i].path,old[i]);}return true;
 }
 async metadata(path,kind,value){const node=this.get(path);if(!node)return false;const changed={...node,[kind]:value};await this.transaction([changed]);node[kind]=value;return true;}
 open(path,readable,writable,append){
  const node=this.get(path);if(!node||node.dir||writable&&node.readonly)return null;
  const handle={node,readable,writable,position:append?node.length:0,closed:false,dirty:false};
  if(writable&&!readable&&!append){this.resize(handle,0);handle.dirty=true;}
  node.openCount=(node.openCount||0)+1;
  return handle;
 }
 check(handle){if(!handle||handle.closed)throw new Error('File is closed');return handle.node;}
 read(handle,count){const node=this.check(handle);if(!handle.readable)throw new Error('File is not readable');const end=Math.min(node.length,handle.position+count),out=node.bytes.subarray(Math.min(handle.position,node.length),end);handle.position+=out.length;return out;}
 resize(handle,size){
  const node=this.check(handle);if(!handle.writable)throw new Error('File is not writable');
  if(!Number.isInteger(size)||size<0||size>this.maxFileBytes)throw new Error('File exceeds 16 MiB browser file budget');
  if(size>node.bytes.length){const capacity=Math.min(this.maxFileBytes,Math.max(size,Math.max(256,node.bytes.length*2))),increase=capacity-node.bytes.length;
   if(this.residentBytes+increase>this.maxResidentBytes)throw new Error('Browser filesystem exceeds 64 MiB resident budget');
   const bytes=new Uint8Array(capacity);bytes.set(node.bytes.subarray(0,node.length));node.bytes=bytes;this.residentBytes+=increase;
  }
  if(size<node.length)node.bytes.fill(0,size,node.length);else if(size>node.length)node.bytes.fill(0,node.length,size);
  node.length=size;node.mtime=Date.now();node.version++;handle.dirty=true;
 }
 write(handle,bytes){const node=this.check(handle),end=handle.position+bytes.length;if(!handle.writable)throw new Error('File is not writable');if(!bytes.length)return;if(end>node.length)this.resize(handle,end);node.bytes.set(bytes,handle.position);handle.position=end;node.version++;node.mtime=Date.now();handle.dirty=true;}
 seek(handle,position){this.check(handle);if(!Number.isInteger(position)||position<0)throw new Error('Invalid file offset');handle.position=position;}
 async flush(handle){const node=this.check(handle);if(!handle.dirty)return;const version=node.version;if(this.nodes.get(node.path)===node)await this.transaction([node]);if(node.version===version)handle.dirty=false;}
 async close(handle){if(handle.closed)return;await this.flush(handle);handle.closed=true;const node=handle.node;node.openCount--;if(!node.openCount&&this.nodes.get(node.path)!==node)this.residentBytes-=node.bytes?.length||0;handle.node=null;}
}
