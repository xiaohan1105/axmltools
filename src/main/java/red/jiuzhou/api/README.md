# API 层

本包包含应用程序的 API 层，提供 REST 端点和通用 API 工具。

## 控制器
- `TabController.java` - 表相关的 API 端点
- `FileController.java` - 文件操作 API 端点

## 通用 API 组件
- `CommonResult.java` - 通用 API 响应格式
- `GlobalErrorCodeConstants.java` - 全局错误代码
- `ErrorCode.java` - 错误代码定义
- `ServiceException.java` - 服务异常处理

## 关键功能

- RESTful API 端点
- 标准化响应格式
- 集中式错误处理
- 文件操作 API
- 表数据管理 API

## 用途

这些组件为应用程序提供后端 API 功能，处理 HTTP 请求并返回带有适当错误处理的结构化响应。