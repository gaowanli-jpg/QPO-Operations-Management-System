# QPO服务器备份说明

## 适用范围

本文只描述 QPO 线上/内网服务器的备份方式。
备份目标是保住这些数据：

- PostgreSQL 数据库
- 已上传图纸和附件
- 当前部署使用的 `erp.env`、`docker-compose.yml` 等配置
- 备份摘要和校验信息

不会做这些事：

- 不删除 Docker volume
- 不清空数据库
- 不覆盖业务数据

## 主流做法

QPO 的备份建议按这个组合来做：

1. 用一个脚本完成一次备份
2. 用 `cron` 或 `systemd timer` 定时触发
3. 用“保留多少天 + 保留多少份”管理历史备份
4. 定期做一次恢复演练，确认备份真能恢复

这就是你说的那种模式：
“写一个脚本，然后定时跑；可以设定每隔多少天备份一次；同时保留最近几份，最老的自动删掉。”

## 备份脚本

脚本路径：

- `scripts/backup-qpo.sh`

它会生成一个时间戳目录，例如：

```text
/opt/qpo/backups/qpo-backup-20260819_020000/
```

目录结构大致是：

```text
db/postgres.sql.gz
uploads/
config/erp.env
config/docker-compose.yml
config/qpo-image.env
meta/backup.json
meta/sha256sums.txt
.completed
```

## 手动备份

### 默认方式

```bash
cd /opt/qpo/qpo-offline-package-20260818
sh scripts/backup-qpo.sh
```

### 指定备份目录

```bash
sh scripts/backup-qpo.sh \
  --project-dir /opt/qpo/qpo-offline-package-20260818 \
  --backup-root /opt/qpo/backups
```

### 保留策略

```bash
sh scripts/backup-qpo.sh \
  --project-dir /opt/qpo/qpo-offline-package-20260818 \
  --backup-root /opt/qpo/backups \
  --keep-days 30 \
  --keep-count 30
```

含义：

- `--keep-days 30`：删除 30 天前的完整备份
- `--keep-count 30`：只保留最近 30 份完整备份
- 两个条件可以一起用

如果你想只按“份数”保留，可以把 `--keep-days` 设成 `0`。
如果你想只按“天数”保留，可以把 `--keep-count` 设成 `0`。

## 定时备份

最常见的是 `cron`。

先确保备份目录存在：

```bash
mkdir -p /opt/qpo/backups/logs
```

然后编辑定时任务：

```bash
crontab -e
```

比如每天凌晨 2 点备份一次：

```cron
0 2 * * * cd /opt/qpo/qpo-offline-package-20260818 && sh scripts/backup-qpo.sh --backup-root /opt/qpo/backups --keep-days 30 --keep-count 30 >> /opt/qpo/backups/logs/scheduled-backup.log 2>&1
```

## 备份保留策略建议

推荐默认：

- 每天备份 1 次
- 保留最近 30 天
- 同时最多保留 30 份

这样既方便回滚，又不会把磁盘很快占满。

如果你们更新很频繁，可以改成：

- 每天备份 1 次
- 保留最近 14 天
- 最多保留 14 份或 30 份

## 使用场景

### 1. 升级前备份

每次替换新版本前，先跑一次手动备份。

```bash
sh scripts/backup-qpo.sh --backup-root /opt/qpo/backups
```

### 2. 每日自动备份

交给 `cron`，凌晨跑一次。

### 3. 回滚前备份

如果你准备手工恢复某个旧版本，先把当前状态再备份一份。

### 4. 恢复演练

不要只存备份，不验证恢复。
至少偶尔在测试环境做一次恢复演练，确认 `db/postgres.sql.gz` 真的能导回去。

对应的 Linux 恢复脚本和详细说明：

- `scripts/restore-qpo.sh`
- `QPO服务器恢复说明.md`

## 恢复时要注意什么

恢复时请记住：

- 先确认目标环境可以被覆盖
- 先恢复数据库，再恢复文件
- 不要执行 `docker compose down -v`
- 不要删除 `quote-system_postgres_data`、`quote-system_uploads_data`、`quote-system_backups_data`
