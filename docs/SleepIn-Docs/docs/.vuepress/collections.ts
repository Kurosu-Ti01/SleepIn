import { defineCollection, defineCollections } from 'vuepress-theme-plume'

const zhDevDoc = defineCollection({
  type: 'doc',
  dir: 'dev',
  linkPrefix: '/dev',
  title: '开发者文档',
  sidebar: [
      {text: '从这里开始', link: '/dev/start-here.md', icon: 'mdi:flag-outline' },
      {text: '1. 编译构建', prefix: '1.build-debug', icon: 'mdi:hammer-wrench', items: 'auto' },
      {text: '2. 项目架构', prefix: '2.architecture', icon: 'mdi:sitemap-outline', items: 'auto' },
      {text: '3. 核心模块', prefix: '3.business', icon: 'mdi:layers-outline', items: 'auto' },
      {text: '4. 功能实现', prefix: '4.feature', icon: 'mdi:puzzle-outline', items: 'auto' },
      {text: '5. 其他东西', prefix: '5.others', icon: 'mdi:toolbox-outline', items: 'auto' },
      ]
})

const zhUserDoc = defineCollection({
  type: 'doc',
  dir: 'user',
  linkPrefix: '/user',
  title: '用户文档',
  sidebar: [
    { text: '快速开始', link: '/user/quick-start.md', icon: 'mdi:rocket-launch-outline' },
    { text: '课程表使用指南', link: '/user/timetable-guide.md', icon: 'mdi:calendar-month-outline' },
    { text: 'CSV 导入导出', link: '/user/import-export-csv.md', icon: 'mdi:file-delimited-outline' },
    { text: '从教务系统导入', link: '/user/portal-import.md', icon: 'mdi:cloud-download-outline' },
    { text: '桌面小组件', link: '/user/widget-guide.md', icon: 'mdi:widgets-outline' },
    { text: '常见问题', link: '/user/faq.md', icon: 'mdi:help-circle-outline' }
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
