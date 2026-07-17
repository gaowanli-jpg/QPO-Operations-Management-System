# QPO 运行日志与数据库慢 SQL 日志开发文档

## 1. 背景

QPO 当前已经完成 **操作日志 / 审计日志** 模块，系统可以记录登录、用户管理、字典维护、RFQ 导入、核价、提单、报价模板、日志保留策略等关键业务动作。

但操作日志解决的是“谁做了什么业务动作”，不是“系统为什么报错、接口为什么慢、数据库是否有慢 SQL”。因此后续需要补齐两类运维排查能力：

- **后端文件日志**：用于排查 Java 后端异常、接口错误、后台任务失败、导出失败、日志清理失败等运行问题。
- **数据库运行日志 / 慢 SQL 日志**：用于排查 Postgres 慢查询、锁等待、死锁、连接异常等数据库问题。

本阶段不替代现有操作日志，也不把所有查询行为写入 `audit_log`。三类日志职责保持分离。

## 2. 当前状态

### 2.1 已有 Docker 日志

`docker-compose.yml` 已经为 `postgres`、`backend`、`frontend` 配置 Docker `json-file` 日志轮转：

```yaml
x-logging: &default-logging
  driver: json-file
  options:
    max-size: ${LOG_MAX_SIZE:-20m}
    max-file: "${LOG_MAX_FILE:-5}"
```

当前可通过以下命令查看：

```powershell
docker logs quote-backend
docker logs quote-postgres
docker logs quote-frontend
```

这能满足基础排查，但不适合长期按日期归档、按错误级别筛选、和业务操作日志的 `requestId` 关联。

### 2.2 已有存储方式

当前 QPO Docker 使用 named volume：

```yaml
backend:
  volumes:
    - uploads_data:/data/uploads
    - backups_data:/data/backups

postgres:
  volumes:
    - postgres_data:/var/lib/postgresql/data
    - ./backups/postgres:/backups
```

本开发阶段继续沿用 named volume。**不改成 Windows 可见 bind mount**，保持和现有归档文件、上传目录、任务输出的一致性。

## 3. 目标

### 3.1 后端文件日志目标

- 后端运行日志写入 `/data/backups/logs/backend/`。
- 普通运行日志按天滚动，文件名类似：
  - `/data/backups/logs/backend/qpo-backend.log`
  - `/data/backups/logs/backend/qpo-backend.2026-07-17.log`
- 错误日志单独写入：
  - `/data/backups/logs/backend/qpo-error.log`
  - `/data/backups/logs/backend/qpo-error.2026-07-17.log`
- 日志保留 30 天。
- 单文件大小建议限制 50MB。
- 日志中包含：
  - 时间
  - level
  - thread
  - logger
  - requestId
  - message
  - exception stacktrace
- 能通过 `requestId` 和操作日志页面中的请求信息互相定位。

### 3.2 数据库运行日志目标

- 不开启全量 SQL 日志。
- 仅开启轻量运维日志：
  - 慢 SQL，例如超过 500ms 或 1000ms。
  - 锁等待。
  - 死锁。
  - checkpoint / vacuum 等关键数据库运行事件可后置，不在第一版强制开启。
- Postgres 日志仍由 Docker `json-file` 捕获并轮转。
- 如后续需要数据库文件日志，再单独评估 `logging_collector`，不要在本阶段默认开启。

## 4. 不做范围

- 不新增前端错误上报平台。
- 不接入 ELK、Loki、Prometheus、Grafana、Sentry。
- 不把后端运行日志写入 `audit_log`。
- 不记录所有 SQL。
- 不把 Docker named volume 改成 Windows bind mount。
- 不在页面中提供运行日志下载或查看功能；第一版以运维命令查看为主。

## 5. 存储与查看方式

### 5.1 后端文件日志位置

容器内路径：

```text
/data/backups/logs/backend/
```

对应 Docker volume：

```text
quote-system_backups_data
```

查看目录：

```powershell
docker exec quote-backend sh -lc "ls -lh /data/backups/logs/backend"
```

查看最新后端日志：

```powershell
docker exec quote-backend sh -lc "tail -n 200 /data/backups/logs/backend/qpo-backend.log"
```

查看最新错误日志：

```powershell
docker exec quote-backend sh -lc "tail -n 200 /data/backups/logs/backend/qpo-error.log"
```

