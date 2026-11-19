<template>
  <section class="ai-assistant" aria-labelledby="ai-title">
    <div class="ai-heading"><h1 id="ai-title">班级助手 <span>AI</span></h1><div class="heading-actions"><el-button text :disabled="busy" @click="settingsOpen=true">助手设置</el-button><el-button :disabled="busy||uploading" @click="reset">新建对话</el-button></div></div>
    <el-alert v-if="connection&&!connection.ready&&!isAndroid" title="助手尚未连接，请联系管理员完成配置。" type="warning" :closable="false"/>
    <el-card v-if="isAndroid&&connection&&!connection.ready" class="ai-key-card" shadow="never">
      <div class="ai-key-title">填写 DeepSeek API Key</div>
      <div class="ai-key-hint">只保存在这台手机上（系统 Keystore 加密），不上传任何服务器。</div>
      <el-input v-model="keyInput" type="password" show-password placeholder="sk-..." />
      <el-button type="primary" :loading="savingKey" style="margin-top:10px" @click="saveKey">保存并启用助手</el-button>
      <div v-if="keyError" class="ai-key-error">{{keyError}}</div>
    </el-card>
    <div ref="thread" class="ai-thread" role="log" aria-live="polite" aria-label="查询对话" :aria-busy="busy">
      <div v-if="!messages.length" class="ai-welcome"><span class="welcome-caption">你的教学工作搭档</span><h2>老师，今天需要帮你做什么？</h2><p>查班级近况、记一次考勤，或整理一份学生名单。</p><div class="ai-examples"><button v-for="example in examples" :key="example.prompt" :disabled="busy" @click="question=example.prompt;composer?.focus()"><strong>{{example.title}}</strong><span>{{example.label}}</span></button></div></div>
      <div class="conversation">
        <article v-for="(message,index) in messages" :key="index" :class="['ai-message',message.role,{'failed':message.failed}]"><div class="message-heading"><strong>{{message.role==='user'?'你':'班级助手'}}</strong><el-button v-if="message.role==='assistant'&&!message.failed" link @click="copy(message.content)">复制</el-button></div><p>{{message.content}}</p><p v-if="message.failed" class="failure-help">问题和附件已保留在输入框中。涉及保存的指令，请先到对应记录页核对结果。</p><AiQueryResult v-for="(query,i) in message.queryResults||[]" :key="i" :query="query" :expanded="false"/><AiOperationPreview v-if="message.operation" :operation="message.operation" :busy="busy" @action="action(message,$event)" @view="$emit('view',$event)"/><AiRosterPreview v-if="message.rosterPreview" :preview="message.rosterPreview" :is-admin="isAdmin"/></article>
        <div v-if="busy" class="ai-pending" role="status"><span class="pending-indicator" aria-hidden="true"></span>{{pending}}</div>
      </div>
    </div>
    <div class="composer-dock">
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon/>
    <div v-if="uploadOpen" class="attachment-tools"><div class="attachment-heading"><strong>添加图片或学生名单</strong><el-button text @click="uploadOpen=false">收起</el-button></div><el-select v-model="uploadClass" :disabled="busy||uploading" clearable placeholder="名单归属班级（可选）" aria-label="名单归属班级"><el-option v-for="c in classes" :key="c.id" :value="c.id" :label="c.className+' · '+c.academicYear"/></el-select><el-button :loading="uploading" :disabled="busy" @click="fileInput.click()">选择文件</el-button><p class="hint">图片在本机识别。支持 PNG、JPG、Excel、CSV、TXT，最大 5MB。</p></div>
    <input ref="fileInput" type="file" accept=".png,.jpg,.jpeg,.xlsx,.csv,.txt" hidden @change="upload"/>
    <section v-if="attachment" class="attachment-review"><div class="message-heading"><strong>{{attachment.name}}</strong><el-button link @click="attachment=null">移除附件</el-button></div><p class="hint">核对识别文字后发送，尤其注意姓名和学号。</p><el-input v-model="attachment.text" type="textarea" :rows="5" maxlength="20000" aria-label="附件识别文字" :disabled="busy"/></section>
    <form class="ai-compose" @submit.prevent="send"><el-input ref="composer" v-model="question" type="textarea" :autosize="{minRows:2,maxRows:6}" maxlength="2000" placeholder="给班级助手发消息…" aria-label="输入查询问题" :disabled="busy" @keydown="composeKeydown"/><div class="compose-toolbar"><el-button text :disabled="busy||uploading" :aria-expanded="uploadOpen" @click="uploadOpen=!uploadOpen">＋ 添加附件</el-button><span class="compose-mode">{{preferences.autoRecord?'考勤指令可直接记录':'写入前需确认'}}</span><el-button class="send-button" native-type="submit" type="primary" :loading="busy" :disabled="busy||!question.trim()||uploading">{{busy?'处理中':'发送 ↑'}}</el-button></div></form><p class="hint compose-help"><span>Enter 发送 · Shift + Enter 换行</span><span>名单导入前需核对确认</span></p>
    </div>
    <el-dialog v-model="settingsOpen" title="助手设置" width="min(520px,94vw)"><el-form label-position="top"><el-form-item v-if="isAndroid" label="DeepSeek API Key">
