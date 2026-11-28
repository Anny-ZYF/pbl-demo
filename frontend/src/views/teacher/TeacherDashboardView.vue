<template>
  <div class="teacher-home">
    <div class="breadcrumb"><button type="button" @click="router.push('/teacher/courses')">课程中心</button><span>&gt;</span><strong>{{ currentCourse?.name || '课程工作台' }}</strong></div>
    <div v-if="errorMessage" class="error-box">{{ errorMessage }}</div>
    <header class="page-header"><h1>欢迎回来，{{ teacherName }}</h1><p>{{ currentCourse?.name || '当前课程' }}</p></header>
    <div class="stats-cards">
      <div class="stat-card"><div><p>进行中项目</p><strong>{{ activeAssignments }}</strong></div><i>▣</i></div>
      <div class="stat-card"><div><p>总学生数</p><strong>{{ students.length }}</strong></div><i>♙</i></div>
      <div class="stat-card"><div><p>待批改任务</p><strong>{{ pendingSubmissions }}</strong></div><i>✓</i></div>
    </div>
    <div v-if="loading" class="loading-box">正在加载课程数据…</div>
    <div v-else class="content-grid">
      <section class="panel project-progress">
        <div class="section-header">
          <h2>作业进度</h2>
          <span>{{ assignments.length }} 个作业</span>
        </div>
        <div v-if="recentFourAssignments.length" class="progress-list">
          <div
            v-for="item in recentFourAssignments"
            :key="item.id"
            class="progress-item"
            role="link"
            tabindex="0"
            :aria-label="`查看「${item.title}」的作业情况`"
            :title="`进入「${item.title}」的查看作业`"
            @click="openAssignment(item)"
            @keydown.enter.prevent="openAssignment(item)"
            @keydown.space.prevent="openAssignment(item)"
          >
            <div class="progress-title">
              <strong>{{ item.title }}</strong>
              <span class="progress-value">{{ assignmentProgress(item) }}%<i>›</i></span>
            </div>
            <div class="progress-track">
              <span :style="{ width: `${assignmentProgress(item)}%` }"></span>
            </div>
            <div class="progress-meta">
              <span>{{ assignmentProgress(item) >= 100 ? '已完成' : '进行中' }}</span>
              <span>{{ remainingDays(item.deadline) }}</span>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无作业进度数据" />
      </section>
      <section class="panel tasks">
        <div class="section-header">
          <h2>待办任务</h2>
          <span>{{ pendingSubmissions }} 项</span>
        </div>
        <div v-if="pendingAssignments.length" class="list">
          <div v-for="item in pendingAssignments" :key="item.id" class="task-item">
            <span class="check"></span>
            <div>
              <strong>{{ item.title }}</strong>
              <small>待批改 {{ pendingReviewCount(item) }} 份</small>
            </div>
            <em>{{ formatDate(item.deadline) }}</em>
          </div>
        </div>
        <el-empty v-else description="暂无待办任务" />
      </section>
    </div>
    <section class="panel visualization">
      <div class="section-header">
        <h2>核心数据可视化</h2>
        <span>基于当前有效提交</span>
      </div>
      <div class="charts-container">
        <div>
          <h3>作业提交情况</h3>
          <div v-if="hasSubmissionData" class="chart-box">
            <canvas ref="submissionCanvas"></canvas>
          </div>
          <el-empty v-else description="暂无提交数据" />
        </div>
        <div>
          <h3>团队教师评分分布</h3>
          <div v-if="hasGradeData" class="chart-box">
            <canvas ref="gradeCanvas"></canvas>
          </div>
          <el-empty v-else description="暂无已批改成绩" />
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElEmpty } from 'element-plus'
import { Chart } from 'chart.js/auto'
import { getAssignments, getCourses, getStudents, getSubmissions } from '@/api/teacher'
import { getErrorMessage } from '@/api/response'
import { useAuthStore } from '@/stores/auth'
import { pinia } from '@/stores'
import {
  assignmentProgress,
  gradedTeamScores,
  pendingReviewAssignments,
  pendingReviewCount,
  pendingReviewTotal,
  scoreBuckets,
  submissionTotals
} from '@/utils/teacherDashboard'

