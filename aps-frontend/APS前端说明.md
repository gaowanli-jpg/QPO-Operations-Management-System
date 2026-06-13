# APS前端说明

## 项目用途

`aps-frontend` 是APS前端页面，包含登录、用户权限、ERP同步、PMC任务分配、部门任务中心、库存、工艺和机台能力等页面。

## 技术栈

- Vue 3
- Vue Router
- Element Plus
- Vite

## 本地开发

```powershell
cd D:\Code\aps-frontend
npm install
npm run dev
```

默认开发地址是 `http://127.0.0.1:5175`。

## 构建

```powershell
cd D:\Code\aps-frontend
npm run build
```

## Docker部署

前端Docker镜像由 `D:\Code\aps-frontend\Dockerfile` 构建，生产环境由Nginx提供静态页面，并把 `/api` 转发到APS后端容器。

生产构建时 `VITE_API_BASE_URL` 可以设为空字符串，这样前端请求会走同域 `/api` 反向代理。

## 参考链接

- [Vue文档](https://vuejs.org/)
- [Vite文档](https://vite.dev/)
- [Element Plus文档](https://element-plus.org/)
