import { defineCollection, defineCollections } from 'vuepress-theme-plume'

const zhDevDoc = defineCollection({
  type: 'doc',
  dir: 'dev',
  linkPrefix: '/dev',
  title: '开发者文档',
  sidebar: [
    {
      text: '1. 新手起步',
      items: [
        { text: '开发者学习路线 (Start Here)', link: '/dev/README.md' },
        { text: '环境搭建与本地构建', link: '/dev/build-debug-windows.md' }
      ]
    },
    {
      text: '2. 整体架构 (Architecture)',
      items: [
        { text: '架构总览', link: '/dev/architecture/README.md' },
        { text: '模块隔离与职责边界', link: '/dev/module-responsibilities.md' }
      ]
    },
    {
      text: '3. 核心分层剖析',
      items: [
        { text: 'Data 层：实体与 Room', link: '/dev/data-model-room.md' },
        { text: 'Domain 层：UseCase 与逻辑', link: '/dev/business/usecase-chain.md' },
        { text: 'UI 层：状态流(StateFlow)与纯 UI', link: '/dev/ui-viewmodel-compose.md' }
      ]
    },
    {
      text: '4. 专项功能实现',
      items: [
        { text: '文件 IO：CSV 导入流程', link: '/dev/business/csv-integration.md' },
        { text: '小组件：Glance 与刷新队列', link: '/dev/widget-workmanager.md' }
      ]
    },
    {
      text: '5. 备忘与除错',
      items: [
        { text: '常见 Bug 与排障指南', link: '/dev/troubleshooting.md' }
      ]
    }
  ]
})

const zhUserDoc = defineCollection({
  type: 'doc',
  dir: 'user',
  linkPrefix: '/user',
  title: '用户文档',
  sidebar: [
    { text: '快速开始', link: '/user/quick-start.md' },
    { text: '课程表使用指南', link: '/user/timetable-guide.md' },
    { text: 'CSV 导入导出', link: '/user/import-export-csv.md' },
    { text: '桌面小组件', link: '/user/widget-guide.md' },
    { text: '常见问题', link: '/user/faq.md' }
  ]
})

export const zhCollections = defineCollections([
  zhDevDoc,
  zhUserDoc,
])

const enDevDoc = defineCollection({
  type: 'doc',
  dir: 'dev',
  linkPrefix: '/dev',
  title: 'Developer Docs',
  sidebar: 'auto',
})

const enUserDoc = defineCollection({
  type: 'doc',
  dir: 'user',
  linkPrefix: '/user',
  title: 'User Docs',
  sidebar: 'auto',
})

export const enCollections = defineCollections([
  enDevDoc,
  enUserDoc,
])
