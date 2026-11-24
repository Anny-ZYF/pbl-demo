<template>
  <div class="assignments-page">
    <div class="breadcrumb"><button type="button" @click="router.push(`/teacher/course/${courseId}/workspace`)">课程首页</button><span>›</span><strong>作业管理</strong></div>
    <header class="page-heading"><div><p class="eyebrow">COURSE ASSIGNMENTS</p><h1>作业管理</h1><span>{{ course?.name || '当前课程' }} · 共 {{ assignments.length }} 个作业</span></div><button class="primary-button" type="button" @click="router.push(`/teacher/course/${courseId}/assignments/create`)">＋ 创建作业</button></header>
    <div v-if="errorMessage" class="error-box">{{ errorMessage }}</div>
    <div class="toolbar"><div class="search-box"><el-icon><Search /></el-icon><input v-model="keyword" placeholder="搜索作业名称" /></div><div class="filters"><button v-for="item in filters" :key="item.value" :class="{ active: statusFilter === item.value }" type="button" @click="statusFilter = item.value">{{ item.label }}</button></div></div>
    <div v-if="loading" class="loading-box">正在加载作业数据…</div>
    <div v-else-if="filteredAssignments.length" class="assignment-grid"><article v-for="item in pagedAssignments" :key="item.id" class="assignment-card" @click="openGrading(item)"><div class="card-top"><span class="assignment-type">作业 {{ assignmentIndex(item) }}</span><span class="status" :class="statusClass(item)">{{ statusLabel(item) }}</span></div><h2>{{ item.title || '未命名作业' }}</h2><p class="description">{{ item.description || '暂无作业说明，点击卡片查看作业。' }}</p><div class="deadline"><el-icon><Calendar /></el-icon><span>截止日期</span><strong>{{ formatDate(item.deadline) }}</strong></div><div class="progress-label"><span>提交进度</span><b>{{ progress(item) }}%</b></div><div class="progress-track"><span :style="{ width: `${progress(item)}%` }"></span></div><div class="card-meta"><span>{{ item.submittedGroupCount ?? 0 }} / {{ item.groupCount || 0 }} 组已提交</span><div><button type="button" @click.stop="openGrading(item)">查看作业</button><button type="button" @click.stop="openDetail(item)">编辑作业</button></div></div></article></div>
    <div v-if="filteredAssignments.length && pageCount > 1" class="pagination">
      <button type="button" :disabled="page === 1" @click="page -= 1">上一页</button>
      <span>{{ (page - 1) * PAGE_SIZE + 1 }}–{{ Math.min(page * PAGE_SIZE, filteredAssignments.length) }} / 共 {{ filteredAssignments.length }} 个作业</span>
      <button type="button" :disabled="page === pageCount" @click="page += 1">下一页</button>
    </div>
    <el-empty v-else-if="!filteredAssignments.length" description="暂无符合条件的作业" />
    <AssignmentDetailDialog v-model="detailVisible" :assignment-id="selectedAssignmentId" @saved="load" @deleted="load" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElEmpty, ElIcon } from 'element-plus'
import { Calendar, Search } from '@element-plus/icons-vue'
import { getAssignments, getCourses } from '@/api/teacher'
import { getErrorMessage } from '@/api/response'
import { assignmentProgress as progress } from '@/utils/teacherDashboard'
import AssignmentDetailDialog from '@/components/teacher/AssignmentDetailDialog.vue'
const route = useRoute(); const router = useRouter(); const courseId = route.params.courseId
const assignments = ref([]); const courses = ref([]); const loading = ref(false); const errorMessage = ref(''); const keyword = ref(''); const statusFilter = ref('all'); const detailVisible = ref(false); const selectedAssignmentId = ref(null)
const course = computed(() => courses.value.find((item) => String(item.id) === String(courseId)))
const filters = [{ label: '全部', value: 'all' }, { label: '进行中', value: 'active' }, { label: '已结束', value: 'closed' }]
const filteredAssignments = computed(() => assignments.value.filter((item) => { const matchesKeyword = !keyword.value || (item.title || '').toLowerCase().includes(keyword.value.toLowerCase()); const closed = item.status === 'CLOSED' || (item.deadline && new Date(item.deadline) < new Date()); return matchesKeyword && (statusFilter.value === 'all' || (statusFilter.value === 'closed' ? closed : !closed)) }))
// 一屏最多 4 张作业卡片，其余在卡片区内翻页，避免长列表把工具栏挤走。
const PAGE_SIZE = 4
const page = ref(1)
const pageCount = computed(() => Math.max(1, Math.ceil(filteredAssignments.value.length / PAGE_SIZE)))
const pagedAssignments = computed(() => filteredAssignments.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE))
watch([keyword, statusFilter], () => { page.value = 1 })
watch(pageCount, (count) => { if (page.value > count) page.value = count })
const assignmentIndex = (item) => assignments.value.findIndex((entry) => entry.id === item.id) + 1
const statusLabel = (item) => item.status === 'CLOSED' || (item.deadline && new Date(item.deadline) < new Date()) ? '已结束' : '进行中'
const statusClass = (item) => statusLabel(item) === '已结束' ? 'closed' : 'active'
const formatDate = (value) => value ? new Date(value).toLocaleDateString('zh-CN') : '未设置'
const openDetail = (item) => { selectedAssignmentId.value = item.id; detailVisible.value = true }
// 点击整张卡片进入作业的批改/查看页面。
const openGrading = (item) => { router.push(`/teacher/course/${courseId}/assignments/${item.id}/grading`) }
const load = async () => { loading.value = true; try { [courses.value, assignments.value] = await Promise.all([getCourses(), getAssignments(courseId)]) } catch (error) { errorMessage.value = getErrorMessage(error, '作业数据加载失败，请稍后重试') } finally { loading.value = false } }
onMounted(load)
</script>

