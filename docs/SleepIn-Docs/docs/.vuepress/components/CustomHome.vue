<template>
  <div class="custom-home">
    <div class="hero">
      <!-- 动画背景：跟随鼠标的网格点，颜色读取主题品牌色与分割线颜色 -->
      <div class="hero-effect" aria-hidden="true">
        <DotGrid
          :dot-size="4"
          :gap="20"
          :proximity="150"
          :speed-trigger="120"
          :shock-radius="240"
          :shock-strength="6"
        />
      </div>

      <div class="hero-content">
        <img src="/SleepIn.png" alt="SleepIn Logo" class="logo" no-view />
        <h1 class="title">{{ content.title }}</h1>
        <p class="tagline">{{ content.tagline }}</p>
        <p class="description">{{ content.description }}</p>

        <p v-if="content.localeNotice" class="locale-note">{{ content.localeNotice }}</p>

        <div class="actions">
          <a :href="links.start" class="action-btn alt">
            <VPIconify class="action-icon" name="mdi:book-open-variant" />
            <span>{{ content.startBtn }}</span>
          </a>
          <a :href="links.download" class="action-btn brand">
            <VPIconify class="action-icon" name="mdi:download" />
            <span>{{ content.downloadBtn }}</span>
          </a>
          <a :href="links.dev" class="action-btn alt">
            <VPIconify class="action-icon" name="mdi:code-braces" />
            <span>{{ content.devBtn }}</span>
          </a>
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
  </div>
</template>

<script setup lang="ts">
  import { useRoute } from 'vue-router'
  import { computed, nextTick, onMounted } from 'vue'
  import DotGrid from 'vuepress-theme-plume/components/background/DotGrid.vue'
  import VPIconify from 'vuepress-theme-plume/components/VPIconify.vue'

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
    } : {
      title: 'SleepIn 课程表',
      tagline: '更实用 · 更简洁 · 更美观',
      description: '一个开源轻量的 Android 课程表。',
      localeNotice: '',
      startBtn: '使用文档',
      downloadBtn: '下载最新版本',
      devBtn: '开发文档',
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

  /**
   * 主题的 DotGrid 组件通过 useCssVar 读取 `--vp-c-brand-3` / `--vp-c-divider`，
   * useCssVar 会把读到的值写回 <html> 的内联样式，而内联样式的优先级高于
   * `[data-theme="dark"]` 规则，会导致切换深色模式后全站的边框色仍是浅色取值。
   *
   * 组件已经把颜色保存进自己的 ref 中，这里在挂载后移除内联覆盖即可，
   * 深色模式重新由主题的 CSS 变量接管。
   */
  onMounted(async () => {
    await nextTick()
    const style = document.documentElement.style
    style.removeProperty('--vp-c-brand-3')
    style.removeProperty('--vp-c-divider')
  })
</script>


<style scoped>
  .custom-home {
    max-width: 1200px;
    margin: 0 auto;
    padding: 0 2rem;
  }

  /* Hero Section */
  .hero {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 4rem;
    padding-block: 1rem;
  }

  /*
   * 动画背景层。
   * 宿主容器（.vp-home-box > .container）限定了 max-width，若直接用 inset: 0
   * 只会得到中间一块矩形；这里用 left: 50% + 100vw + translateX(-50%)
   * 把背景层横向撑满整个视口，纵向上下各溢出一段盖住容器内边距。
   * 由此产生的横向溢出由 `.vp-home` 的 `overflow-x: clip` 裁掉，
   * 参见 theme/styles/custom.css。
   */
  .hero-effect {
    position: absolute;
    top: -8rem;
    bottom: -6rem;
    left: 50%;
    width: 100vw;
    transform: translateX(-50%);
    z-index: 0;
    pointer-events: none;
    /* 横向靠视口边缘自然裁切，纵向上下渐隐，避免出现生硬的矩形边界 */
    -webkit-mask-image: linear-gradient(to bottom, transparent 0%, #000 22%, #000 55%, transparent 100%);
    mask-image: linear-gradient(to bottom, transparent 0%, #000 22%, #000 55%, transparent 100%);
  }

  @media (prefers-reduced-motion: reduce) {
    .hero-effect {
      display: none;
    }
  }

  .hero-content {
    position: relative;
    z-index: 1;
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
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.75rem 1.5rem;
    border-radius: 999px;
    font-weight: 600;
    text-decoration: none;
    transition: all 0.25s ease;
  }

  .action-icon {
    width: 1.15em;
    height: 1.15em;
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
    z-index: 1;
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
