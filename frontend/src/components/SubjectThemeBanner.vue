<template>
  <section v-if="theme" class="subject-banner" :class="{'is-compact':compact}" :aria-label="theme.subject+'主题'">
    <SubjectCover :theme="theme" wide :compact="compact"/>
  </section>
</template>
<script setup>
import {computed} from 'vue';
import {subjectThemes} from '../subject-themes';
import SubjectCover from './SubjectCover.vue';
const props=defineProps({themeId:String,compact:Boolean});
const theme=computed(()=>subjectThemes.find(item=>item.id===props.themeId));
</script>
<style scoped>
.subject-banner{margin:0 0 26px;border:1px solid var(--subject-line);border-radius:4px;overflow:hidden}.subject-banner.is-compact{margin-bottom:20px}@media(max-width:767px){.subject-banner{margin-bottom:22px}}
</style>
