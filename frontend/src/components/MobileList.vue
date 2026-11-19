<template>
  <div class="m-list">
    <div v-if="!items.length" class="m-empty">{{empty||'暂无数据'}}</div>
    <article v-for="(row,index) in items" :key="row.id??index" class="m-card">
      <header class="m-head">
        <div class="m-title">{{primary(row)}}</div>
        <div v-if="secondary(row)" class="m-sub">{{secondary(row)}}</div>
        <span v-if="badge(row)" class="m-badge">{{badge(row)}}</span>
      </header>
      <dl class="m-fields">
        <template v-for="f in fields" :key="f.k">
          <div v-if="value(row,f)" class="m-row">
            <dt>{{f.label}}</dt>
            <dd>{{value(row,f)}}</dd>
          </div>
        </template>
      </dl>
      <footer v-if="$slots.actions" class="m-actions">
        <slot name="actions" :row="row" :index="index" />
      </footer>
    </article>
  </div>
</template>

<script setup>
const props=defineProps({
  items:{type:Array,default:()=>[]},
  // [{k:'className',label:'班级',format:(row)=>string}]
  fields:{type:Array,default:()=>[]},
  primary:{type:Function,default:(row)=>row.name||row.id||''},
  secondary:{type:Function,default:()=>''},
  badge:{type:Function,default:()=>''},
  empty:{type:String,default:''}
});

function value(row,field){
  const raw=field.format?field.format(row):row[field.k];
  if(raw===null||raw===undefined||raw==='')return '';
  return String(raw);
}
</script>

<style scoped>
.m-list{display:grid;gap:10px}
.m-card{background:#fff;border:1px solid var(--border);border-radius:12px;padding:12px 14px}
.m-head{display:flex;align-items:baseline;gap:8px;flex-wrap:wrap;margin-bottom:8px}
.m-title{font-weight:600;font-size:15px;color:var(--ink)}
.m-sub{color:var(--muted);font-size:12px}
.m-badge{margin-left:auto;font-size:12px;color:var(--primary);background:#eef3fb;border-radius:999px;padding:2px 8px}
.m-fields{margin:0;display:grid;gap:6px}
.m-row{display:flex;justify-content:space-between;gap:12px;font-size:13px}
.m-row dt{color:var(--muted);flex:0 0 auto}
.m-row dd{margin:0;text-align:right;color:var(--ink);word-break:break-word}
.m-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:10px;padding-top:10px;border-top:1px solid var(--border)}
.m-empty{padding:28px 0;text-align:center;color:var(--muted);font-size:13px;background:#fff;border:1px solid var(--border);border-radius:12px}
</style>
