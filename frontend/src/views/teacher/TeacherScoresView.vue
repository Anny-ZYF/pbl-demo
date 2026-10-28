<template>
  <div class="scores-page">
    <div class="breadcrumb"><button type="button" @click="router.push(`/teacher/course/${courseId}/workspace`)">课程首页</button><span>›</span><strong>成绩查看</strong></div>
    <header class="heading"><p>SCORE OVERVIEW</p><h1>成绩查看</h1><span>{{ course?.name || '当前课程' }} · 按作业查看学生个人成绩</span></header>
    <div v-if="errorMessage" class="error-box">{{ errorMessage }}</div>
    <div class="assignment-tabs"><button v-for="assignment in assignments" :key="assignment.id" type="button" :class="{ active: selectedId === assignment.id }" @click="selectAssignment(assignment.id)">{{ assignment.title }}</button></div>
    <section v-if="selectedAssignment" class="panel">
      <div class="panel-head"><div><h2>{{ selectedAssignment.title }}</h2><span>先逐人复核，再按小组发布当前评分结果；其他小组与历史运行保持原状</span></div><i :class="published ? 'published' : 'unpublished'">{{ published ? '成绩已发布' : '成绩待复核 / 发布' }}</i></div>
      <div v-if="!loading" class="run-list" aria-label="小组成绩复核与发布">
        <div v-for="run in scoringRuns" :key="run.id" class="run-row">
          <div><strong>{{ run.groupName }}</strong><small>{{ run.memberCount }} 名学生 · {{ statusLabel(run.status) }}</small></div>
          <button v-if="reviewable(run.status)" class="publish" type="button" :disabled="publishingRunId !== null" @click="openReview(run)">逐人复核</button>
          <button v-else-if="run.status === 'APPROVED'" class="publish" type="button" :disabled="publishingRunId !== null" @click="publishScores(run)">{{ publishingRunId === run.id ? '发布中…' : '发布本组成绩' }}</button>
          <span v-else :class="run.status === 'PUBLISHED' ? 'published' : 'unpublished'">{{ statusLabel(run.status) }}</span>
        </div>
        <p v-if="!scoringRuns.length" class="run-note">尚无可复核的评分结果，请先在批改作业中录入团队成绩。</p>
      </div>
      <div class="toolbar"><div class="search"><el-icon><Search /></el-icon><input v-model="keyword" placeholder="搜索学生姓名或学号" /></div><button class="export-button" type="button" :disabled="loading || !scores.length" @click="exportScores">导出 Excel</button></div>
      <div v-if="loading" class="loading">正在加载成绩…</div><el-empty v-else-if="!matchingScores.length" description="暂无成绩记录" />
      <table v-else><thead><tr><th>学生</th><th>团队</th><th>团队成绩</th><th>贡献比例</th><th>个人成绩</th><th>当前结果状态</th></tr></thead><tbody><tr v-for="student in pageScores" :key="student.studentId"><td><strong>{{ student.name || '未命名' }}</strong><small>{{ student.studentNo }}</small></td><td>{{ student.groupName || '未分组' }}</td><td>{{ displayScore(student.groupScore) }}</td><td>{{ displayRatio(student.contributionRatio) }}</td><td class="final-score">{{ displayScore(student.individualFinalScore) }}</td><td>{{ statusLabel(student.runStatus || (student.scorePublished ? 'PUBLISHED' : null)) }}</td></tr></tbody></table>
    </section><el-empty v-else description="当前课程暂无作业" />
    <div v-if="selectedAssignment && pageCount > 1" class="pagination-bar"><button type="button" :disabled="page <= 1" @click="page--">上一页</button><span>第 {{ page }} / {{ pageCount }} 页</span><button type="button" :disabled="page >= pageCount" @click="page++">下一页</button></div>
    <el-dialog v-model="reviewVisible" :title="`${reviewGroupName} · 逐人复核`" width="min(960px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!reviewSaving" :show-close="!reviewSaving" @closed="resetReview">
      <div v-if="reviewLoading" class="loading">正在加载本组评分结果…</div>
      <template v-else>
        <p class="review-note">请核对每名学生的成绩并逐项确认。调整建议分时必须填写理由；保存复核后仍需单独发布。</p>
        <div v-if="reviewError" class="error-box" role="alert">{{ reviewError }}</div>
        <div class="review-table-wrap">
          <table class="review-table"><thead><tr><th>学生</th><th>贡献</th><th>规则分</th><th>模型建议</th><th>教师最终分</th><th>调整理由</th><th>确认</th></tr></thead>
            <tbody><tr v-for="row in reviewRows" :key="row.id">
              <td>{{ row.studentName }}</td><td>{{ displayRatio(row.contribution) }}</td><td>{{ displayScore(row.ruleScore) }}</td><td>{{ displayScore(row.modelScore) }}</td>
              <td><input v-model.number="row.finalScore" type="number" min="0" max="100" step="0.01" :aria-label="`${row.studentName}最终分`" :disabled="reviewSaving" @input="row.confirmed = false" /></td>
              <td><input v-model="row.reason" maxlength="500" :placeholder="scoreChanged(row) ? '调整成绩必须填写理由' : '可填写复核说明'" :aria-label="`${row.studentName}调整理由`" :disabled="reviewSaving" @input="row.confirmed = false" /></td>
              <td><label class="review-confirm"><input v-model="row.confirmed" type="checkbox" :disabled="reviewSaving" :aria-label="`确认${row.studentName}成绩`" />已核对</label></td>
            </tr></tbody>
          </table>
        </div>
        <p v-if="reviewRows.some(row => scoreChanged(row) && !row.reason.trim())" class="review-validation">调整后的成绩需要填写理由。</p>
      </template>
      <template #footer><button class="review-cancel" type="button" :disabled="reviewSaving" @click="reviewVisible = false">取消</button><button class="publish" type="button" :disabled="!canSaveReview" @click="saveReview">{{ reviewSaving ? '保存中…' : '保存本组复核' }}</button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElDialog, ElEmpty, ElIcon, ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getAssignments, getCourses, getTeacherScores, getScoringRun, reviewScoringRun, publishScoringRun } from '@/api/teacher'
