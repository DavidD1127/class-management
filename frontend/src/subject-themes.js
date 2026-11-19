export const themeStorageKey = 'class-management-subject-theme';
export const subjectThemes = [
  {
    "id": "chinese",
    "subject": "语文",
    "name": "水墨山水",
    "description": "萧云从山水册中的笔墨与留白",
    "primary": "#864c43",
    "canvas": "#f6f6f2",
    "art": "/theme-covers/chinese.webp",
    "paper": "#f3f0e7",
    "caption": "萧云从，1668年",
    "material": "馆藏水墨画",
    "source": "https://www.clevelandart.org/art/1955.302.4"
  },
  {
    "id": "math",
    "subject": "数学",
    "name": "公式与几何",
    "description": "代数恒等式、直角三角形与函数图像",
    "primary": "#355f8a",
    "canvas": "#f6f6f2",
    "art": "/theme-covers/math.svg",
    "paper": "#edf1f4",
    "caption": "代数、几何与函数",
    "material": "原创学科示意图"
  },
  {
    "id": "english",
    "subject": "英语",
    "name": "读写之间",
    "description": "字母、单词与英语学习的日常",
    "primary": "#3d586b",
    "canvas": "#f6f6f2",
    "art": "/theme-covers/english.svg",
    "paper": "#eef1ef",
    "caption": "Reading and writing",
    "material": "原创文字排版"
  },
  {
    "id": "physics",
    "subject": "物理",
    "name": "力与电",
    "description": "单摆与电路中的基本物理关系",
    "primary": "#49636d",
    "canvas": "#f6f6f2",
    "art": "/theme-covers/physics.svg",
    "paper": "#edf1ef",
    "caption": "力学与电学",
    "material": "原创学科示意图"
  },
  {
    "id": "chemistry",
    "subject": "化学",
    "name": "元素与实验",
    "description": "化学式、常见仪器与元素符号",
    "primary": "#356a68",
    "canvas": "#f6f6f2",
    "art": "/theme-covers/chemistry.svg",
    "paper": "#edf2ed",
    "caption": "物质与变化",
    "material": "原创学科示意图"
  },
  {
    "id": "biology",
    "subject": "生物",
    "name": "观察植物",
    "description": "以真实植物学版画记录生命形态",
    "primary": "#4e6b47",
    "canvas": "#f6f6f2",
    "art": "/theme-covers/biology.webp",
    "paper": "#eff1e7",
    "caption": "植物学版画，1806年",
    "material": "馆藏植物学版画",
    "source": "https://www.clevelandart.org/art/1955.142"
  },
  {
    "id": "history",
    "subject": "历史",
    "name": "器物与文明",
    "description": "从青铜鼎的实物与纹饰认识历史",
    "primary": "#766044",
    "canvas": "#f6f6f2",
    "art": "/theme-covers/history.webp",
    "paper": "#f1ede4",
    "caption": "青铜鼎，馆藏实物",
    "material": "馆藏文物摄影",
    "source": "https://www.clevelandart.org/art/2003.2"
  },
  {
    "id": "geography",
    "subject": "地理",
    "name": "经纬之间",
    "description": "经线、纬线与赤道构成的地球经纬网",
    "primary": "#386b83",
    "canvas": "#f6f6f2",
    "art": "/theme-covers/geography.svg",
    "paper": "#eaf0ef",
    "caption": "地球经纬网示意",
    "material": "原创学科示意图"
  },
  {
    "id": "politics",
    "subject": "政治",
    "name": "公民与社会",
    "description": "从权利、义务和规则理解公共生活",
    "primary": "#815058",
    "canvas": "#f6f6f2",
    "art": "/theme-covers/politics.svg",
    "paper": "#f3ece8",
    "caption": "权利、义务与责任",
    "material": "原创文字排版"
  },
  {
    "id": "sports",
    "subject": "体育",
    "name": "田径与运动",
    "description": "以田径场的真实平面结构呈现运动",
    "primary": "#90533d",
    "canvas": "#f6f6f2",
    "art": "/theme-covers/sports.svg",
    "paper": "#f3eee5",
    "caption": "田径场平面示意",
    "material": "原创学科示意图"
  },
  {
    "id": "music",
    "subject": "音乐",
    "name": "旋律与节奏",
    "description": "以高音谱表上的C大调音阶呈现音乐",
    "primary": "#62534f",
    "canvas": "#f6f6f2",
    "art": "/theme-covers/music.svg",
    "paper": "#f1eee7",
    "caption": "C大调音阶",
    "material": "原创乐谱排版"
  },
  {
    "id": "art",
    "subject": "美术",
    "name": "画中的色彩",
    "description": "从莫奈的真实画作观察笔触与色彩",
    "primary": "#596a49",
    "canvas": "#f6f6f2",
    "art": "/theme-covers/art.webp",
    "paper": "#f0efe5",
    "caption": "莫奈《睡莲》",
    "material": "馆藏绘画",
    "source": "https://www.clevelandart.org/art/1960.81"
  },
  {
    "id": "it",
    "subject": "信息技术",
    "name": "信息与编码",
    "description": "二进制、ASCII编码与网络连接",
    "primary": "#3c685d",
    "canvas": "#f6f6f2",
    "art": "/theme-covers/it.svg",
    "paper": "#edf1ec",
    "caption": "数据、编码与网络",
    "material": "原创学科示意图"
  }
];
export function applySubjectTheme(id, root = document.documentElement) {
  const theme = subjectThemes.find(item => item.id === id);
  const variables = ['--primary','--canvas','--subject-paper', ...[9,8,7,5,3].map(n=>'--el-color-primary-light-'+n),'--el-color-primary-dark-2'];
  if (!theme) { delete root.dataset.subject; variables.forEach(key=>root.style.removeProperty(key)); return 'default'; }
  root.dataset.subject = theme.id;
  root.style.setProperty('--primary', theme.primary);
  root.style.setProperty('--canvas', theme.canvas);
  root.style.setProperty('--subject-paper', theme.paper);
  [9,8,7,5,3].forEach(n=>root.style.setProperty('--el-color-primary-light-'+n,'color-mix(in srgb, '+theme.primary+', white '+n*10+'%)'));
  root.style.setProperty('--el-color-primary-dark-2', 'color-mix(in srgb, '+theme.primary+', black 20%)');
  return theme.id;
}
export function restoreSubjectTheme() {
  let saved = 'default';
  try { saved = localStorage.getItem(themeStorageKey) || saved; } catch {}
  return applySubjectTheme(saved);
}
