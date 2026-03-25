<template>
  <div>{{ statusText }}</div>
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue'

  const statusText = ref('正在获取最新版本信息...')

  onMounted(async () => {
    const apiUrl = 'https://api.github.com/repos/Kurosu-Ti01/SleepIn/releases/latest'

    try {
      const response = await fetch(apiUrl, {
        headers: {
          Accept: 'application/vnd.github+json',
        },
      })

      if (!response.ok) {
        throw new Error(`GitHub API error: ${response.status}`)
      }

      const release = await response.json()
      const assets = Array.isArray(release.assets) ? release.assets : []

      const apkAsset =
        assets.find((asset: { name?: string }) =>
          typeof asset.name === 'string' && /universal-release.*\.apk$/i.test(asset.name)
        ) ||
        assets.find((asset: { name?: string }) =>
          typeof asset.name === 'string' && /\.apk$/i.test(asset.name)
        )

      if (!apkAsset?.browser_download_url) {
        throw new Error('No APK asset found in the latest release.')
      }

      statusText.value = `已找到安装包，正在开始下载：${apkAsset.name}`
      window.location.href = apkAsset.browser_download_url
    } catch (error) {
      console.error(error)
      statusText.value = '自动下载失败，请使用上方备用链接前往 GitHub Releases 页面手动下载。'
    }
  })
</script>