const route = useRoute(); const router = useRouter(); const auth = useAuthStore(pinia)
const courses = ref([]); const assignments = ref([]); const students = ref([]); const submissions = ref([]); const loading = ref(false); const errorMessage = ref('')
const submissionCanvas = ref(null); const gradeCanvas = ref(null); let submissionChart; let gradeChart
const currentCourse = computed(() => courses.value.find((item) => String(item.id) === String(route.params.courseId)))
const teacherName = computed(() => auth.user?.displayName || auth.user?.name || '教师')
const activeAssignments = computed(() => assignments.value.filter((item) => ['PUBLISHED', 'ACTIVE'].includes(item.status)).length)
const pendingSubmissions = computed(() => pendingReviewTotal(assignments.value))
const pendingAssignments = computed(() => pendingReviewAssignments(assignments.value))
const submissionSummary = computed(() => submissionTotals(assignments.value))
const hasSubmissionData = computed(() => submissionSummary.value.total > 0)
const gradedScores = computed(() => gradedTeamScores(submissions.value))
const hasGradeData = computed(() => gradedScores.value.length > 0)

// ========== 新增：按发布时间倒序，取最近4条 ==========
// 注意：如果你的字段不是 createTime，改成你存发布时间的字段，比如 publishTime
const recentFourAssignments = computed(() => {
  return [...assignments.value]
    .sort((a, b) => new Date(b.createTime) - new Date(a.createTime))
    .slice(0, 4)
})

// 进度条目直接进「查看作业」：工作台看的是整体进度，下一步动作通常是看这一项作业的组队与提交情况。
const openAssignment = (item) => router.push({ name: 'teacher-assignment-grading', params: { courseId: route.params.courseId, assignmentId: item.id } })
const remainingDays = (value) => { if (!value) return '未设置截止日期'; const days = Math.ceil((new Date(value).getTime() - Date.now()) / 86400000); return days > 0 ? `${days}天剩余` : '已截止' }
const formatDate = (value) => value ? new Date(value).toLocaleDateString('zh-CN') : '未设置'
const destroyCharts = () => { submissionChart?.destroy(); gradeChart?.destroy() }
const buildCharts = async () => {
  await nextTick(); destroyCharts()
  const { submitted, waiting } = submissionSummary.value
  if (submissionCanvas.value) submissionChart = new Chart(submissionCanvas.value, { type: 'doughnut', data: { labels: ['已提交', '待提交'], datasets: [{ data: [submitted, waiting], backgroundColor: ['#6bb58b','#e8edf2'] }] }, options: { responsive: true, maintainAspectRatio: false } })
  if (gradeCanvas.value && hasGradeData.value) { gradeChart = new Chart(gradeCanvas.value, { type: 'bar', data: { labels: ['0-60','60-75','75-90','90-100'], datasets: [{ label: '团队数（教师评分）', data: scoreBuckets(gradedScores.value), backgroundColor: '#6b9ed8' }] }, options: { responsive: true, maintainAspectRatio: false, scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } } }) }
}
const loadDashboard = async () => { loading.value = true; errorMessage.value = ''; try { courses.value = await getCourses(); const courseId = route.params.courseId; assignments.value = await getAssignments(courseId); students.value = await getStudents(courseId); const results = await Promise.all(assignments.value.map((item) => getSubmissions(item.id).catch(() => []))); submissions.value = results.flat(); await buildCharts() } catch (error) { errorMessage.value = getErrorMessage(error, '课程首页加载失败，请稍后重试') } finally { loading.value = false } }
onMounted(loadDashboard); onBeforeUnmount(destroyCharts)
</script>

