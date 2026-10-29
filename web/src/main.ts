import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './app/router'
import { i18n } from './locales'
import './styles/main.css'
import './styles/story.css'
import './styles/ai.css'

createApp(App).use(createPinia()).use(i18n).use(router).mount('#app')

import './styles/experience.css'
