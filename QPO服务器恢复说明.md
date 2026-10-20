# QPO服务器恢复说明

## 适用范围

本文说明如何在 Linux + Docker 服务器上恢复 QPO 备份。

恢复脚本只恢复：

- PostgreSQL 数据库
- QPO 本地上传文件，或备份中记录的 MinIO 文件

默认保留当前服务器上的：

- `erp.env`
- 当前 Docker Compose 配置
- 当前镜像版本
- Docker volume 名称

恢复不会自动用备份里的 `config/erp.env` 覆盖当前配置。备份里的配置副本仅用于核对和排查。

## 重要提醒

恢复会覆盖目标数据库和文件存储，属于破坏性操作。

执行恢复前必须：

1. 确认恢复目录正确
2. 确认当前服务器确实需要被旧备份覆盖
3. 先对当前状态再做一份新备份
4. 确认当前 `erp.env` 中的数据库用户、数据库名和密码仍然可用

恢复脚本要求显式添加 `--force`，没有这个参数不会执行恢复。

脚本不会执行以下危险命令：

```bash
docker compose down -v
docker volume rm ...
docker system prune --volumes
```

## 恢复文件位置

备份目录通常类似：

```text
/opt/qpo/backups/qpo-backup-20260819_020000/
```

必须包含：

```text
db/postgres.sql.gz
meta/backup.json
meta/sha256sums.txt
.completed
```

本地文件存储备份还应包含：

```text
uploads/
```

## 标准恢复流程

### 1. 进入当前离线包目录

```bash
cd /opt/qpo/qpo-offline-package-20260818
```

### 2. 先备份当前状态

```bash
sh scripts/backup-qpo.sh \
  --project-dir /opt/qpo/qpo-offline-package-20260818 \
  --backup-root /opt/qpo/backups \
  --keep-days 30 \
  --keep-count 30
```

### 3. 执行恢复

```bash
sh scripts/restore-qpo.sh \
  --project-dir /opt/qpo/qpo-offline-package-20260818 \
  --backup-dir /opt/qpo/backups/qpo-backup-20260819_020000 \
  --force
```

脚本会依次执行：

1. 校验备份文件的 SHA-256
2. 停止后端和前端容器
3. 启动 PostgreSQL
4. 清空目标数据库的 `public` schema
5. 导入 `postgres.sql.gz`
6. 启动后端和前端
7. 清空并恢复上传文件
8. 输出 Compose 服务状态

## 使用当前目录中的备份

如果备份目录就在当前项目目录下：

```bash
sh scripts/restore-qpo.sh \
  --backup-dir ./backups/qpo-backup-20260819_020000 \
  --force
```

## 恢复后检查

```bash
docker compose --env-file erp.env ps
curl -i http://127.0.0.1:8080/api/health
curl -I http://127.0.0.1:8088/
```

然后用浏览器访问：

```text
http://服务器IP:8088
```

重点确认：

- 后端和前端为 `Up` 或 `healthy`
- 登录账号仍然存在
- 历史询价订单、销售订单可以查询
- 图纸和上传附件可以打开
- 数据库迁移没有报错

## 恢复后的配置处理

恢复脚本保留当前 `erp.env`。如果旧备份对应的服务器配置也需要查看，可以检查：

```bash
less /opt/qpo/backups/qpo-backup-20260819_020000/config/erp.env
```

不要直接把备份配置覆盖到线上。需要迁移配置时，先比较差异，再由管理员手工修改当前 `erp.env`，修改后重新创建后端容器：

```bash
export QPO_IMAGE_TAG="$(grep '^QPO_IMAGE_TAG=' qpo-image.env | cut -d= -f2-)"
docker compose --env-file erp.env up -d --no-build --force-recreate backend
```

## 恢复失败时

如果数据库导入失败：

1. 不要删除 Docker volume
2. 查看后端和 PostgreSQL 日志
3. 使用刚才恢复前生成的“当前状态备份”重新恢复

```bash
docker compose --env-file erp.env logs --tail=200 postgres backend
```

如果校验失败，说明备份文件可能不完整或被修改。不要跳过校验，改用另一份完整备份。

## 恢复演练建议

正式环境不要频繁做破坏性恢复测试。建议：

- 定期把备份复制到测试服务器
- 使用同一个恢复脚本执行恢复
- 验证登录、订单、图纸和附件
- 记录演练时间、备份目录和结果

恢复演练通过后，才能确认备份不只是“文件存在”，而是真的可以使用。
