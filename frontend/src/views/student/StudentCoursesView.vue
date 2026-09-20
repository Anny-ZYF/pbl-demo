<template>
  <div class="courses-page">
    <section class="courses-hero">
      <div>
        <span class="eyebrow">COURSE CENTER</span>
        <h1>我的课程</h1>
        <p>查看课程安排、作业进度与学习状态。</p>
      </div>
      <div class="hero-summary">
        <div><strong>{{ courses.length }}</strong><span>全部课程</span></div>
        <i></i>
        <div><strong>{{ activeCourseCount }}</strong><span>进行中</span></div>
        <i></i>
        <div><strong>{{ completedAssignmentCount }}</strong><span>已完成作业</span></div>
      </div>
    </section>

    <section class="courses-panel">
      <div class="toolbar">
        <div class="search-box">
          <el-icon><Search /></el-icon>
          <input v-model.trim="keyword" type="search" placeholder="搜索课程名称、课程代码或教师" />
          <button v-if="keyword" type="button" aria-label="清空搜索" @click="keyword = ''"><el-icon><Close /></el-icon></button>
        </div>
        <div class="filters">
          <select v-model="termFilter" aria-label="按学期筛选">
            <option value="all">全部学期</option>
            <option v-for="term in terms" :key="term" :value="term">{{ term }}</option>
          </select>
          <select v-model="statusFilter" aria-label="按状态筛选">
            <option value="all">全部状态</option>
            <option value="active">进行中</option>
            <option value="upcoming">未开始</option>
            <option value="ended">已结束</option>
          </select>
        </div>
      </div>

      <div v-if="loading" class="state-block"><el-icon class="is-loading"><Loading /></el-icon><span>正在加载课程...</span></div>
      <div v-else-if="errorMessage" class="state-block state-error">
        <el-icon><WarningFilled /></el-icon><strong>课程加载失败</strong><span>{{ errorMessage }}</span><button type="button" @click="loadCourses">重新加载</button>
      </div>
      <template v-else>
        <div class="result-line"><span>共找到 <strong>{{ filteredCourses.length }}</strong> 门课程</span><span v-if="termFilter !== 'all'">{{ termFilter }}</span></div>
        <div v-if="filteredCourses.length" class="course-grid">
          <article v-for="(course, index) in filteredCourses" :key="course.id" class="course-card" tabindex="0" @click="openCourse(course.id)" @keydown.enter="openCourse(course.id)">
            <div class="course-cover" :class="`cover-${index % 4}`">
              <div class="cover-top"><span>{{ course.code || 'COURSE' }}</span><b :class="`status-${courseStatus(course).value}`">{{ courseStatus(course).label }}</b></div>
              <strong>{{ course.name?.slice(0, 1) || '课' }}</strong>
              <small>{{ course.term || '学期未设置' }}</small>
            </div>
            <div class="course-body">
              <div class="course-title"><h2>{{ course.name }}</h2><el-icon><ArrowRight /></el-icon></div>
              <div class="course-meta">
                <span><el-icon><User /></el-icon>{{ course.teacherName || '教师未设置' }}</span>
                <span><el-icon><Calendar /></el-icon>{{ formatCourseDates(course) }}</span>
              </div>
              <div class="progress-heading"><span>课程作业进度</span><strong>{{ courseProgress(course.id) }}%</strong></div>
              <div class="progress-track"><i :style="{ width: `${courseProgress(course.id)}%` }"></i></div>
              <div class="course-footer">
                <span>{{ completedCount(course.id) }} / {{ assignmentCount(course.id) }} 项作业已完成</span>
                <button type="button">进入课程</button>
              </div>
            </div>
          </article>
        </div>
        <div v-else class="state-block empty-state">
          <el-icon><Reading /></el-icon><strong>{{ courses.length ? '没有符合条件的课程' : '暂时还没有课程' }}</strong><span>{{ courses.length ? '尝试调整搜索内容或筛选条件。' : '加入课程后，课程信息会显示在这里。' }}</span><button v-if="courses.length" type="button" @click="resetFilters">清除筛选</button>
        </div>
      </template>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Calendar, Close, Loading, Reading, Search, User, WarningFilled } from '@element-plus/icons-vue'
import { getAssignments, getCourses } from '@/api/student'
import { getErrorMessage } from '@/api/response'

const router = useRouter()
const courses = ref([])
const assignmentsByCourse = ref({})
const keyword = ref('')
const termFilter = ref('all')
const statusFilter = ref('all')
const loading = ref(false)
const errorMessage = ref('')