<template #default><div style="width:100%">
<div class="key-state">{{connection&&connection.ready?'已配置（加密保存在本机 Keystore）':'未配置'}}</div>
<el-input v-model="keyInput" type="password" show-password placeholder="粘贴新的 Key 后点保存" aria-label="DeepSeek API Key"/>
<div style="display:flex;gap:8px;margin-top:8px">
<el-button type="primary" size="small" :loading="savingKey" @click="saveKey">保存</el-button>
<el-button size="small" :disabled="!(connection&&connection.ready)" @click="clearKey">清除</el-button>
</div>
<div v-if="keyError" class="ai-key-error">{{keyError}}</div>
</div></template>
</el-form-item><el-form-item label="性格风格"><el-select v-model="preferences.style" aria-label="性格风格" @change="savePreferences"><el-option label="干练高效" value="concise"/><el-option label="温和耐心" value="warm"/><el-option label="积极鼓励" value="encouraging"/></el-select></el-form-item><el-form-item label="自定义人设"><el-input v-model="preferences.persona" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="例如：称呼我为老师，语气亲切，回复尽量简短。" aria-label="自定义人设" @change="savePreferences"/></el-form-item><el-checkbox v-model="preferences.autoRecord" @change="savePreferences">明确的考勤、表扬指令校验后直接保存</el-checkbox><p class="hint">疑似重复、信息不全或批量导入仍需要核对。人设不会改变系统权限和校验规则。</p></el-form><template #footer><el-button type="primary" @click="savePreferences();settingsOpen=false">完成</el-button></template></el-dialog>
  </section>
