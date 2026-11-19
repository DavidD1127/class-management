<template>
  <div class="layout" :class="{'chat-layout':tab==='ai'}">
    <aside>
      <div class="brand"><span class="brand-mark">班</span><div><h2>智能班级管理</h2><small>教学管理工作空间</small></div></div>
      <nav class="primary-nav" aria-label="主导航">
        <button v-for="item in navigation" :key="item.key" type="button" :class="{active:currentSection===item}" :aria-current="currentSection===item?(item.pages?'location':'page'):undefined" @click="openSection(item)"><span class="nav-full">{{item.label}}</span><span class="nav-short">{{item.short||item.label}}</span></button>
      </nav>
    </aside>
    <div class="workspace">
      <header><span class="breadcrumb">工作空间 <span>/</span> {{currentSection?.label}}</span><SubjectThemePicker @change="themeId=$event"/><div class="user-profile"><span class="user-avatar" aria-hidden="true">{{user.username?.slice(0,1)?.toUpperCase()}}</span><span class="user-info">{{user.username}}<small>{{isAdmin?'管理员':'教师'}}</small></span></div><el-button @click="logout">退出登录</el-button></header>
      <main :class="{'chat-main':tab==='ai'}">
        <nav v-if="currentSection?.pages" class="section-nav" :aria-label="currentSection.label">
          <button v-for="item in currentSection.pages" :key="item.key" type="button" :class="{active:tab===item.key}" :aria-current="tab===item.key?'page':undefined" @click="tab=item.key">{{item.label}}</button>
        </nav>
        <SubjectThemeBanner v-if="themeId!=='default' && tab!=='ai'" :theme-id="themeId" :compact="tab!=='home'"/>
        <Dashboard v-if="tab==='home'" :classes="classes" @view="tab=$event"/>
        <template v-if="tab==='students'">
          <h1>学生管理</h1><p class="hint">按学号识别学生，支持同名档案。{{isAdmin?'移入回收站时会一并保存关联记录，可在回收站恢复。':'教师可查询档案、下载模板和检查导入文件。'}}</p>
          <div class="toolbar">
            <el-input v-model="q" placeholder="搜索姓名或学号" aria-label="搜索姓名或学号" clearable @keyup.enter="search" @clear="search"/>
            <el-select v-model="classId" placeholder="全部班级" aria-label="筛选班级" clearable @change="search"><el-option v-for="c in classes" :key="c.id" :label="classLabel(c)" :value="c.id"/></el-select>
            <el-button @click="search">查询</el-button><el-button v-if="isAdmin" type="primary" @click="openStudent()">新增学生</el-button>
            <el-button :loading="parsing" @click="fileInput.click()">{{parsing?'正在解析':'批量导入'}}</el-button>
            <input ref="fileInput" type="file" accept=".xlsx,.csv" hidden @change="preview"/>
            <el-button @click="downloadTemplate('xlsx')">Excel 模板</el-button><el-button @click="downloadTemplate('csv')">CSV 模板</el-button>
          </div>
          <MobileList v-if="narrow" :items="students" :primary="r=>r.name" :secondary="r=>r.studentNo"
            :badge="r=>r.status" :empty="'暂无匹配的学生'"
            :fields="[{k:'gender',label:'性别'},{k:'className',label:'班级'},{k:'enrollmentDate',label:'入学日期'}]">
            <template #actions="{row}"><template v-if="isAdmin"><el-button link type="primary" @click="openStudent(row)">编辑</el-button><el-button link type="danger" @click="removeStudent(row)">删除</el-button></template></template>
          </MobileList>
          <el-table v-else v-loading="loading" :data="students" empty-text="暂无匹配的学生" class="data-table">
            <el-table-column prop="studentNo" label="学号" min-width="140"/><el-table-column prop="name" label="姓名" min-width="100"/><el-table-column prop="gender" label="性别" width="70"/>
            <el-table-column prop="className" label="班级" min-width="130"/><el-table-column prop="enrollmentDate" label="入学日期" width="120"/><el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status==='在读'?'success':'info'" effect="light">{{row.status}}</el-tag></template></el-table-column>
            <el-table-column v-if="isAdmin" label="操作" fixed="right" width="140"><template #default="{row}"><el-button link type="primary" @click="openStudent(row)">编辑</el-button><el-button link type="danger" @click="removeStudent(row)">删除</el-button></template></el-table-column>
          </el-table>
          <div class="pagination"><span>共 {{total}} 位学生</span><el-pagination layout="prev,pager,next" :pager-count="5" :total="total" :page-size="10" v-model:current-page="page" @current-change="loadStudents"/></div>
        </template>
        <template v-if="tab==='classes'">
          <h1>班级管理</h1><p class="hint">以学年、年级和班级名称共同区分班级。</p>
          <el-button v-if="isAdmin" type="primary" @click="openClass()">新增班级</el-button><el-button v-if="isAndroid&&isAdmin" text type="danger" style="margin-left:8px" @click="resetDemo">重置演示数据</el-button>
          <MobileList v-if="narrow" :items="classes" :primary="r=>r.className" :secondary="r=>r.grade"
            :badge="r=>r.studentCount+' 人'" :empty="'暂无班级，请先创建班级'"
            :fields="[{k:'academicYear',label:'学年'}]">
            <template #actions="{row}"><el-button link type="primary" @click="viewClass(row)">学生名单</el-button><template v-if="isAdmin"><el-button link @click="openClass(row)">编辑</el-button><el-button link type="danger" @click="removeClass(row)">删除</el-button></template></template>
          </MobileList>
          <el-table v-else v-loading="classLoading" :data="classes" empty-text="暂无班级，请先创建班级" class="data-table">
            <el-table-column prop="className" label="班级" min-width="140"/><el-table-column prop="grade" label="年级" min-width="90"/><el-table-column prop="academicYear" label="学年" min-width="120"/><el-table-column prop="studentCount" label="人数" width="70"/>
            <el-table-column label="操作" fixed="right" width="220"><template #default="{row}"><el-button link type="primary" @click="viewClass(row)">学生名单</el-button><template v-if="isAdmin"><el-button link @click="openClass(row)">编辑</el-button><el-button link type="danger" @click="removeClass(row)">删除</el-button></template></template></el-table-column>
          </el-table>
        </template>
        <Scores v-if="tab==='scores'" :classes="classes" :is-admin="isAdmin"/>
        <AiAssistant v-show="tab==='ai'" :classes="classes" @view="tab=$event" @changed="loadClasses()"/>
        <Assignments v-if="tab==='assignments'" :classes="classes" :is-admin="isAdmin"/>
        <StudentRecords v-if="tab==='attendance'" key="attendance" kind="attendance" :classes="classes" :is-admin="isAdmin"/>
        <RecycleBin v-if="tab==='recycle'&&isAdmin" @changed="loadClasses();loadStudents()"/>
        <StudentRecords v-if="tab==='praises'" key="praises" kind="praises" :classes="classes" :is-admin="isAdmin"/>
      </main>
    </div>
    <el-dialog v-model="studentDialog" :title="form.id?'编辑学生':'新增学生'" width="min(560px, 94vw)" :close-on-click-modal="false">
      <el-form label-position="top" @submit.prevent="saveStudent">
        <el-form-item label="学号（必填）"><el-input v-model="form.studentNo" maxlength="50"/></el-form-item>
        <el-form-item label="姓名（必填）"><el-input v-model="form.name" maxlength="100"/></el-form-item>
        <el-form-item label="班级（必选）"><el-select v-model="form.classId" placeholder="选择班级"><el-option v-for="c in classes" :key="c.id" :label="classLabel(c)" :value="c.id"/></el-select></el-form-item>
        <div class="form-grid"><el-form-item label="性别"><el-select v-model="form.gender" clearable><el-option v-for="g in ['男','女','其他']" :key="g" :label="g" :value="g"/></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status"><el-option label="在读" value="在读"/><el-option label="离校" value="离校"/></el-select></el-form-item></div>
        <el-form-item label="入学日期"><el-date-picker v-model="form.enrollmentDate" type="date" value-format="YYYY-MM-DD" placeholder="可留空"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="studentDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveStudent">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="classDialog" :title="classForm.id?'编辑班级':'新增班级'" width="min(520px, 94vw)" :close-on-click-modal="false">
      <el-form label-position="top"><el-form-item label="班级名称（必填）"><el-input v-model="classForm.className" maxlength="100" placeholder="例如：高一（1）班"/></el-form-item><el-form-item label="年级（必填）"><el-input v-model="classForm.grade" maxlength="50" placeholder="例如：高一"/></el-form-item><el-form-item label="学年（必填）"><el-input v-model="classForm.academicYear" maxlength="20" placeholder="例如：2026-2027"/></el-form-item></el-form>
      <template #footer><el-button @click="classDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveClass">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="importDialog" title="学生名单导入" width="min(1000px, 96vw)" :close-on-click-modal="false">
      <p>仅支持单工作表 XLSX 或 UTF-8 CSV，最大 5MB、2000 行。请先创建班级，学号列使用文本格式。</p>
      <el-alert v-if="importResult" :title="`导入完成：成功 ${importResult.success}，失败 ${importResult.failed}，跳过 ${importResult.skipped}`" type="success" :closable="false"/>
      <template v-else><el-alert :title="`有效 ${previewData.valid} 条，错误 ${previewData.errors} 条。确认后仅写入有效记录，错误记录跳过；已有学生不会被覆盖。`" :type="previewData.errors?'warning':'info'" :closable="false"/><p class="hint">预览 15 分钟内有效。存在同名班级时，必须填写年级和学年。</p></template>
      <el-table :data="previewData.rows" max-height="420" empty-text="没有预览数据"><el-table-column prop="rowNumber" label="行" width="60"/><el-table-column prop="studentNo" label="学号" min-width="120"/><el-table-column prop="name" label="姓名" min-width="100"/><el-table-column prop="className" label="班级" min-width="130"/><el-table-column prop="error" label="错误原因" min-width="240"><template #default="{row}"><span :class="{error:row.error}">{{row.error||'校验通过'}}</span></template></el-table-column></el-table>
      <template #footer><el-button @click="importDialog=false">关闭</el-button><el-button v-if="isAdmin&&!importResult" type="primary" :disabled="!previewData.valid||!previewData.previewId" :loading="saving" @click="confirmImport">确认导入 {{previewData.valid}} 条</el-button><span v-if="!isAdmin">正式导入请由管理员操作</span></template>
    </el-dialog>
  </div>