const terms = computed(() => [...new Set(courses.value.map((course) => course.term).filter(Boolean))])
const activeCourseCount = computed(() => courses.value.filter((course) => courseStatus(course).value === 'active').length)
const completedAssignmentCount = computed(() => Object.values(assignmentsByCourse.value).flat().filter(isCompleted).length)
const filteredCourses = computed(() => {
  const search = keyword.value.toLocaleLowerCase('zh-CN')
  return courses.value.filter((course) => {
    const searchable = [course.name, course.code, course.teacherName].filter(Boolean).join(' ').toLocaleLowerCase('zh-CN')
    const matchesKeyword = !search || searchable.includes(search)
    const matchesTerm = termFilter.value === 'all' || course.term === termFilter.value
    const matchesStatus = statusFilter.value === 'all' || courseStatus(course).value === statusFilter.value
    return matchesKeyword && matchesTerm && matchesStatus
  })
})

function parseDate(value) {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

function courseStatus(course) {
  const now = new Date()
  const beginDate = parseDate(course.beginDate)
  const endDate = parseDate(course.endDate)
  if (beginDate && now < beginDate) return { value: 'upcoming', label: '未开始' }
  if (endDate && now > endDate) return { value: 'ended', label: '已结束' }
  return { value: 'active', label: '进行中' }
}

function formatDate(value) {
  const date = parseDate(value)
  return date ? date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }) : ''
}

function formatCourseDates(course) {
  const beginDate = formatDate(course.beginDate)
  const endDate = formatDate(course.endDate)
  if (beginDate && endDate) return `${beginDate} - ${endDate}`
  return beginDate || endDate || '日期未设置'
}

function courseAssignments(courseId) {
  return assignmentsByCourse.value[String(courseId)] || []
}

function isCompleted(assignment) {
  return Boolean(assignment.submitted || assignment.scorePublished)
}

function assignmentCount(courseId) {
  return courseAssignments(courseId).length
}

function completedCount(courseId) {
  return courseAssignments(courseId).filter(isCompleted).length
}

function courseProgress(courseId) {
  const total = assignmentCount(courseId)
  return total ? Math.round((completedCount(courseId) / total) * 100) : 0
}

function openCourse(courseId) {
  router.push(`/student/course/${courseId}`)
}

function resetFilters() {
  keyword.value = ''
  termFilter.value = 'all'
  statusFilter.value = 'all'
}

async function loadCourses() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await getCourses()
    courses.value = Array.isArray(result) ? result : []
    const assignmentResults = await Promise.all(courses.value.map(async (course) => [String(course.id), await getAssignments(course.id)]))
    assignmentsByCourse.value = Object.fromEntries(assignmentResults.map(([courseId, items]) => [courseId, Array.isArray(items) ? items : []]))
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '课程数据加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(loadCourses)
</script>

