<template><el-config-provider :locale="zhCn"><router-view/></el-config-provider></template>
<script setup>
import {ElConfigProvider} from 'element-plus/es/components/config-provider/index';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
</script>
