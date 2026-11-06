import { defineClientConfig } from 'vuepress/client'
import ReleaseDownload from './theme/components/ReleaseDownload.vue'
import CustomHome from './components/CustomHome.vue'

import './theme/styles/custom.css'

/** 深浅模式开关最近一次被点击的位置，用作圆形扩散动画的圆心 */
let appearanceOrigin = { x: 0, y: 0 }

/**
 * 记录深浅模式开关的点击位置并写入 CSS 变量。
 *
 * `--vt-origin-x/y` 供 `custom.css` 里「切到深色时先裁掉新快照」的静态规则使用，
 * 避免动画接管的瞬间闪一帧新主题。
 *
 * 键盘触发（Enter / Space）时 `clientX`/`clientY` 都是 0，此时沿用上一次的位置；
 * 从未用鼠标点过则退回右上角。
 */
function trackAppearanceOrigin() {
  document.addEventListener('click', (event) => {
    const target = event.target as Element | null
    if (!target?.closest?.('.vp-switch-appearance'))
      return
    if (!event.clientX && !event.clientY)
      return

    appearanceOrigin = { x: event.clientX, y: event.clientY }
    const { style } = document.documentElement
    style.setProperty('--vt-origin-x', `${event.clientX}px`)
    style.setProperty('--vt-origin-y', `${event.clientY}px`)
  }, true)
}

/**
 * 把深浅模式切换动画统一成「新主题从切换按钮处圆形展开」。
 *
 * 主题自带的 `circle-clip` 只在切到浅色时扩散新快照，切到深色时是让旧快照收缩，
 * 两个方向观感相反。这里在过渡就绪时补一个作用于 `::view-transition-new(root)`
 * 的 WAAPI 动画（与主题内部用的是同一套机制），旧快照的收缩动画则由
 * `custom.css` 里的 `clip-path: none !important` 屏蔽。
 *
 * 之所以用 WAAPI 而不是 CSS `@keyframes`：部分 Chromium 环境不会执行挂在
 * view-transition 伪元素上的 CSS 动画，而 WAAPI 动画稳定生效。
 */
function unifyAppearanceTransition() {
  if (typeof document === 'undefined' || !('startViewTransition' in document))
    return

  const nativeStart = document.startViewTransition.bind(document)

  document.startViewTransition = ((callback: () => void | Promise<void>) => {
    const transition = nativeStart(callback)

    transition.ready.then(() => {
      // 只有切到深色需要接管；切到浅色时主题自己的扩散动画已经正确
      if (document.documentElement.dataset.theme !== 'dark')
        return

      const { x, y } = appearanceOrigin
      const radius = Math.hypot(
        Math.max(x, innerWidth - x),
        Math.max(y, innerHeight - y),
      )

      document.documentElement.animate(
        {
          clipPath: [
            `circle(0px at ${x}px ${y}px)`,
            `circle(${radius}px at ${x}px ${y}px)`,
          ],
        },
        {
          duration: 650,
          easing: 'ease-out',
          fill: 'forwards',
          pseudoElement: '::view-transition-new(root)',
        },
      )
    }).catch(() => {
      // 过渡被浏览器中止（如页面切到后台）时不做处理
    })

    return transition
  }) as typeof document.startViewTransition
}

export default defineClientConfig({
  enhance({ app }) {
    app.component('ReleaseDownload', ReleaseDownload)
    app.component('CustomHome', CustomHome)
  },
  setup() {
    trackAppearanceOrigin()
    unifyAppearanceTransition()
  },
})