</template>
<script setup>
import {ref,reactive,nextTick,onMounted} from 'vue';
import api from '../api';
import {ElMessage} from 'element-plus';
import AiQueryResult from '../components/AiQueryResult.vue';
import AiOperationPreview from '../components/AiOperationPreview.vue';
import AiRosterPreview from '../components/AiRosterPreview.vue';
import {sendAgentMessage} from '../agent-stream';
defineProps({classes:{type:Array,default:()=>[]}});
const emit=defineEmits(['view','changed']);
const question=ref(''),messages=ref([]),busy=ref(false),error=ref(''),thread=ref(),sessionId=ref(''),pending=ref('');
let account={};try{account=JSON.parse(localStorage.getItem('user')||'{}')}catch{}
const isAdmin=account.role==='ADMIN',preferenceKey='class-agent.preferences.'+account.username;
const preferences=reactive({style:'concise',persona:'',autoRecord:true});
try{const saved=JSON.parse(localStorage.getItem(preferenceKey)||'{}');if(['concise','warm','encouraging'].includes(saved.style))preferences.style=saved.style;if(typeof saved.persona==='string')preferences.persona=saved.persona.slice(0,500);if(typeof saved.autoRecord==='boolean')preferences.autoRecord=saved.autoRecord}catch{}
const settingsOpen=ref(false),fileInput=ref(),uploading=ref(false),attachment=ref(),uploadClass=ref(),connection=ref(),uploadOpen=ref(false),composer=ref();
// 安卓 APK：没有后端，Key 由本机 Keystore 加密保存
const isAndroid=typeof window!=='undefined'&&!!window.CMNative;
const keyInput=ref(''),savingKey=ref(false),keyError=ref('');
async function saveKey(){
  const value=keyInput.value.trim();
  if(!value){keyError.value='请先填写 Key';return}
  savingKey.value=true;keyError.value='';
  try{
    // 只调一次桥：不要再回头查 /ai/status，少一次往返就少一个卡住的可能
    const ok=window.CMNative.setKey(value);
    console.log('[saveKey] setKey ->',ok);
    if(!ok)throw new Error('保存失败，请重试');
    connection.value={engine:'DeepSeek',ready:true};
    keyInput.value='';
    ElMessage.success('已启用 AI 助手');
  }catch(e){
    console.log('[saveKey] error',String(e&&e.message||e));
    keyError.value=(e&&e.message)||'保存失败';
  }finally{savingKey.value=false}
}
async function clearKey(){
  if(!window.CMNative)return;
  try{
    window.CMNative.setKey('');
    connection.value={engine:'DeepSeek',ready:false};
    keyInput.value='';keyError.value='';
    ElMessage.success('已清除本机保存的 Key');
  }catch(e){keyError.value='清除失败'}
}
function composeKeydown(event){if(event.key==='Enter'&&!event.shiftKey&&!event.isComposing){event.preventDefault();send()}}
function savePreferences(){try{localStorage.setItem(preferenceKey,JSON.stringify(preferences))}catch{ElMessage.warning('浏览器未能保存偏好，本次设置仍有效')}}
onMounted(async()=>{try{connection.value=(await api.get('/ai/status')).data.data}catch{}});
async function upload(event){const file=event.target.files[0];event.target.value='';if(!file)return;if(file.size>5*1024*1024)return ElMessage.warning('文件不能超过5MB');uploading.value=true;error.value='';try{const data=new FormData();data.append('file',file);if(uploadClass.value)data.append('classId',uploadClass.value);const result=(await api.post('/ai/attachments',data,{timeout:35000})).data.data;if(result.rosterPreview){attachment.value=null;messages.value.push({role:'user',content:'上传名单：'+result.name},{role:'assistant',content:'名单已读取，请核对下面的导入预览。',rosterPreview:result.rosterPreview});await nextTick();thread.value?.scrollTo({top:thread.value.scrollHeight})}else{attachment.value=result;if(!question.value)question.value='把附件里的学生名单整理后录入系统'}}catch(e){error.value=e.response?.data?.message||e.message}finally{uploading.value=false}}
async function copy(content){try{await navigator.clipboard.writeText(content);ElMessage.success('回答已复制')}catch{ElMessage.warning('复制失败，请选中文本后复制')}}
const examples=[{title:'查看班级',label:'班级和学生人数',prompt:'列出班级及学生人数'},{title:'回顾考勤',label:'最近七天的迟到记录',prompt:'最近七天有哪些迟到记录？'},{title:'记录日常',label:'用一句话登记迟到',prompt:'我要记录一名学生今天迟到，请先询问班级和姓名'}];
async function action(message,kind){if(busy.value)return;busy.value=true;error.value='';pending.value=kind==='refresh'?'正在读取实际执行状态……':kind==='confirm'?'正在提交确认并保存记录……':'正在取消待执行操作……';try{const id=message.operation.id;message.operation=(await(kind==='refresh'?api.get(`/ai/operations/${id}`):api.post(`/ai/operations/${id}/${kind}`))).data.data;if(message.operation.status==='SUCCEEDED')emit('changed')}catch(e){error.value=(e.response?.data?.message||e.message)+'；网络异常时请刷新实际状态后再判断是否成功。'}finally{busy.value=false}}
async function reset(){if(busy.value)return;busy.value=true;pending.value='正在清空旧会话……';error.value='';try{if(sessionId.value)try{await api.delete(`/ai/sessions/${sessionId.value}`)}catch(e){if(e.response?.status!==404)throw e}sessionId.value='';messages.value=[];question.value='';attachment.value=null}catch(e){error.value=e.response?.data?.message||e.message}finally{busy.value=false}}
async function send(){if(busy.value||uploading.value||!question.value.trim())return;const value=question.value.trim();busy.value=true;error.value='';uploadOpen.value=false;pending.value='正在理解你的问题…';messages.value.push({role:'user',content:value});await nextTick();thread.value?.scrollTo({top:thread.value.scrollHeight});try{
  if(!sessionId.value)sessionId.value=(await api.post('/ai/sessions')).data.data.sessionId;
  const result=await sendAgentMessage({sessionId:sessionId.value,message:value,...preferences,attachment:attachment.value?.text},(event,data)=>{if(event==='status')pending.value=data.message});
  messages.value.push({role:'assistant',content:result.answer,operation:result.operation,queryResults:result.queryResults,rosterPreview:result.rosterPreview});question.value='';attachment.value=null;await nextTick();thread.value?.scrollTo({top:thread.value.scrollHeight});
}catch(e){messages.value.push({role:'assistant',failed:true,content:e.response?.data?.message||e.message||'助手暂时未能完成请求，请稍后再试。'});if(e.response?.status===404||e.status===404)sessionId.value=''}finally{busy.value=false;await nextTick();thread.value?.scrollTo({top:thread.value.scrollHeight})}}
</script>
<style scoped>
.ai-assistant{height:100%;min-height:0;display:flex;flex-direction:column;background:#fff;color:var(--ink)}
.ai-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:16px 28px;flex-shrink:0}
.ai-heading h1{font-size:18px;letter-spacing:0;margin:0;font-weight:600}.ai-heading h1 span{font-size:11px;color:var(--primary);margin-left:6px;font-weight:600}
.heading-actions{display:flex;gap:6px}.heading-actions .el-button{margin:0;font-size:13px}.hint{color:#627184;line-height:1.7}
.ai-thread{flex:1;min-height:0;overflow:auto;overscroll-behavior:contain;scrollbar-gutter:stable;padding:0 28px 28px;overflow-wrap:anywhere}
.conversation{max-width:800px;margin:auto}.ai-welcome{max-width:800px;margin:clamp(32px,9vh,100px) auto 40px;text-align:center}
.welcome-caption{font-size:13px;color:var(--primary)}.ai-welcome h2{font-size:clamp(24px,2.7vw,32px);font-weight:600;letter-spacing:-.6px;margin:16px 0 12px}.ai-welcome>p{color:#627184;margin:0;font-size:14px}
.ai-examples{display:flex;justify-content:center;gap:12px;margin:32px auto 0;flex-wrap:wrap}.ai-examples button{border:1px solid var(--border);border-radius:12px;background:#fff;text-align:left;min-width:180px;padding:15px 18px;cursor:pointer;color:var(--ink)}.ai-examples button:hover{background:#f5f8fc;border-color:#9eb4d4}.ai-examples button:focus-visible{outline:2px solid var(--primary);outline-offset:3px}.ai-examples strong{display:block;font-size:14px;font-weight:600}.ai-examples span{display:block;font-size:12px;color:#627184;margin-top:5px}
.ai-message{margin:24px 0 34px;line-height:1.85;min-width:0}.message-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.message-heading strong{font-size:13px;font-weight:600}.message-heading .el-button{font-size:12px;color:#627184}.ai-message>p{white-space:pre-wrap;margin:8px 0 0;line-height:1.9;font-size:15px}.ai-message.user{margin-left:auto;background:#eff3f8;padding:12px 18px;border-radius:16px 16px 4px 16px;width:fit-content;max-width:85%}.ai-message.user .message-heading{display:none}.ai-message.user>p{margin:0}.ai-message.failed{border-left:3px solid #c25555;padding-left:16px}.ai-message.failed>p{color:#a63d3d}.ai-message.failed .failure-help{font-size:12px;color:#627184}.ai-pending{display:flex;align-items:center;gap:10px;font-size:13px;color:#627184;padding:8px 0 24px}.pending-indicator{width:8px;height:8px;background:var(--primary);border-radius:50%;flex-shrink:0}
.composer-dock{width:min(856px,100%);padding:12px 28px 18px;margin:0 auto;flex-shrink:0;background:#fff}.ai-compose{border:1px solid #ccd5df;border-radius:18px;padding:12px 12px 10px;background:#fff;box-shadow:0 3px 16px #26394a06}.ai-compose:focus-within{border-color:#859dbd;box-shadow:0 0 0 2px #315eaa0c}.ai-compose :deep(.el-textarea__inner){box-shadow:none!important;resize:none;background:transparent;padding:4px 7px;color:var(--ink);font-size:15px;line-height:1.7}.ai-compose :deep(.el-textarea__inner::placeholder){color:#68778a}.compose-toolbar{display:flex;align-items:center;gap:8px;margin-top:8px}.compose-toolbar .el-button{margin:0}.compose-toolbar>.el-button:first-child{color:#526179;padding:8px}.compose-mode{font-size:11px;color:#627184;margin-left:2px}.send-button{margin-left:auto!important;border-radius:10px;min-width:78px}.compose-help{display:flex;justify-content:space-between;gap:10px;font-size:11px;margin:10px 6px 0}
.attachment-tools{background:#f7f9fc;border:1px solid var(--border);border-radius:12px;padding:12px 14px;margin-bottom:10px;display:flex;flex-wrap:wrap;gap:10px;align-items:center}.attachment-heading{width:100%;display:flex;justify-content:space-between;align-items:center;font-size:13px}.attachment-tools .el-select{width:250px;max-width:100%}.attachment-tools>.hint{font-size:11px;width:100%;margin:0}.attachment-review{background:#f7f9fc;border:1px solid var(--border);border-radius:12px;padding:12px;margin-bottom:10px;max-height:28vh;overflow:auto}.attachment-review .hint{font-size:12px;margin:6px 0}.ai-assistant>.el-alert{margin:0 28px 8px;width:auto;flex-shrink:0}.el-dialog .el-select{width:100%}
@media(max-width:700px){.ai-heading{padding:12px 16px;gap:8px}.ai-heading h1{font-size:16px}.heading-actions{gap:0}.heading-actions .el-button{padding:8px;font-size:12px}.ai-thread{padding:0 16px 16px}.ai-welcome{margin:24px auto}.ai-welcome h2{font-size:23px}.ai-welcome>p{font-size:13px}.ai-examples{margin-top:22px;display:grid;gap:8px}.ai-examples button{min-width:240px;padding:11px 16px}.ai-examples strong,.ai-examples span{display:inline;font-size:12px}.ai-examples span{margin-left:12px}.composer-dock{padding:8px 12px 12px}.compose-mode{display:none}.compose-help{font-size:10px;flex-wrap:wrap;justify-content:center;gap:3px 10px}.ai-message.user{max-width:92%}.ai-message>p{font-size:14px}.ai-assistant>.el-alert{margin:0 12px 8px}}
</style>
