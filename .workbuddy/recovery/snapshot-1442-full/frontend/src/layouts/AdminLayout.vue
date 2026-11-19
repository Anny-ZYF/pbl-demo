<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <RouterLink class="brand" to="/admin/dashboard">
        <span class="brand-mark">GD</span>
        <span>
          <strong>差异化评分</strong>
          <small>管理平台</small>
        </span>
      </RouterLink>
      <AdminNavigation />
      <div class="sidebar-note">广东医科大学 · 团队学习</div>
    </aside>

    <div class="app-main">
      <header class="app-topbar">
        <button class="mobile-menu-button" type="button" aria-label="打开导航" @click="drawerVisible = true">
          <el-icon><Menu /></el-icon>
        </button>
        <div class="topbar-title">
          <strong>{{ route.meta.title }}</strong>
          <span>管理员端</span>
        </div>
        <AccountMenu />
      </header>
      <main class="page-container">
        <RouterView />
      </main>
    </div>

    <el-drawer v-model="drawerVisible" direction="ltr" size="250px" :with-header="false">
      <RouterLink class="brand brand--drawer" to="/admin/dashboard" @click="drawerVisible = false">
        <span class="brand-mark">GD</span>
        <span><strong>差异化评分</strong><small>管理平台</small></span>
      </RouterLink>
      <AdminNavigation @navigated="drawerVisible = false" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElDrawer, ElIcon } from 'element-plus'
import { Menu } from '@element-plus/icons-vue'
import AccountMenu from '@/components/AccountMenu.vue'
import AdminNavigation from '@/components/AdminNavigation.vue'

const route = useRoute()
const drawerVisible = ref(false)
</script>
