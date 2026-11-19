import { createApp } from 'vue'
import ElementPlus, { ElLoading } from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { pinia } from './stores'
import { useAuthStore } from './stores/auth'
import { setUnauthorizedHandler } from './api/http'
import './styles/index.css'

const app = createApp(App)

app.use(ElementPlus)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)

app.use(ElLoading)

const authStore = useAuthStore(pinia)
setUnauthorizedHandler(() => {
  authStore.clearSession()
  if (router.currentRoute.value.meta.requiresAuth) {
    router.replace({ name: 'login', query: { reason: 'expired' } })
  }
})

app.mount('#app')