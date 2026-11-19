import axios from 'axios';

/**
 * 同一份前端要能跑两种环境：
 * - 浏览器里走正常 HTTP（开发/桌面版）
 * - 装进安卓 APK 后没有后端，改走 CMNative 桥（本地 SQLite）
 * 桥的签名是 call(method, url, bodyJson, token)，返回 {status, body}。
 */
const bridge = typeof window !== 'undefined' ? window.CMNative : null;

function readFileAsBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const text = String(reader.result || '');
      resolve(text.slice(text.indexOf(',') + 1));
    };
    reader.onerror = () => reject(new Error('读取文件失败'));
    reader.readAsDataURL(file);
  });
}

function tokenOf(config) {
  const raw = config.headers?.Authorization || localStorage.token || '';
  return String(raw).replace(/^Bearer\s+/i, '');
}

function queryOf(config) {
  const params = config.params || {};
  const pairs = [];
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue;
    pairs.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
  }
  return pairs.length ? '?' + pairs.join('&') : '';
}

async function bridgeBody(config) {
  const data = config.data;
  if (data instanceof FormData) {
    const file = data.get('file');
    if (!file) return '{}';
    const base64 = await readFileAsBase64(file);
    return JSON.stringify({ fileBase64: base64, fileName: file.name });
  }
  if (data === undefined || data === null) return '{}';
  if (typeof data === 'string') return data;
  return JSON.stringify(data);
}

if (bridge) {
  axios.defaults.adapter = async (config) => {
    const method = String(config.method || 'get').toUpperCase();
    const url = (config.baseURL || '') + (config.url || '') + queryOf(config);
    const body = await bridgeBody(config);
    const raw = bridge.call(method, url, body, tokenOf(config));
    const payload = JSON.parse(raw);
    const data = payload.body;

    // 模板下载：桥不走 HTTP 文件流，直接把文件写进系统下载目录
    if (data?.code === 0 && data.data?.base64) {
      const saved = bridge.saveFile(data.data.fileName, data.data.base64, data.data.mime || 'application/octet-stream');
      bridge.toast(saved ? '已保存到「下载」目录：' + data.data.fileName : '保存失败，请检查存储权限');
      return { data: { ...data, data: new Blob([atob(data.data.base64)]) }, status: 200, statusText: 'OK', headers: {}, config };
    }

    if (payload.status !== 200) {
      const error = new Error(data?.message || '请求失败');
      error.response = { status: payload.status, data };
      throw error;
    }
    return { data, status: 200, statusText: 'OK', headers: {}, config };
  };
}

const api = axios.create({ baseURL: '/api', timeout: 60000 });
api.interceptors.request.use((c) => {
  if (localStorage.token) c.headers.Authorization = `Bearer ${localStorage.token}`;
  return c;
});
api.interceptors.response.use(
  (r) => {
    if (r.data?.code && r.data.code !== 0) throw new Error(r.data.message);
    return r;
  },
  (e) => {
    if (!e.response) e.message = e.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络连接失败，请检查连接后重试';
    if (e.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      if (location.hash !== '#/login') location.assign('/login');
    }
    return Promise.reject(e);
  }
);
export default api;
