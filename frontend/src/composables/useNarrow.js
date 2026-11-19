import {ref,onMounted,onUnmounted} from 'vue'

/**
 * 是否处于窄屏（手机）。用 matchMedia 而不是只靠 CSS，
 * 因为卡片的 DOM 结构和表格完全不同，需要按条件渲染。
 */
const query = typeof window !== 'undefined' && window.matchMedia
  ? window.matchMedia('(max-width:760px)')
  : null
const narrow = ref(query ? query.matches : false)

if (query) {
  const onChange = (e) => { narrow.value = e.matches }
  if (query.addEventListener) query.addEventListener('change', onChange)
  else if (query.addListener) query.addListener(onChange)
}

export function useNarrow() {
  // 视口在组件挂载前就可能变化（旋转、分屏），挂载时再对一次
  onMounted(() => { if (query) narrow.value = query.matches })
  return narrow
}
