<template>
<section class="business" v-loading="loading">
  <h1>成绩管理</h1><p class="hint">每行显示一名学生在同一场考试的成绩。该场考试的科目全部有实考成绩时才显示总分，筛选单科时只显示该科。</p>
  <div class="filters">
    <el-select v-model="filters.classId" clearable placeholder="全部班级" aria-label="成绩班级" @change="changeClass"><el-option v-for="c in classes" :key="c.id" :value="c.id" :label="c.className+' · '+c.academicYear"/></el-select>
    <el-select v-model="filters.examId" clearable placeholder="全部考试" aria-label="选择考试" @change="search"><el-option v-for="e in exams" :key="e.id" :value="e.id" :label="e.name+' · '+e.examDate"/></el-select>
    <el-input v-model="filters.subject" clearable placeholder="科目（精确匹配）" aria-label="成绩科目" @keyup.enter="search"/>
    <StudentPicker v-model="filters.studentId" :class-id="filters.classId"/>
    <el-button :loading="loading" @click="search">查询</el-button><el-button v-if="isAdmin" type="primary" @click="openExam()">创建考试</el-button><el-button v-if="isAdmin" @click="openScore()">录入成绩</el-button>
  </div>
  <el-collapse><el-collapse-item title="考试列表与编辑" name="exams"><el-table :data="exams" empty-text="暂无考试"><el-table-column prop="name" label="考试" min-width="150"/><el-table-column prop="className" label="班级" min-width="130"/><el-table-column prop="examDate" label="考试日期" width="120"/><el-table-column v-if="isAdmin" fixed="right" label="操作" width="80"><template #default="{row}"><el-button link @click="openExam(row)">编辑</el-button></template></el-table-column></el-table></el-collapse-item></el-collapse>
  <MobileList v-if="narrow" :items="rows" :primary="r=>r.studentName" :secondary="r=>r.studentNo"
    :badge="r=>r.examName" :fields="scoreFields" empty="暂无匹配的成绩">
    <template #actions="{row}"><template v-if="isAdmin"><template v-for="s in row.subjects" :key="s"><el-button v-if="row.gradesBySubject.has(s)" link type="primary" @click="openScore(row.gradesBySubject.get(s))">改{{s}}</el-button></template></template></template>
  </MobileList>
  <el-table v-else :data="rows" empty-text="暂无匹配的成绩">
    <el-table-column prop="studentNo" label="学号" min-width="110"/>
    <el-table-column prop="studentName" label="学生" min-width="90"/>
    <el-table-column prop="examName" label="考试" min-width="180"/>
    <el-table-column v-for="subject in summarySubjects" :key="subject" :label="subject" min-width="140">
      <template #default="{row}">
        <template v-if="row.gradesBySubject.has(subject)">
          <ElTooltip :content="scoreDetail(row.gradesBySubject.get(subject))" placement="top">
            <span>{{scoreLabel(row.gradesBySubject.get(subject))}}</span>
          </ElTooltip>
          <el-button v-if="isAdmin" link type="primary" :aria-label="`修改${row.studentName}的${subject}成绩`" @click="openScore(row.gradesBySubject.get(subject))">修改</el-button>
        </template>
        <span v-else>{{row.subjects.includes(subject)?'未录入':'—'}}</span>
      </template>
    </el-table-column>
    <el-table-column v-if="!appliedSubject" label="总分" min-width="115"><template #default="{row}">{{row.totalScore==null?'—':`${row.totalScore} / ${row.totalFullScore}`}}</template></el-table-column>
  </el-table>
  <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="total,prev,pager,next" :pager-count="5" @current-change="load"/>
  <h2 class="subheading">班级成绩统计</h2><p class="hint">按考试和科目统计，学生筛选只作用于成绩列表和个人变化。缺考和未录入不参与统计。按得分率排名，并列采用 1、1、3，及格线为满分的 60%。</p>
  <MobileList v-if="narrow" :items="stats" :primary="r=>r.examName" :secondary="r=>r.subject"
    :badge="r=>r.passRate!=null?('及格率 '+r.passRate+'%'):''" empty="暂无可统计的成绩"
    :fields="[{k:'recorded',label:'实考'},{k:'absent',label:'缺考'},{k:'averageScore',label:'平均原始分'},{k:'highestScore',label:'最高原始分'},{k:'lowestScore',label:'最低原始分'},{k:'averagePercentage',label:'平均得分率(%)'},{k:'passed',label:'及格人数'}]">
    <template #actions="{row}"><el-button link @click="ranking=row.ranking;rankDialog=true">查看排名</el-button></template>
  </MobileList>
  <el-table v-else :data="stats" empty-text="暂无可统计的成绩"><el-table-column prop="examName" label="考试" min-width="120"/><el-table-column prop="subject" label="科目" width="80"/><el-table-column prop="recorded" label="实考" width="70"/><el-table-column prop="absent" label="缺考" width="70"/><el-table-column prop="averageScore" label="平均原始分" width="110"/><el-table-column prop="highestScore" label="最高原始分" width="110"/><el-table-column prop="lowestScore" label="最低原始分" width="110"/><el-table-column prop="averagePercentage" label="平均得分率(%)" width="140"/><el-table-column prop="passed" label="及格人数" width="90"/><el-table-column prop="passRate" label="及格率(%)" width="110"/><el-table-column label="排名" width="90"><template #default="{row}"><el-button link @click="ranking=row.ranking;rankDialog=true">查看</el-button></template></el-table-column></el-table>
  <template v-if="filters.studentId"><h2 class="subheading">个人成绩变化</h2><p class="hint">按考试日期排列，分别查看同科目得分率；不合并不同科目。</p><el-table :data="history" empty-text="该学生暂无成绩历史"><el-table-column prop="examDate" label="日期" width="120"/><el-table-column prop="examName" label="考试" min-width="130"/><el-table-column prop="subject" label="科目" width="90"/><el-table-column label="成绩 / 得分率" min-width="150"><template #default="{row}">{{row.status==='ABSENT'?'缺考':`${row.score} / ${row.fullScore}（${(row.score/row.fullScore*100).toFixed(2)}%）`}}</template></el-table-column></el-table></template>
  <h2 class="subheading">批量导入</h2><p class="hint">先选考试、填写科目和满分，再上传模板文件。最多 2000 行；仅 XLSX 或 UTF-8 CSV，5MB 内。预览有效期 15 分钟。</p>
  <div class="filters"><el-select v-model="importExam" placeholder="导入到考试" aria-label="导入考试"><el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id"/></el-select><el-input v-model="importSubject" placeholder="导入科目" aria-label="导入科目"/><el-input-number v-model="importFull" :min="0.01" :max="99999999.99" :precision="2" aria-label="导入满分"/><el-button @click="download('xlsx')">Excel 模板</el-button><el-button @click="download('csv')">CSV 模板</el-button><el-button :loading="parsing" @click="pickFile">上传预览</el-button><input ref="file" type="file" accept=".csv,.xlsx" hidden @change="preview"/></div>
  <el-dialog v-model="examDialog" :title="examForm.id?'编辑考试':'创建考试'" width="min(520px,94vw)" :close-on-click-modal="false"><el-form label-position="top"><el-form-item label="考试名称（必填）"><el-input v-model="examForm.name" maxlength="100"/></el-form-item><el-form-item label="班级（必选）"><el-select v-model="examForm.classId"><el-option v-for="c in classes" :key="c.id" :value="c.id" :label="c.className+' · '+c.academicYear"/></el-select></el-form-item><el-form-item label="考试日期（必填）"><el-date-picker v-model="examForm.examDate" value-format="YYYY-MM-DD" type="date"/></el-form-item></el-form><template #footer><el-button @click="examDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveExam">保存</el-button></template></el-dialog>
  <el-dialog v-model="scoreDialog" :title="scoreForm.id?'修改成绩':'录入成绩'" width="min(520px,94vw)" :close-on-click-modal="false"><el-form label-position="top"><el-form-item label="考试（必选）"><el-select v-model="scoreForm.examId" @change="scoreForm.studentId=null"><el-option v-for="e in exams" :key="e.id" :value="e.id" :label="e.name"/></el-select></el-form-item><el-form-item label="学生（必选，输入姓名或学号）"><StudentPicker v-model="scoreForm.studentId" :class-id="scoreClass"/></el-form-item><el-form-item label="科目（必填）"><el-input v-model="scoreForm.subject" maxlength="50"/></el-form-item><el-form-item label="状态"><el-select v-model="scoreForm.status" @change="scoreForm.score=null"><el-option label="已录入（含零分）" value="RECORDED"/><el-option label="缺考" value="ABSENT"/></el-select></el-form-item><el-form-item v-if="scoreForm.status==='RECORDED'" label="实际得分（必填）"><el-input-number v-model="scoreForm.score" :min="0" :max="scoreForm.fullScore" :precision="2"/></el-form-item><el-form-item label="满分（大于零）"><el-input-number v-model="scoreForm.fullScore" :min="0.01" :max="99999999.99" :precision="2"/></el-form-item><el-form-item label="备注"><el-input v-model="scoreForm.remark" maxlength="500" type="textarea"/></el-form-item></el-form><template #footer><el-button @click="scoreDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveScore">保存</el-button></template></el-dialog>
  <el-dialog v-model="importDialog" title="成绩导入预览" width="min(960px,96vw)" :close-on-click-modal="false"><el-alert v-if="result" :title="`成功 ${result.success}，失败 ${result.failed}，跳过 ${result.skipped}`" type="success" :closable="false"/><el-alert v-else :title="`有效 ${previewData.valid}，错误 ${previewData.errors}，已有 ${previewData.existing}。默认跳过已有记录。`" type="info" :closable="false"/><el-table :data="previewData.rows" max-height="380"><el-table-column prop="rowNumber" label="行" width="60"/><el-table-column prop="studentNo" label="学号" min-width="110"/><el-table-column prop="name" label="姓名" min-width="90"/><el-table-column prop="score" label="成绩" width="80"/><el-table-column label="校验结果" min-width="230"><template #default="{row}"><span :class="{'error-text':row.error}">{{row.error||(row.existingId?'已有成绩，默认跳过':'可新增')}}</span></template></el-table-column></el-table><el-checkbox v-if="!result&&previewData.existing&&isAdmin" v-model="overwrite">确认修改预览中已有成绩</el-checkbox><template #footer><el-button @click="importDialog=false">关闭</el-button><el-button v-if="isAdmin&&!result" type="primary" :disabled="!previewData.valid" :loading="saving" @click="confirmImport">确认导入</el-button></template></el-dialog>
  <el-dialog v-model="rankDialog" title="按得分率排名" width="min(650px,94vw)"><el-table :data="ranking"><el-table-column prop="rank" label="排名" width="80"/><el-table-column prop="studentNo" label="学号" min-width="110"/><el-table-column prop="studentName" label="姓名" min-width="100"/><el-table-column prop="percentage" label="得分率(%)" width="120"/></el-table></el-dialog>
