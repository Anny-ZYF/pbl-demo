<template>
  <nav class="teacher-navigation" aria-label="教师端导航">
    <RouterLink class="teacher-navigation__item" :to="workspacePath">
      <el-icon><House /></el-icon>
      <span>教师工作台</span>
    </RouterLink>
    <RouterLink v-if="route.params.courseId" class="teacher-navigation__item" :to="`/teacher/course/${route.params.courseId}/assignments`">
      <el-icon><Tickets /></el-icon>
      <span>作业管理</span>
    </RouterLink>
    <RouterLink v-if="route.params.courseId" class="teacher-navigation__item" :to="`/teacher/course/${route.params.courseId}/students`">
      <el-icon><User /></el-icon>
      <span>学生管理</span>
    </RouterLink>
    <RouterLink v-if="route.params.courseId" class="teacher-navigation__item" :to="`/teacher/course/${route.params.courseId}/scores`">
      <el-icon><DataAnalysis /></el-icon>
      <span>成绩查看</span>
    </RouterLink>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElIcon } from 'element-plus'
import { DataAnalysis, Tickets, User } from '@element-plus/icons-vue'

const route = useRoute()
const workspacePath = computed(() => route.params.courseId ? `/teacher/course/${route.params.courseId}/workspace` : '/teacher/courses')
</script>

<style scoped>
.teacher-navigation { display: grid; gap: 7px; padding: 22px 10px; }
.teacher-navigation__item { display: flex; width: 100%; min-height: 46px; align-items: center; gap: 11px; padding: 0 14px; border: 0; border-radius: 3px; color: rgba(255,255,255,.9); background: transparent; font: inherit; text-align: left; text-decoration: none; cursor: pointer; }
.teacher-navigation__item:hover { color: #fff; background: rgba(53,103,152,.82); }
.teacher-navigation__item.router-link-active { color: #fff; background: #356798; box-shadow: inset 3px 0 #74c69d; }
.teacher-navigation__item span { flex: 1; }
</style>
