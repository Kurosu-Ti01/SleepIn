<template>
  <div class="custom-home">
    <div class="hero">
      <div class="hero-content">
        <img src="/SleepIn.png" alt="SleepIn Logo" class="logo" no-view />
        <h1 class="title">{{ content.title }}</h1>
        <p class="tagline">{{ content.tagline }}</p>
        <p class="description">{{ content.description }}</p>

        <p v-if="content.localeNotice" class="locale-note">{{ content.localeNotice }}</p>

        <div class="actions">
          <a :href="links.start" class="action-btn brand">{{ content.startBtn }}</a>
          <a :href="links.download" class="action-btn alt">{{ content.downloadBtn }}</a>
          <a :href="links.dev" class="action-btn alt">{{ content.devBtn }}</a>
        </div>
      </div>

      <div class="hero-image">
        <div class="device-mockup">
          <figure class="screenshot-card light-card">
            <img src="/ScreenshotsLight01.jpg" alt="SleepIn App Screenshot Light" class="screenshot" />
          </figure>
          <figure class="screenshot-card dark-card">
            <img src="/ScreenshotsDark01.jpg" alt="SleepIn App Screenshot Dark" class="screenshot" />
          </figure>
        </div>
      </div>
    </div>

    <div class="features">
      <div v-for="(feature, index) in content.features" :key="index" class="feature-card">
        <h3>{{ feature.title }}</h3>
        <p>{{ feature.desc }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { useRoute } from 'vue-router'
  import { computed } from 'vue'

  const route = useRoute()
  const isEn = computed(() => route.path.startsWith('/en/'))

  const content = computed(() => {
    return isEn.value ? {
      title: 'SleepIn Timetable',
      tagline: 'More Practical · Simpler · More Beautiful',
      description: 'A lightweight, open-source Android timetable app.',
      localeNotice: 'Note: Due to initial design decisions, the UI is currently optimized for Chinese, and the app currently supports Chinese only.',
      startBtn: 'Getting Started',
      downloadBtn: 'Download APK',
      devBtn: 'Developer Guide',
      features: [
        { title: '🎒 Easy Import', desc: 'Effortlessly import schedules from your university system, no more manual entries.' },
        { title: '🎨 Beautiful & Intuitive', desc: 'Follows Material Design 3 and dynamic colors for the most elegant user experience.' },
        { title: '🧩 Widgets Support', desc: 'Desktop widgets allow you to glance at your schedule without opening the app.' },
        { title: '⏰ Class Reminder', desc: 'Receive notifications before class starts, so you never miss important lectures.' },
        { title: '💾 Quick Backup', desc: 'Back up and restore timetable data quickly to migrate between devices with ease.' },
        { title: '🛡️ Local Privacy', desc: 'Your data is always stored locally, open-source and transparent for your safety.' }
      ]
    } : {
      title: 'SleepIn 课程表',
      tagline: '更实用 · 更简洁 · 更美观',
      description: '一个开源轻量的 Android 课程表。',
      localeNotice: '',
      startBtn: '开始使用',
      downloadBtn: '下载最新版本',
      devBtn: '开发文档',
      features: [
        { title: '🎒 课程导入', desc: '轻松导入教务系统 CSV 课表，告别繁琐的手动输入。' },
        { title: '🎨 美观直观', desc: '使用 Material Design 3 现代化 UI，支持系统动态色彩，呈现最优雅的界面。' },
        { title: '🧩 小部件支持', desc: '提供多样化的桌面小部件，无需打开应用即可快速一瞥今天的日程安排。' },
        { title: '⏰ 课前提醒', desc: '支持课前提醒，在上课前按时通知，帮助你不错过每一节重要课程。' },
        { title: '💾 便捷备份', desc: '一键备份与恢复课表数据，换机迁移更轻松，重要课表不丢失。' },
        { title: '🛡️ 本地隐私', desc: '您的数据始终储存在设备本地，绝不私自上传云端，完全开源透明。' }
      ]
    }
  })

  const links = computed(() => {
    return isEn.value ? {
      start: '/user/',
      download: '/download/',
      dev: '/dev/'
    } : {
      start: '/user/',
      download: '/download/',
      dev: '/dev/'
    }
  })
</script>


<style scoped>
  .custom-home {
    max-width: 1200px;
    margin: 0 auto;
    padding: 0 2rem;
    display: flex;
    flex-direction: column;
    gap: 4rem;
  }

  /* Hero Section */
  .hero {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 4rem;
  }

  .hero-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    gap: 1rem;
  }

  .logo {
    width: 300px;
  }

  .title {
    font-size: 3.5rem;
    font-weight: 800;
    line-height: 1.2;
    margin: 0;
    background: -webkit-linear-gradient(120deg, var(--vp-c-brand-1) 30%, var(--vp-c-brand-3));
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }

  .tagline {
    font-size: 1.5rem;
    font-weight: 600;
    color: var(--vp-c-text-1);
    margin: 0;
  }

  .description {
    font-size: 1.125rem;
    line-height: 1.6;
    color: var(--vp-c-text-2);
    margin: 0;
    max-width: 500px;
  }

  .locale-note {
    margin: 0;
    max-width: 680px;
    padding: 0.65rem 0.9rem;
    border-radius: 0.75rem;
    font-size: 0.95rem;
    line-height: 1.5;
    color: var(--vp-c-warning-1);
    background: color-mix(in srgb, var(--vp-c-warning-1) 10%, transparent);
    border: 1px solid color-mix(in srgb, var(--vp-c-warning-1) 28%, transparent);
  }

  /* Actions */
  .actions {
    display: flex;
    gap: 1rem;
    flex-wrap: wrap;
    justify-content: center;
  }

  .action-btn {
    display: inline-block;
    padding: 0.75rem 1.5rem;
    border-radius: 999px;
    font-weight: 600;
    text-decoration: none;
    transition: all 0.25s ease;
  }

  .action-btn.brand {
    background-color: var(--vp-c-brand-1);
    color: var(--vp-c-bg);
    box-shadow: 0 4px 12px rgba(var(--vp-c-brand-1), 0.3);
  }

  .action-btn.brand:hover {
    background-color: var(--vp-c-brand-2);
    transform: translateY(-2px);
  }

  .action-btn.alt {
    background-color: var(--vp-c-bg-soft);
    color: var(--vp-c-text-1);
    border: 1px solid var(--vp-c-divider);
  }

  .action-btn.alt:hover {
    background-color: var(--vp-c-bg-mute);
    transform: translateY(-2px);
  }

  /* Hero Image */
  .hero-image {
    flex: 1;
    display: flex;
    justify-content: center;
    align-self: flex-start;
    position: sticky;
    top: 6rem;
  }

  .device-mockup {
    position: relative;
    width: min(360px, 92vw);
    height: 540px;
    perspective: 1200px;
  }

  .screenshot-card {
    position: absolute;
    width: 80%;
    margin: 0;
    border-radius: 1.35rem;
    overflow: hidden;
    background-color: var(--vp-c-bg);
    border: 1px solid var(--vp-c-divider);
    box-shadow: 0 24px 40px -18px var(--vp-home-card-shadow);
    transition: transform 0.45s ease, box-shadow 0.45s ease, top 0.45s ease, z-index 0.45s;
    transform-origin: bottom center;
  }

  /* Light theme: light screenshot on top */
  .light-card {
    top: 20px;
    left: 6%;
    z-index: 3;
    transform: rotate(-3deg);
  }

  .dark-card {
    top: 40px;
    right: 6%;
    z-index: 2;
    transform: rotate(4deg);
  }

  .device-mockup:hover .light-card {
    transform: rotate(-2.5deg) translate(-2px, -6px);
    box-shadow: 0 30px 52px -18px var(--vp-home-card-shadow-hover);
  }

  .device-mockup:hover .dark-card {
    transform: rotate(3deg) translate(2px, -3px);
    box-shadow: 0 30px 52px -18px var(--vp-home-card-shadow-hover);
  }

  /* Dark theme: dark screenshot on top */
  html[data-theme="dark"] .light-card {
    top: 40px;
    z-index: 2;
    transform: rotate(-4deg);
  }

  html[data-theme="dark"] .dark-card {
    top: 20px;
    z-index: 3;
    transform: rotate(3deg);
  }

  html[data-theme="dark"] .device-mockup:hover .light-card {
    transform: rotate(-6deg) translate(-4px, -4px);
  }

  html[data-theme="dark"] .device-mockup:hover .dark-card {
    transform: rotate(5deg) translate(4px, -8px);
  }

  .screenshot {
    display: block;
    width: 100%;
    height: auto;
    object-fit: cover;
  }

  /* Features Section */
  .features {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
    gap: 2rem;
    margin-top: 2rem;
    padding-top: 4rem;
    border-top: 1px solid var(--vp-c-divider);
  }

  .feature-card {
    padding: 1.5rem;
    border-radius: 1rem;
    background-color: var(--vp-c-bg-soft);
    transition: transform 0.25s ease, background-color 0.25s ease;
  }

  .feature-card:hover {
    transform: translateY(-5px);
    background-color: var(--vp-c-bg-mute);
  }

  .feature-card h3 {
    font-size: 1.25rem;
    font-weight: 700;
    margin: 0 0 1rem 0;
    color: var(--vp-c-text-1);
  }

  .feature-card p {
    font-size: 1rem;
    line-height: 1.5;
    color: var(--vp-c-text-2);
    margin: 0;
  }

  /* Responsive */
  @media (max-width: 960px) {
    .hero {
      flex-direction: column;
      text-align: center;
      gap: 3rem;
    }

    .hero-content {
      align-items: center;
    }

    .hero-image {
      align-self: center;
      position: relative;
      top: 0;
    }

    .actions {
      justify-content: center;
    }

    .device-mockup {
      height: 500px;
    }
  }

  @media (max-width: 640px) {
    .title {
      font-size: 2.5rem;
    }

    .tagline {
      font-size: 1.25rem;
    }

    .features {
      grid-template-columns: 1fr;
    }

    .device-mockup {
      width: min(280px, 84vw);
      height: 420px;
    }

    .screenshot-card {
      width: 80%;
      border-radius: 1rem;
    }

    .light-card {
      top: 10px;
    }

    html[data-theme="dark"] .light-card {
      top: 20px;
    }

    .dark-card {
      top: 20px;
    }

    html[data-theme="dark"] .dark-card {
      top: 10px;
    }
  }
</style>
