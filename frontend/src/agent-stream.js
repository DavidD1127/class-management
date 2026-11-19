export async function consumeAgentStream(response,onEvent=()=>{}){
  if(!response.ok){
    const body=await response.json().catch(()=>({}));
    const error=new Error(body.message||'助手连接失败，请重试');error.status=response.status;throw error;
  }
  if(!response.body)throw new Error('浏览器不支持流式回复');
  const reader=response.body.getReader(),decoder=new TextDecoder();
  let buffer='',result,finished=false;
  function dispatch(block){
    const lines=block.split('\n'),name=lines.find(line=>line.startsWith('event:'))?.slice(6).trim();
    const data=lines.filter(line=>line.startsWith('data:')).map(line=>line.slice(5).trimStart()).join('\n');
    if(!data)return;
    const value=JSON.parse(data);
    if(name==='failure'){const error=new Error(value.message||'助手处理失败');error.status=value.status;throw error;}
    if(name==='result'){result=value;finished=true;}
    onEvent(name,value);
  }
  try{
    while(!finished){
      const {value,done}=await reader.read();buffer+=decoder.decode(value,{stream:!done});buffer=buffer.replace(/\r\n/g,'\n');
      let end;while(!finished&&(end=buffer.indexOf('\n\n'))>=0){dispatch(buffer.slice(0,end));buffer=buffer.slice(end+2);}
      if(done){if(buffer.trim())dispatch(buffer);break;}
    }
  }finally{await reader.cancel().catch(()=>{});reader.releaseLock();}
  if(!finished)throw new Error('连接提前结束，请核对操作状态后再重试');
  return result;
}

export async function sendAgentMessage(input,onEvent){
  // 安卓 APK 里没有 SSE：桥是同步调用，整段回答一次拿到。
  // 事件顺序照旧（status → result），调用方无感。
  if(typeof window!=='undefined'&&window.CMNative){
    onEvent('status',{message:'已收到，正在理解你的指令……'});
    const raw=window.CMNative.call('POST','/api/ai/chat',JSON.stringify(input),localStorage.getItem('token')||'');
    const payload=JSON.parse(raw);
    if(payload.status===401){localStorage.removeItem('token');localStorage.removeItem('user');location.assign('/login');}
    if(payload.status!==200||!payload.body||payload.body.code!==0){
      const error=new Error(payload.body?.message||'助手处理失败');error.status=payload.status;throw error;
    }
    const result=payload.body.data;
    onEvent('result',result);
    return result;
  }
  const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),135000);
  try{
    const response=await fetch('/api/ai/chat/stream',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+localStorage.getItem('token')},body:JSON.stringify(input),signal:controller.signal});
    if(response.status===401){localStorage.removeItem('token');localStorage.removeItem('user');location.assign('/login');}
    return await consumeAgentStream(response,onEvent);
  }catch(e){if(e.name==='AbortError')throw new Error('助手处理超时，请稍后重试');throw e;}
  finally{clearTimeout(timeout);}
}
