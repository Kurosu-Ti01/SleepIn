/**
 * @see https://theme-plume.vuejs.press/config/navigation/ 查看文档了解配置详情
 *
 * Navbar 配置文件，它在 `.vuepress/plume.config.ts` 中被导入。
 */

import { defineNavbarConfig } from 'vuepress-theme-plume'

export const zhNavbar = defineNavbarConfig([
  { text: '首页', link: '/', icon: 'mdi:home-variant-outline' },
  { text: '用户文档', link: '/user/', icon: 'mdi:book-open-page-variant-outline' },
  { text: '开发者文档', link: '/dev/', icon: 'mdi:code-braces' },
  // { text: '博客', link: '/blog/' },
  // { text: '标签', link: '/blog/tags/' },
  // { text: '归档', link: '/blog/archives/' },
  // {
  //   text: '笔记',
  //   items: [{ text: '示例', link: '/demo/README.md' }]
  // },
  {
    text: '更多',
    icon: 'mdi:dots-horizontal-circle-outline',
    items: [
      { text: '下载最新版本', link: '/download/', icon: 'mdi:download-outline' },
      {
        text: 'GitHub 仓库',
        link: 'https://github.com/Kurosu-Ti01/SleepIn',
        icon: 'mdi:github',
      },
      {
        text: '反馈问题',
        link: 'https://github.com/Kurosu-Ti01/SleepIn/issues',
        icon: 'mdi:bug-outline',
      },
    ],
  },
])

export const enNavbar = defineNavbarConfig([
  { text: 'Home', link: '/en/', icon: 'mdi:home-variant-outline' },
  { text: 'User Docs', link: '/user/', icon: 'mdi:book-open-page-variant-outline' },
  { text: 'Developer Docs', link: '/dev/', icon: 'mdi:code-braces' },
  // { text: 'Blog', link: '/en/blog/' },
  // { text: 'Tags', link: '/en/blog/tags/' },
  // { text: 'Archives', link: '/en/blog/archives/' },
  // {
  //   text: 'Notes',
  //   items: [{ text: 'Demo', link: '/en/demo/README.md' }]
  // },
  {
    text: 'More',
    icon: 'mdi:dots-horizontal-circle-outline',
    items: [
      { text: 'Download', link: '/download/', icon: 'mdi:download-outline' },
      {
        text: 'GitHub Repository',
        link: 'https://github.com/Kurosu-Ti01/SleepIn',
        icon: 'mdi:github',
      },
      {
        text: 'Report an Issue',
        link: 'https://github.com/Kurosu-Ti01/SleepIn/issues',
        icon: 'mdi:bug-outline',
      },
    ],
  },
])