复制日志到当前目录：

```powershell
docker cp quote-backend:/data/backups/logs/backend/qpo-error.log .
```

### 5.2 数据库日志查看方式

第一版数据库运行日志继续走 Docker 日志：

```powershell
docker logs quote-postgres
```

筛选慢 SQL：

```powershell
docker logs quote-postgres 2>&1 | Select-String "duration:"
```

筛选锁等待：

```powershell
docker logs quote-postgres 2>&1 | Select-String "lock"
```

## 6. 后端实现设计

### 6.1 新增 logback 配置

新增文件：

```text
quote-system/src/main/resources/logback-spring.xml
```

建议配置：

- `CONSOLE`：继续输出到控制台，保持 `docker logs quote-backend` 可用。
- `ROLLING_FILE`：输出所有 INFO 及以上日志到 `/data/backups/logs/backend/qpo-backend.log`。
- `ERROR_FILE`：只输出 ERROR 级别日志到 `/data/backups/logs/backend/qpo-error.log`。

建议通过环境变量控制：

```yaml
LOG_FILE_DIR: ${LOG_FILE_DIR:/data/backups/logs/backend}
LOG_FILE_MAX_SIZE: ${LOG_FILE_MAX_SIZE:50MB}
LOG_FILE_MAX_HISTORY: ${LOG_FILE_MAX_HISTORY:30}
LOG_LEVEL_ROOT: ${LOG_LEVEL_ROOT:INFO}
LOG_LEVEL_APP: ${LOG_LEVEL_APP:INFO}
```

### 6.2 requestId 进入日志 MDC

当前后端已有 `RequestTraceFilter` 和 `RequestTraceContext`，操作日志已经可以记录 `requestId`。

后续需要让 `RequestTraceFilter` 同时写入 SLF4J MDC：

```java
MDC.put("requestId", trace.requestId());
```

请求结束后清理：

```java
MDC.remove("requestId");
```

这样后端文件日志和操作日志可以通过同一个 `requestId` 对齐。

### 6.3 异常日志策略

检查 `GlobalExceptionHandler`：

- 业务异常可以返回友好消息，但后端文件日志需要记录必要上下文。
- 未预期异常必须 `log.error(...)`，包含 stacktrace。
- 不在日志中打印密码、token、Authorization header、上传文件内容。

建议规则：

- `RuntimeException` 且属于业务可预期错误：`WARN`
- 未捕获异常、系统错误、文件写入失败、数据库异常：`ERROR`
- 定时任务失败：`ERROR`
- 手动操作失败但用户可修正：`WARN`

## 7. 数据库日志实现设计

### 7.1 docker-compose 配置

在 `postgres` service 中增加轻量 Postgres 参数，建议先通过 `command` 配置：

```yaml
postgres:
  command:
    - "postgres"
    - "-c"
    - "log_min_duration_statement=${POSTGRES_LOG_MIN_DURATION_STATEMENT_MS:-500}"
    - "-c"
    - "log_lock_waits=on"
    - "-c"
    - "deadlock_timeout=${POSTGRES_DEADLOCK_TIMEOUT:-1s}"
    - "-c"
    - "log_timezone=Asia/Shanghai"
```

说明：

- `log_min_duration_statement=500` 表示记录超过 500ms 的 SQL。
- `log_lock_waits=on` 表示记录锁等待。
- `deadlock_timeout=1s` 表示锁等待超过 1 秒后开始检测并记录。
- 不开启 `log_statement=all`。

### 7.2 环境变量

在 `erp.env` 中补充：

```env
POSTGRES_LOG_MIN_DURATION_STATEMENT_MS=500
POSTGRES_DEADLOCK_TIMEOUT=1s
```

如果后续发现日志太多，可以把慢 SQL 阈值调高到：

```env
POSTGRES_LOG_MIN_DURATION_STATEMENT_MS=1000
```

## 8. 分阶段实施建议

### 阶段 6.1：后端文件日志

目标：先让 Java 后端具备按天滚动的文件日志。

文件：

- 新增 `quote-system/src/main/resources/logback-spring.xml`
- 修改 `quote-system/src/main/java/com/machining/quote/config/RequestTraceFilter.java`
- 修改或检查 `quote-system/src/main/java/com/machining/quote/config/GlobalExceptionHandler.java`
- 修改 `docker-compose.yml`
- 修改 `erp.env`

