import { defineCollection, defineCollections } from 'vuepress-theme-plume'

const zhDevDoc = defineCollection({
  type: 'doc',
  dir: 'dev',
  linkPrefix: '/dev',
  title: '开发者文档',
  sidebar: [
      {text: '从这里开始', link: '/dev/start-here.md' },
      {text: '1. 编译构建', prefix: '1.build-debug', items: 'auto' },
      {text: '2. 项目架构', prefix: '2.architecture', items: 'auto' },
      {text: '3. 核心模块', prefix: '3.business', items: 'auto' },
      {text: '4. 其他东西', prefix: '4.others', items: 'auto' },
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
