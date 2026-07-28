import { createApp } from 'vue'
import { ElLoading } from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { pinia } from './stores'
import { useAuthStore } from './stores/auth'
import { setUnauthorizedHandler } from './api/http'
import './styles/index.css'

const app = createApp(App)

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
