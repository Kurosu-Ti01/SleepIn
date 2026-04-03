import { defineClientConfig } from 'vuepress/client'
import ReleaseDownload from './theme/components/ReleaseDownload.vue'
import CustomHome from './components/CustomHome.vue'

import './theme/styles/custom.css'

export default defineClientConfig({
  enhance({ app }) {
    app.component('ReleaseDownload', ReleaseDownload)
    app.component('CustomHome', CustomHome)
  },
})

