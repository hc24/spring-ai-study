# spring-ai-study

两个独立进程：

- `spring-ai-study-agent`：Spring Boot Agent。先在该目录启动，默认端口 8080。
- `spring-ai-study-desktop`：Electron 对话框。通过 SSE 调用 `GET /react/stream`。
