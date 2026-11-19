<template>
  <section class="dashboard" aria-labelledby="dashboard-title" :aria-busy="loading">
    <div class="page-heading"><div><p class="eyebrow">工作概览</p><h1 id="dashboard-title">班级工作台</h1><p class="hint">从真实教学记录了解班级近况，开始今天的管理工作。</p></div><el-button :loading="loading" @click="load">刷新数据</el-button></div>
    <section class="daily-tools" aria-label="常用教学操作">
      <div class="tools-heading"><h2>开始今天的班级工作</h2><p>常用事项，一步进入</p></div>
      <div class="tool-grid">
        <button v-for="tool in tools" :key="tool.key" :class="['tool-link',tool.key]" @click="$emit('view',tool.key)"><span class="tool-symbol" aria-hidden="true">{{tool.symbol}}</span><span><strong>{{tool.label}}</strong><small>{{tool.detail}}</small></span><span class="tool-arrow" aria-hidden="true">↗</span></button>
      </div>
    </section>
    <div class="filters"><el-select v-model="classId" clearable placeholder="全部班级" aria-label="统计班级"><el-option v-for="c in classes" :key="c.id" :value="c.id" :label="c.className+' · '+c.academicYear"/></el-select><el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="统计开始日期" end-placeholder="统计结束日期" aria-label="统计日期范围" :clearable="false"/><el-button type="primary" :loading="loading" @click="load">应用范围</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon><el-button link type="primary" @click="load">重新加载</el-button></el-alert>
    <el-skeleton v-if="loading&&!data" :rows="8" animated/>
    <template v-if="data&&!error">
      <p class="hint scope">{{data.from}} 至 {{data.to}}（含首尾日期） · {{scopeName}} · Asia/Shanghai</p>
      <div class="metric-grid">
        <MetricCard label="学生总数" :value="data.students" detail="当前档案，含在读与离校"/>
        <MetricCard label="班级数量" :value="data.classes" detail="当前所选班级范围"/>
        <MetricCard label="最新考试平均分" :value="average?Number(average.averageScore).toFixed(2):'—'" :detail="average?`${data.latestExam.name} · ${average.subject} / ${average.fullScore}分 · 实考${average.recorded}人`:'暂无已录入的实考成绩'"/>
        <MetricCard label="作业完成率" :value="denominator?completion+'%':'—'" :detail="`范围内布置的作业：完成 ${completed} / 纳入 ${denominator} 人次`"/>
        <MetricCard label="考勤异常记录" :value="attendanceTotal" :detail="`迟到 ${attendanceCounts.LATE} · 早退 ${attendanceCounts.EARLY_LEAVE} · 缺勤 ${attendanceCounts.ABSENT}`"/>
        <MetricCard label="表扬记录" :value="data.praises" detail="统计日期范围内的表扬次数"/>
      </div>
      <div class="chart-grid">
        <section class="surface"><h2>考勤记录趋势</h2><p class="hint">按发生日期统计次数；空白日期为零条记录。统计期间无记录时不绘制趋势。</p><div class="legend"><span v-for="(label,key) in attendanceLabels" :key="key"><i :class="key"/>{{label}}</span></div>
          <template v-if="attendanceTotal"><svg class="attendance-chart" viewBox="0 0 720 190" role="img" aria-label="统计期间每日迟到、早退和缺勤次数趋势"><path d="M30 15V160H700" fill="none" stroke="var(--border)"/><g v-for="(label,key) in attendanceLabels" :key="key"><polyline :points="points(key)" fill="none" :stroke="colors[key]" stroke-width="2.5"/><circle v-for="(day,i) in days.filter(d=>d[key])" :key="i" :cx="x(days.indexOf(day))" :cy="160-day[key]/maxAttendance*135" r="3" :fill="colors[key]"><title>{{day.date}} {{label}} {{day[key]}}次</title></circle></g><text x="30" y="184">{{data.from}}</text><text x="700" y="184" text-anchor="end">{{data.to}}</text><text x="30" y="12">最高 {{maxAttendance}} 次 / 日</text></svg><details><summary>查看考勤图表数据</summary><el-table :data="days.filter(d=>d.LATE+d.EARLY_LEAVE+d.ABSENT)" max-height="250"><el-table-column prop="date" label="日期"/><el-table-column v-for="(label,key) in attendanceLabels" :key="key" :prop="key" :label="label"/></el-table></details></template>
          <el-empty v-else description="该范围暂无考勤异常记录" :image-size="64"/>
        </section>
        <section class="surface"><h2>作业完成情况</h2><p class="hint">按纳入统计的学生名单人次汇总；已完成与迟交均计入完成。待完成含逾期待确认。</p><div v-if="denominator" class="distribution"><div v-for="item in assignmentBars" :key="item.status"><div class="bar-label"><span>{{item.label}}</span><strong>{{item.total}} <small>人次</small></strong></div><el-progress :percentage="Number((item.total/denominator*100).toFixed(1))" :color="item.color" :stroke-width="10"/></div></div><el-empty v-else description="该范围暂无纳入统计的作业名单" :image-size="64"/></section>
      </div>
      <section class="surface exam-summary"><div><h2>最新考试口径</h2><p class="hint">截至结束日期最近的一场考试；同日按考试 ID 排序。各科目、满分分别统计，缺考与未录入不计平均分。</p></div><el-select v-if="data.examAverages.length" v-model="averageIndex" aria-label="最新考试统计科目"><el-option v-for="(item,i) in data.examAverages" :key="i" :value="i" :label="`${item.subject} / ${item.fullScore} 分`"/></el-select><p class="hint">{{data.latestExam?`${data.latestExam.name} · ${data.latestExam.examDate}`:'暂无考试'}}。首页不合并科目总分，也不推断跨考试的进步排名。</p></section>
      <div class="quick-links"><el-button @click="$emit('view','students')">管理学生</el-button><el-button @click="$emit('view','scores')">查看成绩</el-button><el-button type="primary" @click="$emit('view','ai')">打开 AI 班级助手</el-button></div>
    </template>
  </section>