验收：

- `docker compose --env-file erp.env up -d --build backend`
- 容器内出现 `/data/backups/logs/backend/qpo-backend.log`
- 触发一个接口请求后，日志包含 requestId。
- 触发一个可控异常后，`qpo-error.log` 有错误记录。
- `docker logs quote-backend` 仍然可用。

### 阶段 6.2：数据库慢 SQL 与锁等待日志

目标：轻量打开 Postgres 慢 SQL 和锁等待日志。

文件：

- 修改 `docker-compose.yml`
- 修改 `erp.env`
- 更新本开发文档或新增运维说明。

验收：

- `docker compose --env-file erp.env up -d --build postgres backend`
- `docker logs quote-postgres` 能看到 PostgreSQL 正常启动日志。
- 人工执行一个慢 SQL 后，`docker logs quote-postgres` 中出现 `duration:`。
- 不出现大量每条 SQL 都被记录的情况。

### 阶段 6.3：运维说明补齐

目标：让后续维护时能快速定位日志。

文件：

- 更新 `docs/QPO日志模块/01-操作日志模块开发文档.md`
- 或新增 `docs/QPO日志模块/03-日志运维说明.md`

说明内容：

- 操作日志：系统页面查看。
- 后端运行日志：`/data/backups/logs/backend/`。
- Postgres 慢 SQL：`docker logs quote-postgres`。
- Docker 容器日志：`docker logs quote-backend`、`docker logs quote-postgres`。
- 日志保留策略：
  - Docker json-file：`LOG_MAX_SIZE * LOG_MAX_FILE`
  - 后端文件日志：30 天
  - 操作日志：页面保留策略配置

## 9. 测试计划

### 9.1 后端测试

建议新增或扩展：

```text
quote-system/src/test/java/com/machining/quote/RequestTraceFilterTests.java
quote-system/src/test/java/com/machining/quote/GlobalExceptionHandlerTests.java
```

测试重点：

- 请求进入时 MDC 包含 requestId。
- 请求结束后 MDC 被清理。
- 未预期异常由 `GlobalExceptionHandler` 记录 ERROR。
- 敏感字段不进入日志 message。

### 9.2 配置验证

建议增加轻量配置检查测试或脚本：

```powershell
docker compose --env-file erp.env config
```

检查：

- backend 有 `LOG_FILE_DIR=/data/backups/logs/backend`
- backend 仍挂载 `backups_data:/data/backups`
- postgres 有慢 SQL 参数。
- Docker logging 仍有 `max-size` 和 `max-file`。

## 10. 验收清单

### 10.1 后端文件日志

- [ ] 后端容器启动后自动创建 `/data/backups/logs/backend/`。
- [ ] `qpo-backend.log` 能记录 INFO/WARN/ERROR。
- [ ] `qpo-error.log` 只记录 ERROR。
- [ ] 文件按日期滚动。
- [ ] 文件保留 30 天。
- [ ] 单文件大小有上限。
- [ ] 日志包含 requestId。
- [ ] `docker logs quote-backend` 仍可用。

### 10.2 数据库运行日志

- [ ] 不开启全量 SQL 日志。
- [ ] 慢 SQL 会出现在 `docker logs quote-postgres`。
- [ ] 锁等待会出现在 `docker logs quote-postgres`。
- [ ] Docker 日志轮转仍然生效。

### 10.3 安全与空间

- [ ] 日志不记录明文密码。
- [ ] 日志不记录 JWT token。
- [ ] 日志不记录 Authorization header。
- [ ] 日志不记录上传文件内容。
- [ ] 文件日志不会无限增长。
- [ ] 数据库慢 SQL 日志不会无限增长。

## 11. 推荐结论

建议后续按以下顺序推进：

1. 先做后端文件日志，因为它对日常排查价值最高。
2. 再做 Postgres 慢 SQL / 锁等待日志，避免数据库问题只能靠猜。
3. 最后补运维说明，统一说明操作日志、文件日志、数据库日志分别在哪里看、保留多久、怎么清理。

本阶段继续沿用 Docker named volume，归档和运行日志仍在容器路径与 Docker volume 中，不改成 Windows 目录直挂。
