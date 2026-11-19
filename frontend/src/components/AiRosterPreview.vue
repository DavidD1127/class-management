<template>
  <section class="roster-preview" aria-label="学生名单导入预览">
    <h3>学生名单 · {{result?'已导入':'待核对'}}</h3>
    <p>共 {{preview.rows.length}} 行，有效 {{preview.valid}} 行，需检查 {{preview.errors}} 行。已有学生不会被覆盖。</p>
    <el-table :data="preview.rows" max-height="350" empty-text="没有名单记录">
      <el-table-column prop="rowNumber" label="行" width="55"/>
      <el-table-column prop="studentNo" label="学号" min-width="110"/>
      <el-table-column prop="name" label="姓名" min-width="85"/>
      <el-table-column prop="className" label="班级" min-width="130"/>
      <el-table-column label="检查结果" min-width="180"><template #default="{row}"><span :class="{invalid:row.error}">{{row.error||'校验通过'}}</span></template></el-table-column>
    </el-table>
    <el-alert v-if="error" :title="error" type="error" :closable="false"/>
    <el-alert v-if="result" :title="`已导入 ${result.success} 人，跳过 ${result.skipped} 行`" type="success" :closable="false"/>
    <el-button v-if="!result&&isAdmin" type="primary" :loading="saving" :disabled="!preview.valid" @click="confirm">确认导入 {{preview.valid}} 人</el-button>
    <p v-if="!isAdmin" class="hint">请由管理员确认正式导入。</p>
  </section>
</template>
<script setup>
import {ref} from 'vue';import api from '../api';
const props=defineProps({preview:{type:Object,required:true},isAdmin:Boolean});
const saving=ref(false),result=ref(),error=ref('');
async function confirm(){if(saving.value)return;saving.value=true;error.value='';try{result.value=(await api.post('/students/import/confirm',{previewId:props.preview.previewId})).data.data}catch(e){error.value=e.response?.data?.message||e.message}finally{saving.value=false}}
</script>
<style scoped>.roster-preview{margin-top:20px}.roster-preview h3{font-size:16px}.roster-preview>.el-button{margin-top:16px}.invalid{color:#b42318}</style>
