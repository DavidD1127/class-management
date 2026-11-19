import assert from 'node:assert/strict';
import {consumeAgentStream} from './agent-stream.js';
const bytes=new TextEncoder().encode('event:status\r\ndata:{"message":"正在核对学生"}\r\n\r\nevent:result\ndata:{"answer":"已保存记录","operation":{"status":"SUCCEEDED"}}\n\n');
const stream=new ReadableStream({start(controller){for(let i=0;i<bytes.length;i+=3)controller.enqueue(bytes.slice(i,i+3));controller.close();}});
const progress=[];
const result=await consumeAgentStream(new Response(stream),(event,value)=>{if(event==='status')progress.push(value.message);});
assert.equal(result.answer,'已保存记录');assert.deepEqual(progress,['正在核对学生']);
await assert.rejects(consumeAgentStream(new Response('event:failure\ndata:{"message":"请补充学号"}\n\n')),/请补充学号/);
await assert.rejects(consumeAgentStream(new Response('event:status\ndata:{"message":"处理中"}\n\n')),/连接提前结束/);
let cancelled=false,timer;
const keptOpen=new ReadableStream({start(controller){controller.enqueue(new TextEncoder().encode('event:result\ndata:{"answer":"完成"}\n\n'));},cancel(){cancelled=true;}});
try{
  const completed=await Promise.race([consumeAgentStream(new Response(keptOpen)),new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('Final result must not wait for EOF')),1000);})]);
  assert.equal(completed.answer,'完成');assert.equal(cancelled,true);
}finally{clearTimeout(timer);}
console.log('Agent stream checks passed: split UTF-8, progress, result, failure and terminal result without EOF.');
