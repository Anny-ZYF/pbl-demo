<template>
  <nav class="teacher-navigation" aria-label="教师端导航">
    <RouterLink class="teacher-navigation__item" :to="workspacePath">
      <el-icon><House /></el-icon>
      <span>教师工作台</span>
    </RouterLink>
    <button v-for="item in plannedItems" :key="item.label" class="teacher-navigation__item" type="button" @click="notifyUnavailable(item.label)">
      <el-icon><component :is="item.icon" /></el-icon>
      <span>{{ item.label }}</span>
      <el-tag size="small" effect="plain">规划中</el-tag>
    </button>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElIcon, ElMessage, ElTag } from 'element-plus'
import { DataAnalysis, Tickets, User } from '@element-plus/icons-vue'

const route = useRoute()
const workspacePath = computed(() => route.params.courseId ? `/teacher/course/${route.params.courseId}/workspace` : '/teacher/courses')
const plannedItems = [
  { label: '作业管理', icon: Tickets },
  { label: '学生管理', icon: User },
  { label: '成绩查看', icon: DataAnalysis }
]
const notifyUnavailable = (label) => ElMessage.info(`${label}页面正在接入真实业务接口`)
</script>

<style scoped>
.teacher-navigation { display: grid; gap: 7px; padding: 22px 10px; }
.teacher-navigation__item { display: flex; width: 100%; min-height: 46px; align-items: center; gap: 11px; padding: 0 14px; border: 0; border-radius: 3px; color: rgba(255,255,255,.9); background: transparent; font: inherit; text-align: left; text-decoration: none; cursor: pointer; }
.teacher-navigation__item:hover { color: #fff; background: rgba(53,103,152,.82); }
.teacher-navigation__item.router-link-active { color: #fff; background: #356798; box-shadow: inset 3px 0 #74c69d; }
.teacher-navigation__item span { flex: 1; }
.teacher-navigation__item .el-tag { color: rgba(255,255,255,.76); border-color: rgba(255,255,255,.35); transform: scale(.82); transform-origin: right center; }
</style>
