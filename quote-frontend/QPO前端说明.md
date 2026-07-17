# QPO前端说明

## 项目用途

`quote-frontend` 是QPO前端页面，包含市场部订单处理、工程部工艺与核价、仓库、报价、系统管理等业务界面。

## 技术栈

- Vue 3
- Vue Router
- Element Plus
- Vite

## 本地开发

```powershell
cd D:\Code\quote-frontend
npm install
npm run dev
```

Vite默认会使用本地开发端口。后端API地址由 `VITE_API_BASE_URL` 控制，可参考 `quote-frontend\.env.example`。

## 构建

```powershell
cd D:\Code\quote-frontend
npm run build
```

## Docker部署

前端Docker镜像由 `D:\Code\quote-frontend\Dockerfile` 构建。生产环境由Nginx提供静态页面，并通过 QPO 的 Docker Compose 统一启动。

## 参考链接

- [Vue文档](https://vuejs.org/)
- [Vite文档](https://vite.dev/)
- [Element Plus文档](https://element-plus.org/)
