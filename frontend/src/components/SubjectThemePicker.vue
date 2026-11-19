<template>
  <el-button class="theme-trigger" @click="open=true">学科主题 · {{current?.subject || '默认'}}</el-button>
  <el-dialog v-model="open" title="选择学科主题" width="min(1080px, 94vw)" class="subject-theme-dialog" :close-on-click-modal="false">
    <div class="theme-studio">
      <section class="theme-feature" aria-label="当前主题预览">
        <SubjectCover v-if="current" :theme="current"/>
        <div v-else class="default-preview"><span>教学工作空间</span><strong>默认主题</strong><p>熟悉的蓝白配色</p></div>
        <div class="feature-copy"><h2>{{current?.name || '默认主题'}}</h2><p>{{current?.description || '简洁的工作界面，保留原始外观。'}}</p><a v-if="current?.source" :href="current.source" target="_blank" rel="noopener noreferrer">查看馆藏原作 ↗</a><span v-else class="feature-material">{{current?.material || '默认界面配色'}}</span></div>
      </section>
      <section class="theme-collection" aria-label="选择学科主题">
        <div class="collection-heading"><h3>像翻开一本课本</h3><p>以真实作品、学科符号和纸面排版区分主题。点击即可应用。</p></div>
        <div class="subject-theme-grid" role="group" aria-label="学科主题">
          <button v-for="theme in subjectThemes" :key="theme.id" class="subject-theme-card" :aria-label="theme.subject+' '+theme.name" :aria-pressed="selected===theme.id" :style="{'--swatch':theme.primary}" @click="select(theme.id)">
            <SubjectCover :theme="theme"/>
            <span class="theme-card-state">{{selected===theme.id?'使用中':'应用主题'}}</span>
          </button>
          <button class="subject-theme-card default-theme" :aria-pressed="selected==='default'" @click="select('default')"><span class="default-card-art"><span>教学工作空间</span><strong>默认主题</strong><span>蓝白配色</span></span><span class="theme-card-state">{{selected==='default'?'使用中':'恢复默认'}}</span></button>
        </div>
      </section>
    </div>
    <template #footer><p role="status" aria-live="polite" class="theme-status">{{status || '当前主题：'+(current?.subject || '默认')}}</p><el-button type="primary" @click="open=false">完成</el-button></template>
  </el-dialog>
</template>
<script setup>
import {computed,ref} from 'vue';
import {subjectThemes,applySubjectTheme,themeStorageKey} from '../subject-themes';
import SubjectCover from './SubjectCover.vue';
const emit=defineEmits(['change']);
const open=ref(false),selected=ref(document.documentElement.dataset.subject || 'default'),status=ref('');
const current=computed(()=>subjectThemes.find(item=>item.id===selected.value));
function select(id){
  selected.value=applySubjectTheme(id);
  emit('change',selected.value);
  status.value='已应用'+(current.value?.subject || '默认')+'主题';
  try{localStorage.setItem(themeStorageKey,selected.value)}catch{status.value+='；浏览器未允许保存，刷新后将恢复默认'}
}
</script>
<style scoped>
.theme-trigger{white-space:nowrap}.theme-studio{display:grid;grid-template-columns:250px minmax(0,1fr);gap:30px;align-items:start}.theme-feature{position:sticky;top:0;min-width:0}.theme-feature>.subject-cover{border:1px solid var(--border);border-left:5px solid var(--primary)}.feature-copy{padding:18px 2px 0}.feature-copy h2{font-size:17px;margin:0 0 8px;color:var(--ink)}.feature-copy p{font-size:12px;color:var(--muted);line-height:1.8;margin:0 0 10px}.feature-copy a,.feature-material{font-size:11px;color:var(--primary)}.feature-copy a{text-underline-offset:3px}.feature-copy a:focus-visible{outline:2px solid var(--primary);outline-offset:3px}.collection-heading{margin:0 0 20px}.collection-heading h3{font-size:20px;margin:0 0 8px;color:var(--ink)}.collection-heading p{font-size:12px;line-height:1.7;color:var(--muted);margin:0}.subject-theme-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px 16px}.subject-theme-card{--swatch:#315eaa;display:block;min-width:0;text-align:left;padding:0;background:#fdfdf9;border:1px solid var(--border);border-radius:3px;overflow:hidden;color:var(--ink);cursor:pointer}.subject-theme-card:hover{border-color:var(--swatch)}.subject-theme-card:active{opacity:.88}.subject-theme-card[aria-pressed=true]{outline:2px solid var(--swatch);outline-offset:2px}.subject-theme-card:focus-visible{outline:3px solid var(--swatch);outline-offset:3px}.theme-card-state{display:block;padding:8px 10px;font-size:11px;color:var(--swatch);border-top:1px solid var(--border);text-align:right;line-height:1.5}.theme-status{display:inline-block;margin:0 auto 0 0;color:var(--muted);font-size:12px;text-align:left}.default-card-art{aspect-ratio:3 / 4;display:flex;flex-direction:column;justify-content:center;gap:18px;padding:20px;border-left:5px solid #315eaa;background:#eef2f6;color:#315eaa}.default-card-art>span{font-size:11px}.default-card-art strong{font-size:clamp(20px,2vw,29px);font-family:'SimSun',serif;letter-spacing:2px}.default-preview{aspect-ratio:3 / 4;display:flex;flex-direction:column;justify-content:center;gap:20px;padding:26px;border-left:5px solid #315eaa;background:#eef2f6;color:#315eaa}.default-preview strong{font-family:'SimSun',serif;font-size:32px}.default-preview span,.default-preview p{font-size:12px;margin:0}@media(max-width:900px) and (min-width:768px){.theme-studio{grid-template-columns:220px minmax(0,1fr);gap:24px}.subject-theme-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:767px){.theme-studio{grid-template-columns:1fr;gap:24px}.theme-feature{display:grid;grid-template-columns:minmax(0,40%) minmax(0,1fr);gap:17px;position:static;align-items:center}.feature-copy{padding:0}.feature-copy h2{font-size:17px}.feature-copy p{font-size:11px}.subject-theme-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:15px 12px}.collection-heading h3{font-size:18px}.collection-heading p{font-size:11px}.theme-status{max-width:70%;font-size:11px}.theme-card-state{font-size:10px;padding:7px 9px}.default-preview{padding:12px;gap:12px}.default-preview strong{font-size:24px}.default-preview span,.default-preview p{font-size:10px}.default-card-art{padding:12px}}
</style>
