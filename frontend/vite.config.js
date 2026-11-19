import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig(({ mode }) => {
  const { API_PROXY_TARGET } = loadEnv(mode, process.cwd(), 'API_');
  return {
    plugins: [vue()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: process.env.API_PROXY_TARGET || API_PROXY_TARGET || 'http://127.0.0.1:8092',
          changeOrigin: false,
        },
      },
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) return;
            return id.includes('/element-plus/') ? 'element-plus' : 'vendor';
          },
        },
      },
    },
  };
});
