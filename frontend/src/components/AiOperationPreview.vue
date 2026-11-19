<template>
  <section class="operation" aria-label="操作预览与执行结果">
    <h3>{{ kinds[operation.kind] }} · {{ states[operation.status] || operation.status }}</h3>
    <p class="hint">操作 ID：{{ operation.id }} · 时区：Asia/Shanghai</p>
    <p v-if="operation.status==='PENDING'" class="hint">尚未写入。请核对以下内容，预览有效期15分钟。确认后产生真实业务记录。</p>
    <template v-if="operation.kind.startsWith('recycle_')"><p><strong>{{preview.label}}</strong></p><p>{{preview.rule}}</p><el-descriptions :column="2" border><el-descriptions-item v-for="(count,type) in preview.counts" :key="type" :label="recycleTypes[type]||type">{{count}} 条</el-descriptions-item><el-descriptions-item label="合计">{{preview.total}} 条</el-descriptions-item></el-descriptions></template>
    <template v-else-if="preview.rows">
      <p>{{ preview.rule }}</p>
      <el-table :data="displayedRows" border max-height="400" empty-text="无成绩记录">
        <el-table-column prop="studentNo" label="学号" min-width="120"/>
        <el-table-column prop="name" label="姓名" min-width="90"/>
        <el-table-column prop="subject" label="科目" width="80"/>
        <el-table-column prop="examName" label="考试" min-width="120"/>
        <el-table-column prop="score" label="得分" width="80"/>
        <el-table-column prop="fullScore" label="满分" width="80"/>
        <el-table-column prop="state" label="识别状态" width="95"/>
        <el-table-column prop="error" label="错误或冲突原因" min-width="220"/>
      </el-table>
    </template>
    <template v-else>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="学生">{{ preview.student?.name }}（{{ preview.student?.studentNo }}）</el-descriptions-item>
        <el-descriptions-item label="班级">{{ preview.student?.className }}</el-descriptions-item>
        <el-descriptions-item v-for="row in details" :key="row.label" :label="row.label">{{ row.value }}</el-descriptions-item>
      </el-descriptions>
      <details v-if="preview.before"><summary>查看修改前的原始记录</summary><el-descriptions :column="1" border><el-descriptions-item v-for="row in before" :key="row.label" :label="row.label">{{ row.value }}</el-descriptions-item></el-descriptions></details>
      <el-alert v-if="preview.duplicates?.length" title="存在同日疑似重复记录。确认表示仍要新增，请先核对。" type="warning" :closable="false" show-icon/>
      <el-table v-if="preview.duplicates?.length" :data="preview.duplicates" border><el-table-column prop="id" label="已有记录 ID"/><el-table-column prop="type" label="类型"/><el-table-column prop="reason" label="原因"/><el-table-column prop="remark" label="备注"/></el-table>
    </template>
    <el-alert v-if="operation.failure" :title="operation.failure" type="error" :closable="false" show-icon/>
    <div v-if="operation.result" role="status">
      <p v-if="operation.kind.startsWith('recycle_')">实际执行结果：移入回收站 {{operation.result.deleted||0}} 条，恢复 {{operation.result.restored||0}} 条。</p><p v-else>实际执行结果：新增 {{ operation.result.added || 0 }} 条，修改 {{ operation.result.updated || 0 }} 条，跳过 {{ operation.result.skipped || 0 }} 条，失败 {{ operation.result.failed || 0 }} 条。</p>
      <p v-if="operation.result.record">保存的记录 ID：{{ operation.result.record.id }}；更新时间：{{ operation.result.record.updatedAt || operation.result.record.createdAt }}</p>
      <el-descriptions v-if="operation.result.record" :column="2" border><el-descriptions-item v-for="row in saved" :key="row.label" :label="row.label">{{ row.value }}</el-descriptions-item></el-descriptions>
      <el-button @click="$emit('view', target)">查看业务记录</el-button>
    </div>
    <p v-if="operation.status==='FAILED'">本次操作未提交业务变更；请修正问题后重新发送指令生成预览。</p>
    <div class="actions">
      <el-button v-if="operation.status==='PENDING'" type="primary" :disabled="busy || !validRows" @click="$emit('action','confirm')">{{preview.duplicates?.length?'确认仍要新增':'确认提交'}}</el-button>
      <el-button v-if="operation.status==='PENDING'" :disabled="busy" @click="$emit('action','cancel')">取消操作</el-button>
      <el-button :disabled="busy" @click="$emit('action','refresh')">刷新实际状态</el-button>
    </div>
  </section>
