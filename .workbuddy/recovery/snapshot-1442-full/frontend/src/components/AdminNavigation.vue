<template>
  <el-menu :default-active="route.path" class="admin-menu" @select="navigate">
    <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
      <el-icon><component :is="item.icon" /></el-icon>
      <span>{{ item.label }}</span>
    </el-menu-item>
  </el-menu>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { ElIcon, ElMenu, ElMenuItem } from 'element-plus'
import { DataAnalysis, Document, HomeFilled, School, UserFilled } from '@element-plus/icons-vue'

const emit = defineEmits(['navigated'])
const route = useRoute()
const router = useRouter()

const menuItems = [
  { path: '/admin/dashboard', label: '工作台', icon: HomeFilled },
  { path: '/admin/organization', label: '组织与账号', icon: UserFilled },
  { path: '/admin/teaching-data', label: '教学数据', icon: School },
  { path: '/admin/scoring-model', label: '评分模型', icon: DataAnalysis },
  { path: '/admin/audit-logs', label: '审计日志', icon: Document }
]

async function navigate(path) {
  if (path !== route.path) await router.push(path)
  emit('navigated')
}
</script>
