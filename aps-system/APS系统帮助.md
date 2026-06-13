# APS后端系统说明

## 项目用途

`aps-system` 是APS后端服务，负责ERP交付任务同步、PMC派工、部门任务中心、零件库存、材料库存、工艺和机台能力等接口。

## 技术栈

- Spring Boot 3.5
- PostgreSQL
- Flyway
- MyBatis-Plus
- Spring Validation
- 自定义JWT登录鉴权

## 本地运行

```powershell
cd D:\Code\aps-system
.\mvnw.cmd spring-boot:run
```

默认后端端口是 `8091`。本地开发配置可参考 `aps-system\.env.example`。

## 测试

```powershell
cd D:\Code\aps-system
.\mvnw.cmd test
```

## Docker部署

APS的Docker编排文件在 `D:\Code\docker-compose.aps.yml`，运行配置在 `D:\Code\aps.env`。

```powershell
cd D:\Code
docker compose --env-file aps.env -f docker-compose.aps.yml up -d --build
```

## 相关文档

- [Maven官方文档](https://maven.apache.org/guides/index.html)
- [Spring Boot Maven插件](https://docs.spring.io/spring-boot/docs/current/maven-plugin/reference/htmlsingle/)
- [Flyway迁移说明](https://docs.spring.io/spring-boot/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)
- [MyBatis Spring Boot说明](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)