import { getErrorMessage } from '@/api/response'
const route = useRoute(); const router = useRouter(); const courseId = route.params.courseId
const courses = ref([]); const assignments = ref([]); const scores = ref([]); const selectedId = ref(null); const keyword = ref(''); const page = ref(1); const pageSize = 8; const loading = ref(false); const publishingRunId = ref(null); const errorMessage = ref('')
const course = computed(() => courses.value.find((item) => String(item.id) === String(courseId))); const selectedAssignment = computed(() => assignments.value.find((item) => String(item.id) === String(selectedId.value))); const matchingScores = computed(() => scores.value.filter((item) => `${item.name || ''} ${item.studentNo || ''}`.toLowerCase().includes(keyword.value.toLowerCase()))); const pageScores = computed(() => matchingScores.value.slice((page.value - 1) * pageSize, page.value * pageSize)); const pageCount = computed(() => Math.max(1, Math.ceil(matchingScores.value.length / pageSize))); const published = computed(() => scores.value.length > 0 && scores.value.every((item) => item.scorePublished))
const reviewVisible = ref(false); const reviewLoading = ref(false); const reviewSaving = ref(false); const reviewError = ref(''); const reviewRunId = ref(null); const reviewGroupName = ref(''); const reviewRows = ref([])
const reviewable = status => ['MODEL_COMPLETE', 'DEGRADED'].includes(status)
const statusLabel = status => ({ MODEL_COMPLETE: '待复核', DEGRADED: '待复核（规则评分）', APPROVED: '已复核，待发布', PUBLISHED: '已发布' }[status] || (status ? '评分处理中' : '尚未评分'))
const scoringRuns = computed(() => {
  const runs = new Map()
  for (const item of scores.value) {
    if (!item.scoringRunId) continue
    if (!runs.has(item.scoringRunId)) runs.set(item.scoringRunId, { id: item.scoringRunId, groupName: item.groupName || '当前小组', status: item.runStatus, memberCount: 0 })
    runs.get(item.scoringRunId).memberCount++
  }
  return [...runs.values()]
})
const scoreChanged = row => Number.isFinite(row.finalScore) && Math.abs(row.finalScore - row.suggestedScore) > 0.005
const canSaveReview = computed(() => !reviewLoading.value && !reviewSaving.value && reviewRows.value.length > 0 && reviewRows.value.every(row => row.confirmed && Number.isFinite(row.finalScore) && row.finalScore >= 0 && row.finalScore <= 100 && (!scoreChanged(row) || row.reason.trim())))
const displayScore = (value) => value == null ? '—' : Number(value).toFixed(2); const displayRatio = (value) => value == null ? '—' : `${(Number(value) * 100).toFixed(1)}%`; watch(keyword, () => { page.value = 1 })
const exportScores = () => {
  const headers = ['学号', '专业', '班级', '姓名', '成绩']
  const escapeCell = value => {
    const text = value == null ? '' : String(value)
    const safeText = /^[\s]*[=+@\-]/.test(text) ? `'${text}` : text
    return `"${safeText.replaceAll('"', '""')}"`
  }
  const rows = [headers, ...scores.value.map(student => [
    student.studentNo,
    student.majorName,
    student.className,
    student.name,
    student.individualFinalScore
  ])]
  const csv = `\uFEFF${rows.map(row => row.map(escapeCell).join(',')).join('\r\n')}`
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  const safeTitle = (selectedAssignment.value?.title || '作业成绩').replace(/[\\\\/:*?"<>|]/g, '_')
  link.href = url
  link.download = `${safeTitle}-成绩.csv`
  document.body.append(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
const selectAssignment = async (id) => {
  selectedId.value = id; keyword.value = ''; page.value = 1; scores.value = []; loading.value = true; errorMessage.value = ''
  try {
    const result = await getTeacherScores(courseId, id)
    if (selectedId.value === id) scores.value = result.map(item => ({ ...item, individualFinalScore: item.individualFinalScore ?? item.finalScore ?? item.suggestedScore ?? item.modelScore ?? item.ruleScore }))
  } catch (error) { if (selectedId.value === id) errorMessage.value = getErrorMessage(error, '成绩加载失败') }
  finally { if (selectedId.value === id) loading.value = false }
}
const resetReview = () => { reviewRunId.value = null; reviewRows.value = []; reviewError.value = '' }
const openReview = async run => {
  reviewRunId.value = run.id; reviewGroupName.value = run.groupName; reviewRows.value = []; reviewError.value = ''; reviewVisible.value = true; reviewLoading.value = true
  try {
    const result = await getScoringRun(run.id)
    if (reviewRunId.value !== run.id || !reviewVisible.value) return
    if (!reviewable(result.status)) { reviewError.value = '当前结果状态已变化，请关闭并刷新成绩页'; return }
    reviewRows.value = result.results.map(row => ({ ...row, suggestedScore: Number(row.finalScore), finalScore: Number(row.finalScore), reason: '', confirmed: false }))
  } catch (error) { reviewError.value = getErrorMessage(error, '评分结果加载失败') }
  finally { if (reviewRunId.value === run.id) reviewLoading.value = false }
}
const saveReview = async () => {
  if (!canSaveReview.value) return
  reviewSaving.value = true; reviewError.value = ''
  try {
    await reviewScoringRun(reviewRunId.value, { decisions: reviewRows.value.map(row => ({ scoreResultId: row.id, finalScore: row.finalScore, reason: row.reason.trim() || null })) })
    reviewVisible.value = false
    ElMessage.success('本组全部成员成绩已复核，可发布本组成绩')
    await selectAssignment(selectedId.value)
  } catch (error) { reviewError.value = getErrorMessage(error, '复核保存失败') }
  finally { reviewSaving.value = false }
}
const publishScores = async run => {
  const assignmentId = selectedId.value
  publishingRunId.value = run.id; errorMessage.value = ''
  try {
    await ElMessageBox.confirm(`仅发布 ${run.groupName} 的本次已复核成绩，发布后该组学生可以查看。`, '发布本组成绩', { confirmButtonText: '确认发布', cancelButtonText: '取消', type: 'warning' })
    await publishScoringRun(run.id)
    ElMessage.success('本组成绩已发布')
    if (selectedId.value === assignmentId) await selectAssignment(assignmentId)
  } catch (error) { if (error !== 'cancel' && error !== 'close') errorMessage.value = getErrorMessage(error, '发布成绩失败') }
  finally { publishingRunId.value = null }
}
onMounted(async () => { try { [courses.value, assignments.value] = await Promise.all([getCourses(), getAssignments(courseId)]); if (assignments.value.length) await selectAssignment(assignments.value[0].id) } catch (error) { errorMessage.value = getErrorMessage(error, '课程成绩加载失败') } })
</script>
<style scoped>
.run-list{padding:0 22px;border-bottom:1px solid #edf1f4}.run-row{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:14px 0;border-bottom:1px solid #edf1f4}.run-row:last-child{border-bottom:0}.run-row strong,.run-row small{display:block}.run-row strong{color:#334155;font-size:14px}.run-row small,.run-note{margin-top:5px;color:#718096;font-size:12px}.review-note{color:#64748b;line-height:1.7}.review-table-wrap{overflow-x:auto}.review-table{min-width:800px}.review-table th,.review-table td{padding:12px 8px}.review-table input[type=number]{width:90px}.review-table input:not([type=checkbox]){box-sizing:border-box;padding:8px;border:1px solid #cbd5e1;border-radius:5px;font:inherit}.review-table input[type=text],.review-table input:not([type]){width:180px}.review-confirm{display:flex;align-items:center;gap:5px;white-space:nowrap}.review-validation{color:#b42318}.review-cancel{margin-right:12px;padding:10px 16px;border:1px solid #cbd5e1;border-radius:6px;background:#fff;color:#64748b;cursor:pointer}.publish:disabled{cursor:not-allowed}
.pagination-bar{display:flex;align-items:center;justify-content:center;gap:16px!important;margin:22px 0 0!important;color:#718096;font-size:14px!important}.pagination-bar button{min-width:76px!important;height:auto!important;padding:9px 14px!important;border:1px solid #dce5ec!important;border-radius:5px!important;color:#52657a!important;background:#fff!important;font-size:14px!important;cursor:pointer}.pagination-bar button:disabled{color:#b5c0ca!important;background:#f5f7f9!important;cursor:not-allowed}
.scores-page{max-width:1180px;margin:0 auto}.breadcrumb{display:flex;gap:8px;margin-bottom:24px;color:#8795a6;font-size:14px}.breadcrumb button{padding:0;border:0;background:none;color:inherit;cursor:pointer}.breadcrumb strong{color:#263b53}.heading{margin-bottom:26px}.heading p{margin:0 0 7px;color:#6bb58b;font-size:11px;letter-spacing:1.5px}.heading h1{margin:0 0 8px;color:#263b53;font-size:30px}.heading span{color:#8795a6;font-size:14px}.assignment-tabs{display:flex;gap:10px;margin-bottom:18px;overflow:auto}.assignment-tabs button{padding:11px 18px;border:1px solid #e2eaf0;border-radius:6px;color:#64748b;background:#fff;white-space:nowrap;cursor:pointer}.assignment-tabs button.active{border-color:#356798;color:#fff;background:#356798}.panel{overflow:hidden;border-radius:8px;background:#fff;box-shadow:0 2px 8px rgba(38,59,83,.05)}.panel-head{display:flex;align-items:center;justify-content:space-between;padding:20px 22px;border-bottom:1px solid #edf1f4}.panel-head h2{margin:0 0 7px;color:#263b53;font-size:19px}.panel-head span{color:#8795a6;font-size:12px}.published,.unpublished{padding:4px 9px;border-radius:99px;font-size:12px;font-style:normal}.published{color:#35845a;background:#edf8f1}.unpublished{color:#9b6b29;background:#fff6e8}.publish{padding:10px 16px;border:0;border-radius:6px;color:#fff;background:#356798;cursor:pointer}.publish:disabled{opacity:.5}.toolbar{display:flex;align-items:center;padding:14px 22px}.search{display:flex;align-items:center;gap:8px;width:260px;color:#9aa8b7}.search input{width:100%;border:0;outline:0;font-size:14px}table{width:100%;border-collapse:collapse;text-align:left}th{padding:13px 22px;color:#8795a6;background:#fafcfd;font-size:12px;font-weight:500}td{padding:15px 22px;border-top:1px solid #edf1f4;color:#52657a;font-size:13px}td strong,td small{display:block}td small{margin-top:4px;color:#98a5b3;font-size:11px}.final-score{color:#356798;font-size:16px;font-weight:600}.pagination-bar{display:flex;align-items:center;justify-content:center;gap:32px;margin:28px 0 0;color:#718096;font-size:28px}.pagination-bar button{min-width:152px;height:77px;padding:0 24px;border:2px solid #dce5ec;border-radius:10px;color:#52657a;background:#fff;font-size:28px;cursor:pointer}.pagination-bar button:disabled{color:#b5c0ca;background:#f8fafc;cursor:not-allowed}.loading{text-align:center;padding:30px;color:#8795a6}.error-box{margin-bottom:16px;padding:13px;border-radius:6px;color:#b42318;background:#fff5f5}@media(max-width:720px){.panel-head{align-items:flex-start;flex-direction:column;gap:14px}.search{width:100%}.panel{overflow-x:auto}table{min-width:700px}}
.toolbar{justify-content:space-between;gap:16px}.export-button{padding:9px 14px;border:1px solid #356798;border-radius:6px;color:#356798;background:#fff;white-space:nowrap;cursor:pointer}.export-button:disabled{border-color:#dce5ec;color:#aab6c2;cursor:not-allowed}
</style>