<style scoped>
.assignments-page{max-width:1180px;margin:0 auto}.breadcrumb{display:flex;gap:8px;align-items:center;margin-bottom:24px;color:#8795a6;font-size:14px}.breadcrumb button{padding:0;border:0;background:none;color:inherit;cursor:pointer}.breadcrumb strong{color:#263b53}.page-heading{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:28px}.eyebrow{margin:0 0 7px;color:#6bb58b;font-size:11px;letter-spacing:1.5px}.page-heading h1{margin:0 0 8px;color:#263b53;font-size:30px}.page-heading span{color:#8a98a8;font-size:14px}.primary-button{padding:11px 19px;border:0;border-radius:6px;color:#fff;background:#356798;font-size:14px;cursor:pointer}.toolbar{display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;padding:14px 18px;border:1px solid #e8edf2;border-radius:8px;background:#fff}.search-box{display:flex;align-items:center;gap:8px;width:250px;color:#9aa8b7}.search-box input{width:100%;border:0;outline:0;color:#334155;font-size:14px}.filters{display:flex;gap:8px}.filters button{padding:7px 16px;border:0;border-radius:5px;color:#718096;background:transparent;cursor:pointer}.filters button.active{color:#356798;background:#eaf2fa}.assignment-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:20px}.assignment-card{min-width:0;padding:22px;overflow:hidden;border:1px solid #e8edf2;border-radius:9px;background:#fff;box-shadow:0 2px 8px rgba(38,59,83,.05);cursor:pointer;transition:border-color .15s,box-shadow .15s}.assignment-card:hover{border-color:#cddbea;box-shadow:0 6px 18px rgba(38,59,83,.09)}.card-top,.detail-header,.progress-label,.card-meta,.detail-row{display:flex;align-items:center;justify-content:space-between}.assignment-type{color:#6b9ed8;font-size:12px}.status{padding:4px 10px;border-radius:999px;font-size:12px}.status.active{color:#35845a;background:#edf8f1}.status.closed{color:#8290a0;background:#f1f3f5}.assignment-card h2{display:-webkit-box;min-height:48px;margin:18px 0 8px;overflow:hidden;color:#263b53;font-size:20px;overflow-wrap:anywhere;-webkit-box-orient:vertical;-webkit-line-clamp:2}.description{display:-webkit-box;height:40px;margin:0 0 18px;overflow:hidden;color:#8795a6;font-size:13px;line-height:20px;overflow-wrap:anywhere;-webkit-box-orient:vertical;-webkit-line-clamp:2}.deadline{display:flex;align-items:center;gap:8px;padding:12px 0;border-top:1px solid #edf1f4;color:#8795a6;font-size:12px}.deadline strong{margin-left:auto;color:#4c6075;font-weight:500}.progress-label{margin-bottom:8px;color:#8795a6;font-size:12px}.progress-label b{color:#52708d;font-weight:500}.progress-track{height:8px;border-radius:99px;background:#eef0f2}.progress-track span{display:block;height:100%;border-radius:inherit;background:#6bb58b}.card-meta{margin-top:16px;color:#98a5b3;font-size:12px}.card-meta button{border:0;background:transparent;color:#356798;cursor:pointer}.error-box,.loading-box{padding:14px;border-radius:7px;background:#fff}.error-box{color:#b42318;background:#fff5f5}.detail-header{margin-bottom:16px}.detail-header+h2{margin:0 0 12px;color:#263b53;overflow-wrap:anywhere}.detail-copy{max-height:240px;overflow:auto;color:#718096;line-height:1.7;overflow-wrap:anywhere;white-space:pre-wrap}.detail-row{padding:13px 0;border-top:1px solid #edf1f4;color:#8492a4;font-size:14px}.detail-row strong{color:#334155;font-weight:500}@media(max-width:720px){.page-heading,.toolbar{align-items:stretch;flex-direction:column;gap:16px}.assignment-grid{grid-template-columns:1fr}.search-box{width:100%}}.pagination{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:22px;padding:12px 18px;border:1px solid #e8edf2;border-radius:8px;background:#fff;color:#8795a6;font-size:13px}.pagination span{flex:1;text-align:center}.pagination button{padding:7px 16px;border:1px solid #dbe3ea;border-radius:6px;color:#356798;background:#fff;font-size:13px;cursor:pointer}.pagination button:disabled{color:#b6c0cc;background:#f7f9fb;cursor:not-allowed}
</style>
