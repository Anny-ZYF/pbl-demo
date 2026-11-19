<template>
  <nav class="student-navigation" aria-label="学生端导航">
    <RouterLink class="student-navigation__item" :class="{ active: route.name === 'student-dashboard' }" to="/student/dashboard">
      <el-icon><House /></el-icon>
      <span>首页</span>
    </RouterLink>
    <RouterLink class="student-navigation__item" :class="{ active: isCourseRoute }" to="/student/courses">
      <el-icon><Reading /></el-icon>
      <span>我的课程</span>
    </RouterLink>
    <RouterLink v-if="recentProject" class="student-navigation__item student-navigation__item--project" :class="{ active: isCurrentProject }" :to="`/student/assignment/${recentProject.assignmentId}`" :title="recentProject.title">
      <el-icon><FolderOpened /></el-icon>
      <span>当前项目</span>
    </RouterLink>
  </nav>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElIcon } from 'element-plus'
import { FolderOpened, House, Reading } from '@element-plus/icons-vue'
import { getRecentProject, RECENT_PROJECT_EVENT } from '@/utils/studentRecentProject'

const route = useRoute()
const recentProject = ref(getRecentProject())
const isCourseRoute = computed(() => ['student-courses', 'student-course'].includes(route.name))
const isCurrentProject = computed(() => route.name === 'student-assignment-detail' && String(route.params.assignmentId) === recentProject.value?.assignmentId)
const updateRecentProject = (event) => { recentProject.value = event.detail || getRecentProject() }
onMounted(() => window.addEventListener(RECENT_PROJECT_EVENT, updateRecentProject))
onBeforeUnmount(() => window.removeEventListener(RECENT_PROJECT_EVENT, updateRecentProject))
</script>

<style scoped>
.student-navigation{display:grid;gap:7px;padding:18px 13px}
.student-navigation__item{display:flex;align-items:center;gap:12px;height:46px;padding:0 14px;border-radius:10px;color:#64748b;text-decoration:none;transition:background .2s,color .2s,transform .2s}
.student-navigation__item:hover{color:#2563eb;background:#f1f6ff;transform:translateX(2px)}
.student-navigation__item.active{color:#2563eb;background:#eaf2ff;font-weight:600}
.student-navigation__item .el-icon{font-size:18px}
</style>
