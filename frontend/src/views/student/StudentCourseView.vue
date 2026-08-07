<template>
  <div class="page">
    <header><h1>{{ course?.name || '课程作业' }}</h1></header>
    <StudentGradeTrend :scores="courseScores" />
    <section class="panel">
      <div class="panel-head"><h2>课程作业</h2><span>{{ assignments.length }} 项作业</span></div>
      <button v-for="item in assignments" :key="item.id" class="assignment" @click="router.push('/student/assignment/' + item.id)">
        <div class="icon">作业</div><div class="main"><h3>{{ item.title }}</h3><p>截止时间：{{ formatDeadline(item.deadline) }}</p></div>
        <i :class="statusClass(item)">{{ statusText(item) }}</i>
      </button>
    </section>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StudentGradeTrend from '@/components/student/StudentGradeTrend.vue'
import { getAssignments, getCourses, getScores } from '@/api/student'
import { getErrorMessage } from '@/api/response'
const route = useRoute(); const router = useRouter(); const courses = ref([]); const assignments = ref([]); const scores = ref([]); const errorMessage = ref('')
const course = computed(() => courses.value.find((item) => String(item.id) === String(route.params.courseId))); const courseScores = computed(() => scores.value.filter((item) => item.courseName === course.value?.name))
const statusText = (item) => item.teamFormation ? '组队中' : item.scorePublished ? '已批阅' : item.submitted ? '已提交' : '待提交'
const statusClass = (item) => item.teamFormation ? 'forming' : item.scorePublished ? 'reviewed' : item.submitted ? 'submitted' : 'pending'
const formatDeadline = (value) => value ? new Date(value).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '未设置'
onMounted(async () => { try { [courses.value, assignments.value, scores.value] = await Promise.all([getCourses(), getAssignments(route.params.courseId), getScores()]) } catch (error) { errorMessage.value = getErrorMessage(error, '课程数据加载失败') } })
</script>
<style scoped>
.forming,.pending,.submitted,.reviewed{padding:5px 10px!important;border-radius:99px!important;font-style:normal!important}.forming{color:#356798!important;background:#eaf2fa!important}.pending{color:#9b6b29!important;background:#fff6e8!important}.submitted{color:#c46a22!important;background:#fff1e3!important}.reviewed{color:#35845a!important;background:#edf8f1!important}
.page{max-width:1120px;margin:0 auto}header{margin-bottom:25px}header h1{color:#263b53}.panel{overflow:hidden;border-radius:9px;background:#fff}.panel-head{display:flex;justify-content:space-between;padding:19px 22px;border-bottom:1px solid #edf1f4}.assignment{display:flex;width:100%;align-items:center;gap:16px;padding:18px 22px;border:0;border-bottom:1px solid #edf1f4;background:#fff;text-align:left;cursor:pointer}.icon{padding:12px;border-radius:9px;color:#356798;background:#eaf2fa}.main{flex:1}.main p{color:#98a5b3}.assignment i{padding:5px 10px;border-radius:99px;font-style:normal;white-space:nowrap}.forming{color:#356798;background:#eaf2fa}.pending{color:#9b6b29;background:#fff6e8}.submitted{color:#c46a22;background:#fff1e3}.reviewed{color:#35845a;background:#edf8f1}
</style>
