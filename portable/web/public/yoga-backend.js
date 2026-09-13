import Yoga from './vendor/yoga/dist/src/index.js';
export class BrowserYoga {
 constructor(){this.objects=new Map();this.next=1;}
 add(value){const id=this.next++;this.objects.set(id,value);return id;}
 config(){return this.add(Yoga.Config.create());}
 node(config){return this.add(Yoga.Node.create(this.get(config)));}
 get(id){const object=this.objects.get(id);if(!object)throw new Error('Disposed layout handle');return object;}
 free(id){const object=this.objects.get(id);if(object){object.free();this.objects.delete(id);}}
 close(){// Nodes must be released before the configurations they borrow.
  for(const [id,object] of this.objects)if(typeof object.getChildCount==='function')this.free(id);
  for(const id of this.objects.keys())this.free(id);
 }
}