</section>
</template>
<script setup>
import {ElTooltip} from 'element-plus/es/components/tooltip/index';
import {ref,reactive,computed,onMounted} from 'vue';
import MobileList from '../components/MobileList.vue';
import {useNarrow} from '../composables/useNarrow';
const narrow=useNarrow();import {ElMessage,ElMessageBox} from 'element-plus';import api from '../api';import StudentPicker from '../components/StudentPicker.vue';import './business.css';
defineProps({classes:Array,isAdmin:Boolean});
const filters=reactive({classId:null,examId:null,subject:'',studentId:null}),exams=ref([]),rows=ref([]),stats=ref([]),history=ref([]),page=ref(1),total=ref(0),loading=ref(false),saving=ref(false),parsing=ref(false),file=ref();
const examDialog=ref(false),scoreDialog=ref(false),importDialog=ref(false),rankDialog=ref(false),ranking=ref([]),examForm=reactive({}),scoreForm=reactive({});
const importExam=ref(),importSubject=ref(''),importFull=ref(100),previewData=ref({rows:[]}),overwrite=ref(false),result=ref();
const scoreClass=computed(()=>exams.value.find(e=>e.id===scoreForm.examId)?.classId);
const appliedSubject=ref(''),coreSubjects=['语文','数学','英语'];
const summarySubjects=computed(()=>{
  const subjects=new Set(rows.value.flatMap(row=>row.subjects));
  return [...coreSubjects.filter(subject=>subjects.delete(subject)),...subjects];
});
function scoreLabel(grade){return grade.status==='ABSENT'?'缺考':`${grade.score} / ${grade.fullScore}`}
function scoreDetail(grade){
  const rate=grade.status==='ABSENT'?`满分 ${grade.fullScore}`:`得分率 ${(grade.score/grade.fullScore*100).toFixed(2)}%`;
  return grade.remark?`${rate} · ${grade.remark}`:rate;
}
let loadSequence=0;
const error=e=>ElMessage.error(e.response?.data?.message||e.message||'请求失败');
function params(){return Object.fromEntries(Object.entries(filters).filter(([,v])=>v!==null&&v!==''&&v!==undefined))}
async function loadExams(){exams.value=(await api.get('/exams',{params:{classId:filters.classId||undefined}})).data.data}
async function load(){const request=++loadSequence;loading.value=true;try{const p=params();const [r,s,h]=await Promise.all([api.get('/scores/summary',{params:{...p,page:page.value-1}}),api.get('/scores/statistics',{params:p}),filters.studentId?api.get(`/scores/history/${filters.studentId}`,{params:{subject:filters.subject||undefined}}):Promise.resolve(null)]);if(request!==loadSequence)return;rows.value=r.data.data.content.map(row=>({...row,gradesBySubject:new Map(row.grades.map(grade=>[grade.subject,grade]))}));appliedSubject.value=p.subject||'';total.value=r.data.data.total;stats.value=s.data.data;history.value=h?.data.data||[]}catch(e){error(e)}finally{if(request===loadSequence)loading.value=false}}
function search(){page.value=1;load()}
async function changeClass(){filters.examId=null;filters.studentId=null;try{await loadExams();search()}catch(e){error(e)}}
function openExam(row){Object.assign(examForm,{id:null,name:'',classId:filters.classId,examDate:null},row||{});examDialog.value=true}
function openScore(row){Object.assign(scoreForm,{id:null,studentId:null,examId:filters.examId,subject:filters.subject||'',score:null,fullScore:100,status:'RECORDED',remark:''},row||{});scoreDialog.value=true}
async function saveExam(){if(saving.value)return;if(!examForm.name?.trim()||!examForm.classId||!examForm.examDate)return ElMessage.warning('请填写考试名称、班级和日期');saving.value=true;try{await api({url:examForm.id?`/exams/${examForm.id}`:'/exams',method:examForm.id?'put':'post',data:examForm});examDialog.value=false;await loadExams();await load();ElMessage.success('考试已保存')}catch(e){error(e)}finally{saving.value=false}}
async function saveScore(){if(saving.value)return;if(!scoreForm.studentId||!scoreForm.examId||!scoreForm.subject?.trim()||!(scoreForm.fullScore>0)||(scoreForm.status==='RECORDED'&&(scoreForm.score===null||scoreForm.score===undefined||scoreForm.score<0||scoreForm.score>scoreForm.fullScore)))return ElMessage.warning('请检查学生、考试、科目和分数');saving.value=true;try{await api({url:scoreForm.id?`/scores/${scoreForm.id}`:'/scores',method:scoreForm.id?'put':'post',data:{...scoreForm,score:scoreForm.status==='ABSENT'?null:scoreForm.score}});scoreDialog.value=false;await load();ElMessage.success('成绩已保存')}catch(e){error(e)}finally{saving.value=false}}
async function download(format){try{const r=await api.get('/scores/import/template',{params:{format},responseType:'blob'});const u=URL.createObjectURL(r.data),a=document.createElement('a');a.href=u;a.download=`成绩模板.${format}`;a.click();setTimeout(()=>URL.revokeObjectURL(u),1000)}catch(e){error(e)}}
function pickFile(){if(!importExam.value||!importSubject.value.trim()||!importFull.value)return ElMessage.warning('请先选择考试、科目和满分');file.value.click()}
async function preview(e){const f=e.target.files[0];e.target.value='';if(!f)return;parsing.value=true;try{const data=new FormData();data.append('file',f);const r=await api.post('/scores/import/preview',data,{params:{examId:importExam.value,subject:importSubject.value,fullScore:importFull.value}});previewData.value=r.data.data;overwrite.value=false;result.value=null;importDialog.value=true}catch(e){error(e)}finally{parsing.value=false}}
async function confirmImport(){try{if(overwrite.value)await ElMessageBox.confirm(`将修改预览中 ${previewData.value.existing} 条已有成绩，确认继续？`,'确认覆盖成绩',{type:'warning',confirmButtonText:'确认修改',cancelButtonText:'取消'});saving.value=true;result.value=(await api.post('/scores/import/confirm',{previewId:previewData.value.previewId,overwrite:overwrite.value})).data.data;await load()}catch(e){if(e!=='cancel'&&e!=='close')error(e)}finally{saving.value=false}}
onMounted(async()=>{try{await loadExams();await load()}catch(e){error(e)}});

// 手机卡片：每科一行，缺考/未录入也照原样展示
const scoreFields=computed(()=>summarySubjects.value.map(subject=>({
  k:subject,label:subject,
  format:row=>row.gradesBySubject.has(subject)
    ?scoreLabel(row.gradesBySubject.get(subject))
    :(row.subjects.includes(subject)?'未录入':'—')
})));
</script>