</template>
<script setup>
import {ref,computed,onMounted} from 'vue';
import api from '../api';
import MetricCard from './MetricCard.vue';
const props=defineProps({classes:Array});defineEmits(['view']);
const tools=[{key:'attendance',symbol:'勤',label:'考勤管理',detail:'查看与记录出勤情况'},{key:'assignments',symbol:'业',label:'作业管理',detail:'布置作业，跟进完成情况'},{key:'praises',symbol:'赞',label:'表扬管理',detail:'记录每一次值得肯定的表现'},{key:'ai',symbol:'AI',label:'AI 班级助手',detail:'用自然语言查询教学记录'}];
const today=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date()),start=new Date(today+'T00:00:00Z');start.setUTCDate(start.getUTCDate()-29);
const range=ref([start.toISOString().slice(0,10),today]),classId=ref(),data=ref(),loading=ref(false),error=ref(''),averageIndex=ref(0),appliedClass=ref();
const scopeName=computed(()=>props.classes.find(c=>c.id===appliedClass.value)?.className||'全部班级');
const average=computed(()=>data.value?.examAverages[averageIndex.value]);
const states=computed(()=>Object.fromEntries((data.value?.assignmentStates||[]).map(s=>[s.status,Number(s.total)])));
const denominator=computed(()=>Object.values(states.value).reduce((a,b)=>a+b,0)),completed=computed(()=>(states.value.COMPLETED||0)+(states.value.LATE||0)),completion=computed(()=>(completed.value/denominator.value*100).toFixed(1));
const attendanceLabels={LATE:'迟到',EARLY_LEAVE:'早退',ABSENT:'缺勤'},colors={LATE:'#b7791f',EARLY_LEAVE:'#526eab',ABSENT:'#c25555'};
const attendanceCounts=computed(()=>{const counts={LATE:0,EARLY_LEAVE:0,ABSENT:0};for(const row of data.value?.attendance||[])counts[row.type]+=Number(row.total);return counts});
const attendanceTotal=computed(()=>Object.values(attendanceCounts.value).reduce((a,b)=>a+b,0));
const days=computed(()=>{if(!data.value)return [];const rows=[],cursor=new Date(data.value.from+'T00:00:00Z');while(cursor.toISOString().slice(0,10)<=data.value.to){rows.push({date:cursor.toISOString().slice(0,10),LATE:0,EARLY_LEAVE:0,ABSENT:0});cursor.setUTCDate(cursor.getUTCDate()+1)}const index=Object.fromEntries(rows.map(r=>[r.date,r]));for(const row of data.value.attendance)index[row.occurredDate][row.type]=Number(row.total);return rows});
const maxAttendance=computed(()=>Math.max(1,...days.value.flatMap(d=>Object.keys(colors).map(k=>d[k]))));
const x=i=>30+(days.value.length===1?335:i/(days.value.length-1)*670),points=key=>days.value.map((d,i)=>`${x(i)},${160-d[key]/maxAttendance.value*135}`).join(' ');
const assignmentBars=computed(()=>[{status:'COMPLETED',label:'已完成',color:'#36836f'},{status:'LATE',label:'迟交',color:'#b7791f'},{status:'INCOMPLETE',label:'未完成',color:'#c25555'},{status:'PENDING',label:'待完成 / 逾期待确认',color:'#8794a8'}].map(s=>({...s,total:states.value[s.status]||0})));
async function load(){if(loading.value)return;loading.value=true;error.value='';try{const r=await api.get('/dashboard',{params:{classId:classId.value||undefined,from:range.value?.[0],to:range.value?.[1]}});data.value=r.data.data;appliedClass.value=classId.value;averageIndex.value=0}catch(e){error.value=e.response?.data?.message||e.message||'统计加载失败，请重试'}finally{loading.value=false}}
onMounted(load);
</script>
<style scoped>
.daily-tools{margin:26px 0;background:#edf3fa;border:1px solid #dce6f2;border-radius:16px;padding:22px}.tools-heading{display:flex;align-items:baseline;gap:14px;margin-bottom:18px}.tools-heading h2{font-size:16px;margin:0}.tools-heading p{font-size:13px;color:var(--muted);margin:0}.tool-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.tool-link{display:flex;align-items:center;gap:12px;text-align:left;background:#fff;border:1px solid #e0e7ee;border-radius:12px;padding:18px 14px;cursor:pointer;color:var(--ink);transition:background .16s,border-color .16s}.tool-link:hover{background:#f9fcff;border-color:#9bb6d7}.tool-link:focus-visible{outline:2px solid var(--primary);outline-offset:3px}.tool-symbol{display:grid;place-items:center;flex-shrink:0;width:40px;height:40px;border-radius:12px;background:#e8f2ec;color:#326b52;font-weight:650}.assignments .tool-symbol{background:#fff2db;color:#8b621e}.praises .tool-symbol{background:#fcebe5;color:#a05037}.ai .tool-symbol{background:#e8effa;color:var(--primary);font-size:13px}.tool-link strong{display:block;font-size:14px;font-weight:600}.tool-link small{display:block;font-size:12px;line-height:1.6;color:var(--muted);margin-top:5px}.tool-arrow{margin-left:auto;color:var(--muted)}.metric-grid :deep(.metric-card){position:relative;padding:20px 22px 20px 26px}.metric-grid :deep(.metric-card)::before{content:'';position:absolute;left:0;top:24px;height:26px;width:3px;background:#8aa9ce;border-radius:0 3px 3px 0}.metric-grid :deep(.metric-card:nth-child(4))::before,.metric-grid :deep(.metric-card:nth-child(6))::before{background:#78a58c}.scope{padding-left:2px}.exam-summary{background:#fafbf9}@media(max-width:1280px){.tool-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:600px){.daily-tools{padding:16px}.tools-heading{display:block}.tools-heading p{margin-top:6px}.tool-link{padding:14px 10px;gap:8px}.tool-symbol{width:32px;height:32px}.tool-arrow{display:none}.tool-link small{font-size:11px}}
.metric-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin:20px 0 24px}.chart-grid{display:grid;grid-template-columns:1.35fr 1fr;gap:20px}.surface h2{font-size:17px;margin:0 0 8px}.surface .hint{font-size:13px}.attendance-chart{width:100%;height:auto;margin-top:20px}.attendance-chart text{font-size:12px;fill:var(--muted)}.legend{display:flex;gap:18px;font-size:12px}.legend i{display:inline-block;width:9px;height:9px;margin-right:6px;border-radius:50%}.LATE{background:#b7791f}.EARLY_LEAVE{background:#526eab}.ABSENT{background:#c25555}.distribution{display:grid;gap:20px;margin:24px 0}.bar-label{display:flex;justify-content:space-between;font-size:14px;margin-bottom:8px}.bar-label small{font-weight:400;color:var(--muted)}.exam-summary{margin-top:20px}.exam-summary .el-select{width:240px}.quick-links{display:flex;gap:12px;flex-wrap:wrap;margin-top:24px}.scope{font-size:13px}summary{cursor:pointer;font-size:13px;color:var(--primary);padding:8px 0}@media(max-width:1100px){.chart-grid{grid-template-columns:1fr}.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:600px){.metric-grid{grid-template-columns:1fr}}
</style>
<style scoped>@media(min-width:360px) and (max-width:600px){.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.metric-grid :deep(.metric-card){padding:16px}.metric-grid :deep(.metric-card strong){font-size:27px}.metric-grid :deep(.metric-card p){font-size:12px}}</style>