<style scoped>
.teacher-home{padding:0}.breadcrumb{display:flex;gap:8px;align-items:center;margin-bottom:22px;color:#748294;font-size:14px}.breadcrumb button{padding:0;border:0;color:inherit;background:transparent;cursor:pointer}.breadcrumb strong{color:#263b53}.error-box{margin-bottom:16px;padding:12px 16px;border:1px solid #f4c7c7;border-radius:6px;color:#b42318;background:#fff5f5}.page-header{margin-bottom:24px;text-align:center}.page-header h1{margin:0;color:#263b53;font-size:30px}.page-header p{margin:9px 0 0;color:#8492a4;font-size:18px}.stats-cards{display:grid;grid-template-columns:repeat(3,1fr);gap:20px;margin-bottom:24px}.stat-card{display:flex;align-items:center;justify-content:space-between;padding:22px 24px;border-radius:8px;background:#fff;box-shadow:0 2px 9px rgba(0,0,0,.08)}.stat-card p{margin:0;color:#657487;font-size:16px}.stat-card strong{display:block;margin-top:7px;color:#263b53;font-size:32px}.stat-card i{display:grid;width:54px;height:54px;place-items:center;border-radius:50%;color:#6bb58b;background:#eff8f3;font-style:normal;font-size:22px}.content-grid{display:grid;grid-template-columns:2fr 1fr;gap:20px;margin-bottom:24px}.panel{overflow:hidden;border-radius:8px;background:#fff;box-shadow:0 2px 9px rgba(0,0,0,.08)}.section-header{display:flex;align-items:center;justify-content:space-between;padding:16px 20px;border-bottom:1px solid #edf1f4}.section-header h2{margin:0;color:#263b53;font-size:17px}.section-header span{color:#6bb58b;font-size:13px}.chart-box{position:relative;height:260px;padding:18px 20px}.task-item{display:flex;align-items:center;gap:10px;padding:14px 18px;border-bottom:1px solid #edf1f4}.check{width:14px;height:14px;border:1px solid #aab6c2;border-radius:3px}.task-item div{display:grid;flex:1;gap:5px}.task-item strong{color:#334155;font-size:13px}.task-item small,.task-item em{color:#94a3b8;font-size:11px}.task-item em{font-style:normal}.visualization{margin-bottom:24px}.charts-container{display:grid;grid-template-columns:1fr 1fr;gap:24px;padding:20px}.charts-container h3{margin:0;text-align:center;color:#334155;font-size:15px}@media(max-width:900px){.content-grid,.charts-container{grid-template-columns:1fr}}@media(max-width:600px){.stats-cards{grid-template-columns:1fr}.page-header h1{font-size:25px}}
.stat-card>div{display:flex;align-items:baseline;gap:14px}.stat-card strong{margin-top:0;line-height:1}.progress-list{padding:8px 20px 16px;}
.progress-item{margin:0 -20px;padding:16px 20px;border-bottom:1px solid #edf1f4;cursor:pointer;transition:background .15s}.progress-item:hover{background:#f6faff}.progress-item:focus-visible{outline:2px solid #6b9ed8;outline-offset:-2px;border-radius:6px}.progress-item:last-child{border-bottom:0}.progress-title,.progress-meta{display:flex;align-items:center;justify-content:space-between}.progress-title strong{color:#263b53;font-size:16px}.progress-title span{color:#52708d;font-size:15px}.progress-value{display:inline-flex;align-items:center;gap:6px;flex:0 0 auto}.progress-value i{color:#a8bccf;font-size:17px;font-style:normal;line-height:1;transition:transform .15s,color .15s}.progress-item:hover .progress-title strong{color:#356798}.progress-item:hover .progress-value i{color:#356798;transform:translateX(3px)}.progress-track{height:12px;margin:14px 0 8px;border-radius:999px;background:#eef0f2}.progress-track span{display:block;height:100%;border-radius:999px;background:#6bb58b;transition:width .3s ease}.progress-meta{color:#8492a4;font-size:13px}
</style>
