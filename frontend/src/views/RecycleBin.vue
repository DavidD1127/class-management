<template>
  <section class="recycle-page" aria-labelledby="recycle-title">
    <div class="page-heading"><div><h1 id="recycle-title">回收站</h1><p class="hint">按类型找回误删数据。恢复保留原编号，遇到冲突不会覆盖现有记录。</p></div><el-button :loading="loading" @click="load">刷新</el-button></div>
    <div class="type-filters" role="group" aria-label="回收站数据类型"><button v-for="(label,key) in types" :key="key" :class="{active:type===key}" :aria-pressed="type===key" @click="type=key;page=1;load()">{{label}} <span>{{key?counts[key]||0:allCount}}</span></button></div>
    <form class="filters" @submit.prevent="page=1;load()"><el-input v-model="q" placeholder="搜索姓名、学号或班级" aria-label="搜索回收站" clearable @clear="page=1;load()"/><el-button native-type="submit">查询</el-button><span class="hint">整组恢复会找回同次删除的全部关联数据。</span></form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon/>
    <p class="mobile-table-help hint">左右滑动表格查看完整数据与恢复按钮。</p>
    <MobileList v-if="narrow" :items="rows" :primary="r=>r.label" :secondary="r=>r.type"
    :badge="r=>r.batchCount>1?('同组 '+r.batchCount+' 条'):''" empty="这里暂无已删除数据"
    :fields="[{k:'source',label:'删除来源'},{k:'deletedAt',label:'删除时间'},{k:'deletedBy',label:'操作人'}]">
    <template #actions="{row}"><el-button link :disabled="saving" @click="restore(row,false)">恢复此条</el-button><el-button v-if="row.batchCount>1" link :disabled="saving" @click="restore(row,true)">恢复整组（{{row.batchCount}}条）</el-button></template>
  </MobileList>
  <el-table v-else v-loading="loading" :data="rows" empty-text="这里暂无已删除数据" row-key="id">
      <el-table-column label="类型" width="100"><template #default="{row}"><el-tag type="info" effect="plain">{{types[row.type]}}</el-tag></template></el-table-column>
      <el-table-column prop="label" label="数据" min-width="200"/><el-table-column prop="source" label="删除来源" min-width="190"/>
      <el-table-column label="删除时间" min-width="165"><template #default="{row}">{{row.deletedAt?.replace('T',' ')}}</template></el-table-column><el-table-column prop="deletedBy" label="操作人" width="100"/>
      <el-table-column label="操作" min-width="210" fixed="right"><template #default="{row}"><el-button link type="primary" :disabled="saving" @click="restore(row,false)">恢复此条</el-button><el-button v-if="row.batchCount>1" link :disabled="saving" @click="restore(row,true)">恢复整组（{{row.batchCount}}条）</el-button></template></el-table-column>
    </el-table>
    <div class="recycle-pagination"><span class="hint">共 {{total}} 条待恢复记录</span><el-pagination v-model:current-page="page" :total="total" :page-size="20" :pager-count="5" layout="prev,pager,next" @current-change="load"/></div>
  </section>
</template>
<script setup>
import MobileList from '../components/MobileList.vue';
import {useNarrow} from '../composables/useNarrow';
const narrow=useNarrow();

import {ref,computed,onMounted} from 'vue';
import {ElMessage,ElMessageBox} from 'element-plus';
import api from '../api';
const emit=defineEmits(['changed']);
const types={'':'全部',classes:'班级',students:'学生',exams:'考试',scores:'成绩',assignments:'作业',assignment_records:'作业记录',attendance:'考勤',praises:'表扬'};
const type=ref(''),q=ref(''),page=ref(1),rows=ref([]),total=ref(0),counts=ref({}),loading=ref(false),saving=ref(false),error=ref('');
const allCount=computed(()=>Object.values(counts.value).reduce((sum,n)=>sum+Number(n),0));
let request=0;
async function load(){const current=++request;loading.value=true;error.value='';try{const data=(await api.get('/recycle-bin',{params:{type:type.value||undefined,q:q.value,page:page.value-1,size:20}})).data.data;if(current!==request)return;rows.value=data.content;total.value=data.total;counts.value=Object.fromEntries(data.types.map(row=>[row.type,row.total]));}catch(e){if(current===request)error.value=e.response?.data?.message||e.message}finally{if(current===request)loading.value=false}}
async function restore(row,batch){if(saving.value)return;saving.value=true;error.value='';try{const preview=(await api.get(`/recycle-bin/${row.id}/preview`,{params:{batch}})).data.data;await ElMessageBox.confirm(`将恢复“${preview.label}”${batch?'及同组关联数据':''}，共 ${preview.total} 条。${preview.rule}`,'确认恢复',{confirmButtonText:'确认恢复',cancelButtonText:'取消',type:'warning'});const result=(await api.post(`/recycle-bin/${row.id}/restore`,null,{params:{batch}})).data.data;ElMessage.success(`已恢复 ${result.restored} 条记录`);await load();emit('changed')}catch(e){if(e!=='cancel'&&e!=='close')error.value=e.response?.data?.message||e.message}finally{saving.value=false}}
onMounted(load);
</script>
<style scoped>
.recycle-page{max-width:1400px;margin:auto}.type-filters{display:flex;gap:8px;flex-wrap:wrap;margin:26px 0 12px}.type-filters button{border:1px solid var(--border);border-radius:8px;background:#fff;padding:9px 13px;font:inherit;color:var(--ink);cursor:pointer}.type-filters button.active{border-color:var(--primary);color:var(--primary);background:#edf3fc}.type-filters button:focus-visible{outline:2px solid var(--primary);outline-offset:3px}.type-filters span{margin-left:6px;font-size:12px;font-variant-numeric:tabular-nums}.filters .el-input{width:280px}.filters .hint{font-size:12px;margin-left:auto}.recycle-pagination{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;margin-top:16px}@media(max-width:700px){.filters .el-input{width:100%}.filters .hint{margin:0}.type-filters{gap:6px}.type-filters button{padding:8px 10px}}
</style>
<style scoped>.mobile-table-help{display:none}@media(max-width:700px){.mobile-table-help{display:block;font-size:12px}.recycle-page :deep(.el-table__cell.is-fixed-column--right){position:static!important;right:auto!important}.recycle-page :deep(.el-table__cell.is-fixed-column--right::before){display:none}}</style>
