<template><el-tag :type="type" effect="light" size="small">{{label}}</el-tag></template>
<script setup>
import {computed} from 'vue';
const props=defineProps({status:String,kind:String});
const label=computed(()=>props.status==='ABSENT'?(props.kind==='score'?'缺考':'缺勤'):({LATE:props.kind==='attendance'?'迟到':'迟交',EARLY_LEAVE:'早退',PENDING:'待完成',COMPLETED:'已完成',INCOMPLETE:'未完成',UNCONFIRMED:'逾期待确认'}[props.status]||props.status));
const type=computed(()=>({COMPLETED:'success',PENDING:'info',LATE:'warning',EARLY_LEAVE:'warning',ABSENT:'danger',INCOMPLETE:'danger',UNCONFIRMED:'warning'}[props.status]||'info'));
</script>
