import http from 'node:http';
import {readFile,stat} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('./build/dist/',import.meta.url));
const types={'.html':'text/html','.js':'text/javascript','.wasm':'application/wasm','.css':'text/css','.json':'application/json'};
const server=http.createServer(async(req,res)=>{
    try {
        const pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
        const file=path.resolve(root,'.'+(pathname==='/'?'/index.html':pathname));
        if(!file.startsWith(root)){res.writeHead(403);res.end();return;}
        if(!(await stat(file)).isFile())throw new Error('Not a file');
        res.writeHead(200,{'Content-Type':types[path.extname(file)]||'application/octet-stream','Cache-Control':'no-store'});
        res.end(await readFile(file));
    }catch{res.writeHead(404);res.end('Not found');}
});
server.listen(Number(process.env.PORT||8095),'127.0.0.1',()=>console.log('Valthorne web: http://127.0.0.1:'+server.address().port));
