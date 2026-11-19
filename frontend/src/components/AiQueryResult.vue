<template>
  <details class="query-result" :open="expanded">
    <summary>{{names[query.kind]||'数据库查询'}} · {{rows.length}} 条</summary>
    <p class="hint">{{query.result.range||query.result.notice}}</p><details v-if="query.result.rules"><summary>查看统计口径</summary><p class="hint">{{query.result.rules}}</p></details>
    <el-table :data="rows" max-height="320" empty-text="本轮数据库查询无匹配记录">
      <el-table-column v-for="key in columns" :key="key" :label="labels[key]" min-width="110"><template #default="{row}"><StatusTag v-if="key==='status'||key==='type'" :status="row[key]" :kind="query.kind==='scores'?'score':query.kind==='attendance'?'attendance':undefined"/><span v-else>{{format(row[key])}}</span></template></el-table-column>
    </el-table>
    <template v-if="query.kind==='score_statistics'||query.kind==='score_leaders'"><details v-for="(group,i) in rows" :key="i"><summary>{{group.examName}} · {{group.subject}} 排名</summary><el-table :data="group.ranking||[]" max-height="300" empty-text="没有实考成绩"><el-table-column prop="rank" label="排名"/><el-table-column prop="studentNo" label="学号"/><el-table-column prop="studentName" label="学生"/><el-table-column prop="score" label="得分"/><el-table-column prop="fullScore" label="满分"/><el-table-column v-if="query.kind!=='score_leaders'" prop="percentage" label="得分率(%)"/></el-table></details></template>
  </details>
</template>
<script setup>
import {computed} from 'vue';import StatusTag from './StatusTag.vue';
const props=defineProps({query:Object,expanded:{type:Boolean,default:true}});
const names={recycle_bin:'回收站记录',classes:'班级列表',students:'学生匹配',exams:'考试列表',scores:'成绩记录',score_statistics:'成绩统计',score_leaders:'最高分与排名',assignments:'作业完成记录',assignment_catalog:'作业列表',assignment_statistics:'作业统计',attendance:'考勤记录',attendance_statistics:'考勤统计',praises:'表扬记录',praise_statistics:'表扬统计'};
const labels={id:'记录编号',recordId:'原记录编号',label:'数据',source:'删除来源',deletedAt:'删除时间',deletedBy:'删除人',batchCount:'同组待恢复数',studentNo:'学号',studentName:'学生',name:'名称',className:'班级',grade:'年级',academicYear:'学年',studentCount:'人数',examName:'考试',examDate:'考试日期',subject:'科目',score:'得分',fullScore:'满分',status:'状态',type:'类型',occurredDate:'发生日期',occurredTime:'时间',praiseDate:'表扬日期',reason:'表扬原因',assignmentName:'作业',assignedDate:'布置日期',deadline:'截止时间',submittedAt:'提交时间',included:'纳入统计',highestScore:'最高分',incomplete:'缺科人数',averageScore:'平均分',averagePercentage:'平均得分率(%)',recorded:'实考人数',absent:'缺考人数',passed:'及格人数',passRate:'及格率(%)',count:'表扬次数',LATE:'迟到 / 迟交',EARLY_LEAVE:'早退',ABSENT:'缺勤',COMPLETED:'已完成',PENDING:'待完成',INCOMPLETE:'未完成',UNCONFIRMED:'逾期待确认',denominator:'统计分母',excluded:'排除人数',completionRate:'完成率(%)'};
const rows=computed(()=>{const result=props.query.result;return result.matches|| (Array.isArray(result.data)?result.data:result.data?[result.data]:[])});
const columns=computed(()=>Object.keys(labels).filter(k=>rows.value.some(row=>Object.hasOwn(row,k))));
const format=value=>value===null||value===undefined?'—':typeof value==='boolean'?(value?'是':'否'):value;
</script>
<style scoped>.query-result{margin-top:18px;border-top:1px solid var(--border);padding-top:10px}summary{cursor:pointer;color:var(--primary);font-size:13px;padding:8px 0}.hint{font-size:12px}</style>
