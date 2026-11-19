<template><el-select :model-value="modelValue" filterable remote clearable :remote-method="search" :loading="loading" placeholder="输入姓名或学号查询学生" aria-label="查询学生" @update:model-value="$emit('update:modelValue',$event)"><el-option v-for="s in options" :key="s.id" :value="s.id" :label="`${s.name} · ${s.studentNo} · ${s.className}`"/></el-select></template>
<script setup>
import {ref,watch} from 'vue';
import {ElMessage} from 'element-plus';
import api from '../api';
const props=defineProps({modelValue:Number,classId:Number});defineEmits(['update:modelValue']);
const options=ref([]),loading=ref(false);let sequence=0;
async function search(q=''){const id=++sequence;loading.value=true;try{const r=await api.get('/students',{params:{q,classId:props.classId||undefined,size:30}});if(id===sequence)options.value=r.data.data.content}catch(e){ElMessage.error(e.response?.data?.message||'学生查询失败')}finally{if(id===sequence)loading.value=false}}
watch(()=>[props.classId,props.modelValue],async()=>{await search();if(props.modelValue&&!options.value.some(s=>s.id===props.modelValue)){try{options.value.push((await api.get(`/students/${props.modelValue}`)).data.data)}catch(e){ElMessage.error(e.response?.data?.message||'学生不存在')}}},{immediate:true});
</script>
