# RAG 知识库管理平台 · Vue 3 前端

配套后端：`../spring-ai-qdrant-rag`（Spring Boot 3.5.7 + Spring AI 1.1.0 + Qdrant）。
本前端只接入后端**已经存在**的接口，其余能力在页面上明确标记「待开发」，不使用伪造业务数据。

## 命令

```bash
cd frontend
npm install          # 安装依赖
npm run dev          # 开发服务器 http://127.0.0.1:5173
npm run build        # 类型检查(vue-tsc -b) + 生产构建，产物在 dist/
npm run build:only   # 只构建，跳过类型检查
npm run preview      # 预览 dist 产物
npm run type-check   # 单独跑 vue-tsc
```

后端不在 8080 时，改 `.env.development` 里的 `VITE_PROXY_TARGET`（开发期由 Vite 代理 `/api`、`/actuator`，避免后端未开 CORS 的跨域问题）。

## 已接入的真实接口（4 个）

| 接口 | 用途 | 使用页面 |
| --- | --- | --- |
| `POST /api/vector/add` | 写入单个向量片段 | 文档管理 |
| `POST /api/vector/search` | 相似度检索（租户过滤） | 切片管理、AI 对话（仅检索）、RAG 评估、Dashboard 自检 |
| `GET /actuator/health` | 服务状态与耗时 | Dashboard、顶部状态灯 |
| `GET /actuator/info` | 构建信息 | Dashboard |

## 页面

`/dashboard` 首页概览 · `/chat` AI 对话 · `/knowledge` 知识库管理 · `/document` 文档管理 ·
`/document/chunks` 切片管理 · `/evaluation` RAG 评估 · `/system/users` 用户管理 · `/system/tenants` 租户管理

## 待开发接口（20 个）

清单集中在 `src/api/registry.ts`，页面顶部的「待开发」标签和 Dashboard 的接口表都由它驱动。
后端补接口时：改 `registry.ts` 的 `status`，再把对应页面里 `PendingNotice` 换成真实调用。

对话生成 `POST /api/chat/stream`（SSE）、会话历史、知识库 CRUD、文档上传与解析状态、按文档遍历切片、
评估任务与历史、统计概览、租户 CRUD、用户 CRUD。

## 目录

```
src/api        请求封装 + 接口清单（registry.ts 是唯一事实来源）
src/components 通用组件（状态灯、待开发提示、Markdown、检索结果列表等）
src/layouts    DefaultLayout：侧边栏 + 顶部栏 + 内容区
src/router     hash 路由，meta.status 标记页面接入程度
src/stores     app / tenant / chat / system（Pinia）
src/types      后端 DTO 与预留契约的类型定义
src/utils      SSE 读取、格式化
src/views      8 个业务页面 + 404
```
