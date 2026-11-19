import assert from 'node:assert/strict';
import {existsSync,readFileSync} from 'node:fs';
import {subjectThemes,applySubjectTheme,restoreSubjectTheme,themeStorageKey} from './subject-themes.js';
function luminance(hex){const rgb=hex.slice(1).match(/../g).map(value=>parseInt(value,16)/255).map(value=>value<=.04045?value/12.92:((value+.055)/1.055)**2.4);return rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722;}
const values=new Map(),root={dataset:{},style:{setProperty:(key,value)=>values.set(key,value),removeProperty:key=>values.delete(key)}};
assert.equal(subjectThemes.length,13);
assert.equal(new Set(subjectThemes.map(t=>t.id)).size,13);
assert.deepEqual(subjectThemes.map(t=>t.subject),['语文','数学','英语','物理','化学','生物','历史','地理','政治','体育','音乐','美术','信息技术']);
for(const theme of subjectThemes){
  assert.equal(applySubjectTheme(theme.id,root),theme.id);
  assert.equal(root.dataset.subject,theme.id);
  assert.equal(values.get('--primary'),theme.primary);
  assert.ok(1.05/(luminance(theme.primary)+.05)>=4.5,theme.subject+'主按钮对比度');
  assert.ok((luminance(theme.paper)+.05)/(luminance('#26394a')+.05)>=7,theme.subject+'纸面正文对比度');
  assert.equal(values.get('--subject-paper'),theme.paper);
  assert.match(theme.art,/^\/theme-covers\/[a-z]+\.(webp|svg)$/);
  assert.ok(existsSync(new URL('../public'+theme.art,import.meta.url)),theme.subject+'素材存在');
  if(theme.art.endsWith('.webp'))assert.ok(theme.source?.startsWith('https://www.clevelandart.org/art/'));
  assert.ok(values.get('--el-color-primary-light-9').includes(theme.primary));
}
assert.equal(applySubjectTheme('unknown',root),'default');
assert.equal(root.dataset.subject,undefined);
assert.equal(values.size,0);
globalThis.document={documentElement:root};
globalThis.localStorage={getItem:key=>key===themeStorageKey?'music':null};
assert.equal(restoreSubjectTheme(),'music');
globalThis.localStorage={getItem:()=>{throw new Error('blocked')}};
assert.equal(restoreSubjectTheme(),'default');
assert.equal(values.size,0);
const math=readFileSync(new URL('../public/theme-covers/math.svg',import.meta.url),'utf8');
assert.ok(math.includes('a² + b² = c²') && math.includes('y = x²'));
const music=readFileSync(new URL('../public/theme-covers/music.svg',import.meta.url),'utf8');
assert.ok(music.includes('C 大调音阶') && music.includes('𝄞'));
console.log('通过：13学科、素材存在、配色与文字对比度、公式与乐谱、默认恢复、存储回退');