<style scoped>
.courses-page{width:min(1280px,100%);margin:0 auto;color:#26364a}.courses-hero{display:flex;align-items:center;justify-content:space-between;gap:28px;padding:8px 4px 28px}.eyebrow{color:#397bd7;font-size:11px;font-weight:700;letter-spacing:1.8px}.courses-hero h1{margin:7px 0 8px;color:#1e2e43;font-size:30px;line-height:1.2}.courses-hero p{margin:0;color:#7a899d;font-size:14px}.hero-summary{display:flex;align-items:center;gap:25px;padding:17px 24px;border:1px solid #e4eaf2;border-radius:14px;background:#fff;box-shadow:0 7px 24px rgba(37,66,99,.05)}.hero-summary div{display:grid;gap:2px;text-align:center}.hero-summary strong{color:#2b6fc7;font-size:23px}.hero-summary span{color:#8b98a9;font-size:11px;white-space:nowrap}.hero-summary i{width:1px;height:28px;background:#e8edf3}.courses-panel{padding:24px;border:1px solid #e2e9f1;border-radius:16px;background:#fff;box-shadow:0 8px 28px rgba(37,66,99,.055)}.toolbar{display:flex;justify-content:space-between;gap:16px;margin-bottom:22px}.search-box{display:flex;width:min(480px,100%);height:42px;align-items:center;gap:9px;padding:0 13px;border:1px solid #dfe6ef;border-radius:10px;background:#f9fbfd;color:#8b99aa;transition:border .2s,box-shadow .2s}.search-box:focus-within{border-color:#78a9e9;box-shadow:0 0 0 3px rgba(64,133,222,.1)}.search-box input{width:100%;border:0;outline:0;background:transparent;color:#334155;font:inherit;font-size:13px}.search-box button{display:grid;padding:2px;border:0;background:transparent;color:#9aa6b5;cursor:pointer}.filters{display:flex;gap:10px}.filters select{min-width:126px;height:42px;padding:0 34px 0 13px;border:1px solid #dfe6ef;border-radius:10px;outline:0;background:#fff;color:#526277;font:inherit;font-size:13px;cursor:pointer}.result-line{display:flex;justify-content:space-between;margin-bottom:14px;color:#8a98a9;font-size:12px}.result-line strong{color:#397bd7}.course-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:20px}.course-card{overflow:hidden;border:1px solid #e1e8f0;border-radius:14px;background:#fff;box-shadow:0 5px 16px rgba(42,67,96,.045);cursor:pointer;outline:0;transition:transform .22s,box-shadow .22s,border-color .22s}.course-card:hover,.course-card:focus-visible{transform:translateY(-4px);border-color:#bdd3ee;box-shadow:0 13px 30px rgba(42,90,145,.12)}.course-cover{position:relative;height:132px;padding:16px 18px;overflow:hidden;color:#fff;background:linear-gradient(135deg,#2e78d2,#65a7ed)}.course-cover::after{position:absolute;right:-22px;bottom:-50px;width:145px;height:145px;border:25px solid rgba(255,255,255,.12);border-radius:50%;content:""}.cover-1{background:linear-gradient(135deg,#6b59cf,#9a87e7)}.cover-2{background:linear-gradient(135deg,#238e86,#5bc4b1)}.cover-3{background:linear-gradient(135deg,#d37847,#efa271)}.cover-top{display:flex;position:relative;z-index:1;align-items:center;justify-content:space-between}.cover-top>span{font-size:11px;font-weight:700;letter-spacing:.6px}.cover-top b{padding:4px 8px;border-radius:99px;background:rgba(255,255,255,.18);font-size:10px;font-weight:500}.cover-top .status-ended{background:rgba(40,48,61,.28)}.cover-top .status-upcoming{background:rgba(255,244,190,.22)}.course-cover>strong{display:block;position:relative;z-index:1;margin-top:17px;font-size:38px;font-weight:600;line-height:1}.course-cover>small{display:block;position:relative;z-index:1;margin-top:8px;color:rgba(255,255,255,.82);font-size:11px}.course-body{padding:18px}.course-title{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.course-title h2{min-height:44px;margin:0;color:#26384e;font-size:16px;line-height:1.4}.course-title .el-icon{flex:0 0 auto;margin-top:3px;color:#87a2bf;transition:transform .2s}.course-card:hover .course-title .el-icon{transform:translateX(3px)}.course-meta{display:grid;gap:8px;margin:12px 0 18px;color:#8492a4;font-size:11px}.course-meta span{display:flex;align-items:center;gap:7px}.progress-heading{display:flex;justify-content:space-between;color:#75859a;font-size:11px}.progress-heading strong{color:#397bd7}.progress-track{height:6px;margin-top:8px;overflow:hidden;border-radius:99px;background:#edf2f7}.progress-track i{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,#397bd7,#69a9ec);transition:width .4s}.course-footer{display:flex;align-items:center;justify-content:space-between;margin-top:17px;padding-top:14px;border-top:1px solid #edf1f5}.course-footer span{color:#95a1b0;font-size:10px}.course-footer button,.state-block button{padding:7px 12px;border:0;border-radius:8px;color:#397bd7;background:#edf5ff;font-size:11px;font-weight:600;cursor:pointer}.state-block{display:flex;min-height:330px;align-items:center;justify-content:center;flex-direction:column;gap:10px;color:#8795a8}.state-block>.el-icon{font-size:31px;color:#7da6d5}.state-block strong{color:#42536a;font-size:15px}.state-block span{font-size:12px}.state-error>.el-icon{color:#d88172}.state-error button,.empty-state button{margin-top:5px;padding:9px 18px}.empty-state>.el-icon{font-size:40px}.is-loading{animation:rotating 1.2s linear infinite}@keyframes rotating{to{transform:rotate(360deg)}}@media(max-width:1080px){.course-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:760px){.courses-hero{align-items:flex-start;flex-direction:column}.hero-summary{width:100%;justify-content:space-around;box-sizing:border-box}.toolbar{flex-direction:column}.search-box{box-sizing:border-box}.filters select{width:50%;min-width:0}.course-grid{grid-template-columns:1fr}.courses-panel{padding:16px}.course-cover{height:120px}}@media(max-width:420px){.hero-summary{gap:12px;padding:15px 12px}.filters{flex-direction:column}.filters select{width:100%}}
</style>
