---
title: 从这里开始
createTime: 2026/03/20
permalink: /dev/
---

# 欢迎来到 SleepIn 开发者文档！ 👋

如果你刚刚接触这个项目，可能是为了提交代码开发新特性或修复 Bug，从这里开始可以省下很多时间。

> **写在前面的话**：
> 这个文档说是写给新的开发者的，实际上其实是为了写给我自己做备忘录的。而如果我尽可能地写详细一些，未来如有人要参与这个项目，
> 肯定也会有所帮助。
> 
> 然而，事实上是，目前这个文档基本上都是 AI 生成的，什么时候有时间了再重新写过。😀

## 技术栈总览

| 领域 | 技术栈 | 说明 |
| --- | --- | --- |
| 语言 | Kotlin | Android 主开发语言 |
| UI | Jetpack Compose + Material 3 | 声明式 UI 与设计系统 |
| 架构 | MVVM + Clean Architecture | 分层解耦，便于维护与测试 |
| 本地存储 | Room | SQLite ORM，支持编译期校验 |
| 设置存储 | DataStore | 键值配置持久化 |
| 后台任务 | WorkManager | 小组件刷新与后台调度 |
| 小组件 | Glance | 桌面 Widget UI |
| 导航 | Navigation Compose | 单 Activity 下路由管理 |
| 文档站 | VuePress + Plume | 开发者/用户文档站点 |

## 项目结构总览

```text
SleepIn/
├─ app/
│  ├─ src/main/java/com/kurosu/sleepin/
│  │  ├─ MainActivity.kt                        # Activity 入口
│  │  ├─ SleepInApplication.kt                  # 应用初始化与手动 DI 根节点
│  │  ├─ data/                                  # 数据层：Room/DAO/Entity/RepositoryImpl/CSV/DataStore
│  │  ├─ domain/                                # 领域层：Model/Repository 接口/UseCase
│  │  ├─ ui/                                    # UI 层：Screen/ViewModel/Navigation/Theme
│  │  ├─ di/                                    # 手动依赖提供模块
│  │  └─ widget/                                # 桌面小组件与刷新调度
│  └─ schemas/                                  # Room schema 快照
└─ docs/                                        # 文档目录
```

## 进一步阅读开发者文档

下一步是详细了解项目的[架构](2.architecture/1.outline.md)，之后可以深入到
[各个模块的职责](2.architecture/2.module-responsibilities.md)。