</template>
<script setup>
import {computed} from 'vue';
const props=defineProps({operation:{type:Object,required:true},busy:Boolean});
defineEmits(['action','view']);
const recycleTypes={classes:'班级',students:'学生',exams:'考试',assignments:'作业',scores:'成绩',assignment_records:'作业记录',attendance:'考勤',praises:'表扬'};
const kinds={attendance_add:'新增考勤',attendance_edit:'修改考勤备注',assignment_state:'修改作业状态',praise_add:'新增表扬',praise_edit:'修改表扬',scores_add:'批量录入成绩',recycle_delete:'移入回收站',recycle_restore:'恢复数据'};
const states={PENDING:'待确认',CANCELLED:'已取消',SUCCEEDED:'已保存',FAILED:'执行失败'};
const labels={id:'记录 ID',date:'日期',occurredDate:'考勤日期',occurredTime:'发生时间',time:'发生时间',type:'考勤类型',remark:'备注',reason:'表扬原因',praiseDate:'表扬日期',status:'状态',submittedAt:'实际提交时间',assignmentId:'作业 ID',studentId:'学生 ID',recorder:'原记录人',createdAt:'创建时间',updatedAt:'更新时间',recordId:'目标记录 ID'};
const values={LATE:'迟到 / 迟交',EARLY_LEAVE:'早退',ABSENT:'缺勤',COMPLETED:'已完成',INCOMPLETE:'未完成',PENDING:'待提交'};
const preview=computed(()=>props.operation.preview);
const validRows=computed(()=>!preview.value.rows||preview.value.rows.some(r=>r.state==='待确认'));
const displayedRows=computed(()=>(preview.value.rows||[]).map(row=>({...row,state:row.state==='待确认'?({SUCCEEDED:'已新增',FAILED:'未提交（回滚）',CANCELLED:'已取消'}[props.operation.status]||row.state):row.state})));
const details=computed(()=>{
  const rows=Object.entries(preview.value).filter(([k])=>labels[k]).map(([k,v])=>({label:labels[k],value:values[v]||v||'无'}));
  if(preview.value.assignment)rows.push({label:'具体作业',value:`${preview.value.assignment.name} · ${preview.value.assignment.subject} · ${preview.value.assignment.assignedDate} · ID ${preview.value.assignment.id}`});
  return rows;
});
const before=computed(()=>Object.entries(preview.value.before||{}).filter(([k])=>labels[k]).map(([k,v])=>({label:labels[k],value:values[v]||v||'无'})));
const saved=computed(()=>Object.entries(props.operation.result?.record||{}).filter(([k])=>labels[k]).map(([k,v])=>({label:labels[k],value:values[v]||v||'无'})));
const target=computed(()=>props.operation.kind.startsWith('recycle_')?'recycle':props.operation.kind.startsWith('attendance')?'attendance':props.operation.kind.startsWith('praise')?'praises':props.operation.kind==='scores_add'?'scores':'assignments');
</script>
<style scoped>
.operation{margin-top:16px;overflow:auto}.hint{color:#526179;overflow-wrap:anywhere}.actions{display:flex;gap:8px;flex-wrap:wrap;margin:16px 0}.actions .el-button{margin:0}details{margin:16px 0}summary{cursor:pointer;padding:8px 0}.el-alert{margin-top:12px}
</style>