</template>
<script setup>
import {ref,reactive,computed,onMounted,watch,defineAsyncComponent} from 'vue';
import {useRouter,useRoute} from 'vue-router';
import {ElMessage,ElMessageBox} from 'element-plus';
import api from '../api';
import Dashboard from '../components/Dashboard.vue';
import MobileList from '../components/MobileList.vue';
import {useNarrow} from '../composables/useNarrow';
import SubjectThemePicker from '../components/SubjectThemePicker.vue';
import SubjectThemeBanner from '../components/SubjectThemeBanner.vue';
const themeId=ref(document.documentElement.dataset.subject || 'default');
const RecycleBin=defineAsyncComponent(()=>import('./RecycleBin.vue'));
const Scores=defineAsyncComponent(()=>import('./Scores.vue'));
const AiAssistant=defineAsyncComponent(()=>import('./AiAssistant.vue'));
const Assignments=defineAsyncComponent(()=>import('./Assignments.vue'));
const StudentRecords=defineAsyncComponent(()=>import('./StudentRecords.vue'));
const route=useRoute(),router=useRouter(),user=ref({}),isAdmin=computed(()=>user.value.role==='ADMIN');
const navigation=[
  {key:'home',label:'首页',short:'首页'},
  {key:'roster',label:'班级与学生',short:'班级',pages:[{key:'students',label:'学生名单'},{key:'classes',label:'班级列表'}]},
  {key:'scores',label:'成绩管理',short:'成绩'},
  {key:'records',label:'日常记录',short:'日常',pages:[{key:'attendance',label:'考勤'},{key:'assignments',label:'作业'},{key:'praises',label:'表扬'}]},
  {key:'ai',label:'AI 班级助手',short:'AI'},
  ...(JSON.parse(localStorage.getItem('user')||'{}').role==='ADMIN'?[{key:'recycle',label:'回收站',short:'回收站'}]:[])
];
const tabs=navigation.flatMap(item=>item.pages||[item]);
const narrow=useNarrow();
const isAndroid=typeof window!=='undefined'&&!!window.CMNative;
async function resetDemo(){
  try{
    await ElMessageBox.confirm('会清空现有班级、学生与记录，并重新铺一份演示数据。确定吗？','重置演示数据',{type:'warning',confirmButtonText:'确定重置',cancelButtonText:'取消'});
  }catch{return}
  try{
    const summary=window.CMNative.resetDemo();
    ElMessage.success('已重置：'+summary);
    await Promise.all([loadClasses(),loadStudents()]);
  }catch(e){ElMessage.error('重置失败')}
}
const initialTab=new URLSearchParams(location.search).get('view');
const tab=ref(tabs.some(t=>t.key===initialTab)?initialTab:'home'),students=ref([]),classes=ref([]),q=ref(''),classId=ref(),page=ref(1),total=ref(0);
const currentSection=computed(()=>navigation.find(item=>item.key===tab.value||item.pages?.some(page=>page.key===tab.value)));
function openSection(item){if(currentSection.value!==item)tab.value=item.pages?.[0].key||item.key}
const loading=ref(false),classLoading=ref(false),saving=ref(false),parsing=ref(false),fileInput=ref();
const studentDialog=ref(false),classDialog=ref(false),importDialog=ref(false),form=reactive({}),classForm=reactive({}),previewData=ref({rows:[],valid:0,errors:0}),importResult=ref(null);
const error=e=>ElMessage.error(e.response?.data?.message||e.message||'操作失败，请重试');
const classLabel=c=>`${c.className} · ${c.grade} · ${c.academicYear}`;
watch(()=>route.query.view,value=>{if(tabs.some(t=>t.key===value))tab.value=value});
watch(tab,value=>{router.replace({query:{view:value}});if(value==='students')loadStudents();if(value==='classes')loadClasses()});
let requestSequence=0;
async function loadClasses(){classLoading.value=true;try{classes.value=(await api.get('/classes')).data.data}catch(e){error(e)}finally{classLoading.value=false}}
async function loadStudents(){const request=++requestSequence;loading.value=true;try{const {data}=await api.get('/students',{params:{q:q.value,classId:classId.value||undefined,page:page.value-1,size:10}});if(request===requestSequence){students.value=data.data.content;total.value=data.data.total}}catch(e){error(e)}finally{if(request===requestSequence)loading.value=false}}
function search(){page.value=1;loadStudents()}
function viewClass(c){classId.value=c.id;q.value='';tab.value='students';search()}
function openStudent(row){Object.keys(form).forEach(k=>delete form[k]);Object.assign(form,{id:null,studentNo:'',name:'',gender:'',classId:null,enrollmentDate:null,status:'在读'},row||{});form.enrollmentDate=form.enrollmentDate||null;studentDialog.value=true}
async function saveStudent(){if(saving.value)return;if(!form.studentNo?.trim()||!form.name?.trim()||!form.classId)return ElMessage.warning('请填写学号、姓名并选择班级');saving.value=true;try{await api({url:form.id?`/students/${form.id}`:'/students',method:form.id?'put':'post',data:{...form,enrollmentDate:form.enrollmentDate||null}});studentDialog.value=false;await Promise.all([loadStudents(),loadClasses()]);ElMessage.success('学生已保存')}catch(e){error(e)}finally{saving.value=false}}
async function removeStudent(row){try{await ElMessageBox.confirm(`将 ${row.name}（${row.studentNo}）及其成绩、作业、考勤、表扬记录移入回收站？之后可以恢复。`,'移入回收站',{type:'warning',confirmButtonText:'移入回收站',cancelButtonText:'取消'});await api.delete(`/students/${row.id}`);if(students.value.length===1&&page.value>1)page.value--;await Promise.all([loadStudents(),loadClasses()]);ElMessage.success('学生已删除')}catch(e){if(e!=='cancel'&&e!=='close')error(e)}}
function openClass(row){Object.keys(classForm).forEach(k=>delete classForm[k]);Object.assign(classForm,{id:null,className:'',grade:'',academicYear:''},row||{});classDialog.value=true}
async function saveClass(){if(saving.value)return;if(!classForm.className?.trim()||!classForm.grade?.trim()||!classForm.academicYear?.trim())return ElMessage.warning('请填写班级名称、年级和学年');saving.value=true;try{await api({url:classForm.id?`/classes/${classForm.id}`:'/classes',method:classForm.id?'put':'post',data:classForm});classDialog.value=false;await Promise.all([loadClasses(),loadStudents()]);ElMessage.success('班级已保存')}catch(e){error(e)}finally{saving.value=false}}
async function removeClass(row){try{await ElMessageBox.confirm(`将 ${classLabel(row)} 及其所有学生、考试、作业和教学记录移入回收站？之后可以整组恢复。`,'移入回收站',{type:'warning',confirmButtonText:'确定删除',cancelButtonText:'取消'});await api.delete(`/classes/${row.id}`);if(classId.value===row.id){classId.value=undefined;search()}await loadClasses();ElMessage.success('班级已删除')}catch(e){if(e!=='cancel'&&e!=='close')error(e)}}
async function preview(event){const file=event.target.files[0];event.target.value='';if(!file)return;if(!/\.(xlsx|csv)$/i.test(file.name)||file.size>5*1024*1024)return ElMessage.warning('请选择不超过 5MB 的 XLSX 或 CSV 文件');parsing.value=true;previewData.value={rows:[],valid:0,errors:0};importResult.value=null;const fd=new FormData();fd.append('file',file);try{previewData.value=(await api.post('/students/import/preview',fd)).data.data;importDialog.value=true}catch(e){error(e)}finally{parsing.value=false}}
async function confirmImport(){saving.value=true;try{importResult.value=(await api.post('/students/import/confirm',{previewId:previewData.value.previewId})).data.data;await Promise.all([loadStudents(),loadClasses()])}catch(e){previewData.value.previewId=null;error(e);ElMessage.warning('请重新上传获取最新预览后再确认')}finally{saving.value=false}}
async function downloadTemplate(format){try{const r=await api.get('/students/import/template',{params:{format},responseType:'blob'}),url=URL.createObjectURL(r.data),a=document.createElement('a');a.href=url;a.download=`学生名单模板.${format}`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}catch(e){error(e)}}
function logout(){localStorage.removeItem('token');localStorage.removeItem('user');router.push('/login')}
onMounted(async()=>{try{user.value=(await api.get('/auth/me')).data.data;await loadClasses();if(tab.value==='students')await loadStudents()}catch(e){error(e)}});
</script>
<style scoped>
.layout{min-height:100vh;display:flex;color:var(--ink)}aside{width:224px;flex-shrink:0;background:#fff;border-right:1px solid var(--border);padding:28px 16px;position:sticky;top:0;height:100vh}.brand{display:flex;align-items:center;gap:10px;margin-bottom:38px;padding:0 8px}.brand h2{font-size:16px;margin:0}.brand small{font-size:11px;color:var(--muted)}.brand-mark{background:var(--primary);color:white;width:34px;height:34px;border-radius:8px;display:grid;place-items:center;font-weight:600}nav{display:grid;gap:6px}nav button{background:transparent;color:#596a82;border:0;padding:12px 16px;text-align:left;border-radius:6px;font:inherit;cursor:pointer}nav button:hover{background:#f4f6fa}nav button.active{background:#edf2fa;color:var(--primary);font-weight:600}nav button:focus-visible{outline:2px solid var(--primary)}.workspace{flex:1;min-width:0}header{background:white;border-bottom:1px solid var(--border);display:flex;align-items:center;gap:18px;padding:15px 32px;min-height:66px;font-size:13px;color:var(--muted)}.breadcrumb{margin-right:auto;color:var(--ink)}.breadcrumb span{margin:0 10px;color:#b1bbc9}main{padding:30px 32px 48px;max-width:1600px;margin:auto}h1{margin:0 0 10px}.hint{font-size:14px;line-height:1.7}.toolbar .el-input{width:210px}.toolbar .el-select{width:250px}.data-table{margin-top:18px}.pagination{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:8px;flex-wrap:wrap}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.error{color:#b42318}:deep(.el-dialog .el-select){width:100%}
@media(max-width:1100px){aside{width:190px;padding:24px 10px}.brand{gap:7px;padding:0}.brand h2{font-size:14px}main{padding:24px 20px}}@media(max-width:700px){.layout{display:block}aside{width:100%;height:auto;position:static;padding:14px 16px}.brand{margin-bottom:14px}nav{display:flex;gap:6px;flex-wrap:wrap}nav button{padding:9px 12px}header{padding:12px 16px;gap:10px;flex-wrap:wrap}.breadcrumb{width:100%}main{padding:20px 12px}.toolbar .el-input,.toolbar .el-select{width:100%}.form-grid{gap:10px}}
</style>
<style scoped>
aside{width:238px;display:flex;flex-direction:column;background:#fcfdfd}nav button{display:flex;align-items:center;min-height:44px;border-radius:9px;transition:background .16s,color .16s}nav button.active{background:#e9f0f9}.user-profile{display:flex;align-items:center;gap:10px}.user-avatar{display:grid;place-items:center;width:34px;height:34px;border-radius:50%;background:#e9f0f8;color:var(--primary);font-weight:600}.user-info{display:flex;flex-direction:column;gap:3px;color:var(--ink)}.user-info small{font-size:11px;color:var(--muted)}main{padding:32px 36px 48px}@media(max-width:1100px){aside{width:200px}main{padding:24px 20px}}@media(max-width:700px){aside{width:100%}nav button{flex-shrink:0;min-height:40px}.user-avatar{width:28px;height:28px}main{padding:22px 14px}}
</style>

<style scoped>
.section-nav{display:flex;flex-wrap:wrap;gap:6px;padding-bottom:10px;margin-bottom:20px;border-bottom:1px solid var(--border)}
.section-nav button{min-height:40px;padding:7px 14px}
@media(max-width:700px){
  .primary-nav{display:grid;grid-template-columns:repeat(3,minmax(0,1fr))}
  .primary-nav button{justify-content:center;padding:8px 4px;text-align:center}
  .section-nav{margin-bottom:16px}
}
.chat-layout{height:100dvh;min-height:0;overflow:hidden}.chat-layout .workspace{display:flex;flex-direction:column;min-height:0}.chat-layout header{flex-shrink:0}.chat-layout .chat-main{flex:1;min-height:0;width:100%;max-width:none;margin:0;padding:0;overflow:hidden}@media(max-width:700px){.chat-layout{display:flex;flex-direction:column}.chat-layout aside{flex-shrink:0;padding:10px 12px 0}.chat-layout .brand{display:none}.chat-layout nav button{padding:8px 10px;min-height:36px}.chat-layout header{min-height:48px;padding:7px 14px;flex-wrap:nowrap}.chat-layout .breadcrumb{width:auto;font-size:12px}.chat-layout .user-info{display:none}.chat-layout .workspace{flex:1}}
</style>
<style>
/* 手机：导航沉到底部，顶部只留必要信息。
   选择器要压过 scoped 生成的 aside[data-v-x]，所以都带上 .layout 前缀。 */
.nav-short{display:none}
@media(max-width:760px){
  .layout{display:block!important;padding-bottom:calc(62px + env(safe-area-inset-bottom,0px))}
  .layout > aside{position:fixed!important;left:0;right:0;bottom:0;top:auto;width:100%!important;
    height:auto!important;z-index:200;padding:0!important;margin:0;
    border-right:0!important;border-top:1px solid var(--border);background:#fff;
    box-shadow:0 -2px 12px rgba(24,39,58,.06)}
  .layout > aside .brand{display:none!important}
  .layout > aside nav{display:flex!important;gap:0;flex-wrap:nowrap!important;
    padding:6px 4px calc(6px + env(safe-area-inset-bottom,0px))}
  .layout > aside nav button{flex:1;min-height:0!important;padding:7px 2px!important;
    font-size:11px;line-height:1.2;justify-content:center!important;text-align:center;
    border-radius:9px;white-space:nowrap}
  .layout .nav-full{display:none}
  .layout .nav-short{display:inline}
  .layout > .workspace > header{min-height:0!important;padding:9px 12px!important;
    gap:8px;flex-wrap:nowrap!important;align-items:center}
  .layout .breadcrumb{display:none!important}
  .layout .user-profile{margin-left:auto}
  .layout .user-info{display:none!important}
  .layout > .workspace > header .el-button{min-height:30px;padding:5px 10px;font-size:12px}
  .layout > .workspace > main{padding:14px 12px 20px!important}
  .layout > .workspace > main h1{font-size:20px}
  /* 学科主题选择是桌面习惯，手机上收起来，省出头部空间 */
  .layout > .workspace > header > .theme-picker,
  .layout > .workspace > header [class*="theme"]{display:none!important}
  .layout > .workspace > main{padding-top:10px!important}
  /* 聊天页占满「导航以上」的高度：底部栏是 fixed 的，100dvh 会把它自己顶出屏幕，
     表现为对话区被截断且滚不动。这里把高度扣掉底部栏，并取消外层的下内边距。 */
  .layout.chat-layout{padding-bottom:0!important;
    height:calc(100dvh - 62px - env(safe-area-inset-bottom,0px))!important}
  .layout.chat-layout > .workspace{min-height:0}
  .layout.chat-layout > .workspace > main.chat-main{padding:0!important;min-height:0}
  .layout.chat-layout .ai-thread{padding:0 14px 14px}
}
</style>
