<template>
  <main class="not-found-page">
    <div class="not-found-card">
      <span>404</span>
      <h1>页面不存在</h1>
      <p>你访问的地址不存在或已经调整。</p>
      <el-button type="primary" @click="goHome">返回首页</el-button>
    </div>
  </main>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElButton } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const goHome = () => router.replace(authStore.isAuthenticated ? authStore.homePath : '/login')
</script>
