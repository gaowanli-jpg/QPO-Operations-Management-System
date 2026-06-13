# APS上线验收清单

## 部署检查

- [x] `aps.env` 已配置强随机 `APS_POSTGRES_PASSWORD`
- [x] `aps.env` 已配置强随机 `APS_JWT_SECRET`
- [x] `APS_ERP_AUTH_TOKEN` 与 ERP `ERP_APS_SOURCE_TOKEN` 一致
- [ ] `APS_ERP_BASE_URL` 指向生产 ERP 内网后端地址；当前本机验收值为 `http://host.docker.internal:8080`
- [ ] `APS_CORS_ALLOWED_ORIGINS` 指向生产 APS HTTPS 地址；当前本机验收值为 `http://localhost:8095,http://127.0.0.1:8095`
- [x] 生产环境使用新库卷名，例如 `APS_POSTGRES_VOLUME_NAME=aps_postgres_data_prod`
- [x] Docker Compose 只暴露 APS 前端端口给宿主机
- [ ] 外层 HTTPS/Nginx 代理到 `http://127.0.0.1:8095`；由正式部署人员处理

## 容器检查

- [x] `docker compose --env-file aps.env -f docker-compose.aps.yml ps` 显示三个容器 healthy
- [x] `http://127.0.0.1:8095/` 可访问
- [x] `http://127.0.0.1:8095/api/health` 返回 `UP`
- [x] 重启容器后页面和数据正常

## 账号与权限

- [x] 管理员可登录
- [x] 默认密码已修改
- [x] 用户权限模块可新增管理员
- [x] 用户权限模块可停用、重置密码、删除非初始管理员

## ERP同步与业务流程

- [x] ERP到APS同步任务可执行；当前新库已有 APS 任务 30 条
- [x] 工艺从ERP同步到APS可执行；当前新库已有工艺 27 条
- [x] PMC任务列表可查看
- [x] PMC任务可分配到部门
- [x] 已分配任务列表可查看
- [x] 部门中心可查看任务明细

## 库存与管理页面

- [x] 零件库存页面可查询、新增、调整
- [ ] 零件锁定记录可查询；当前新库没有匹配库存生成的锁定记录，因此未实际验证确认、释放
- [x] 生产需求页面可查询
- [x] 材料库存页面可查询、新增、删除
- [ ] 材料锁定页面可查询；当前新库没有匹配库存生成的锁定记录，因此未实际验证确认、释放
- [x] 材料缺口页面可查询
- [x] 部门管理页面可维护
- [x] 工艺管理页面可同步和查看
- [x] 机台能力页面可维护

## 备份与恢复

- [x] `scripts\backup-aps.ps1` 可生成备份；最终备份为 `D:\Code\backups\aps\20260612_234217`
- [x] 备份文件不为空；最终备份 `db\aps.sql` 约 197 KB
- [x] `scripts\restore-drill-aps.ps1` 恢复演练通过
- [x] `scripts\install-aps-backup-task.ps1` 已安装定时备份；任务名 `ApsSystemDailyBackup`
- [x] 定时备份日志可查看；日志文件 `D:\Code\backups\aps\logs\scheduled-aps-backup.log`
