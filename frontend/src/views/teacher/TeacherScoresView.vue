<template>
  <div class="scores-page">
    <div class="breadcrumb"><button type="button" @click="router.push(`/teacher/course/${courseId}/workspace`)">课程首页</button><span>›</span><strong>成绩查看</strong></div>
    <header class="heading">
      <div>
        <p>SCORE OVERVIEW</p>
        <h1>成绩查看</h1>
        <span>{{ course?.name || '当前课程' }} · 按作业查看学生个人成绩</span>
      </div>
      <button class="ghost-button" type="button" @click="appealsVisible = true">成绩申诉</button>
    </header>
    <ScoreAppealsDialog v-model="appealsVisible" :course-id="courseId" @resolved="refreshSelectedAssignment" />

    <div class="toolbar-bar">
      <span class="field-label">作业</span>
      <el-select v-model="selectedId" class="assignment-select" filterable placeholder="选择作业" @change="selectAssignment">
        <el-option v-for="assignment in assignments" :key="assignment.id" :label="assignment.title" :value="assignment.id" />
      </el-select>
      <span v-if="pendingGroupCount" class="chip chip--pending">待发布 {{ pendingGroupCount }} 组</span>
      <span v-if="publishedGroupCount" class="chip chip--published">已发布 {{ publishedGroupCount }} 组</span>
      <span class="bar-spacer"></span>
      <button class="export-button" type="button" :disabled="loading || !scores.length" @click="exportScores">导出 Excel</button>
      <button class="publish-main" type="button" :disabled="!pendingGroupCount || publishing" @click="publishAll">{{ publishLabel }}</button>
    </div>

    <div v-if="errorMessage" class="error-box">{{ errorMessage }}</div>

    <section v-if="selectedAssignment" class="panel">
      <div class="panel-head">
        <div>
          <h2>{{ selectedAssignment.title }}</h2>
          <span>{{ groups.length }} 个小组 · {{ scores.length }} 名学生</span>
        </div>
        <span class="head-hint">发布即采用当前算法分，发布后学生可见</span>
      </div>

      <div class="toolbar">
        <div class="search"><el-icon><Search /></el-icon><input v-model="keyword" placeholder="搜索学生姓名或学号" /></div>
        <span v-if="keyword" class="filter-hint">{{ filteredGroups.length }} / {{ groups.length }} 个小组</span>
      </div>

      <div v-if="loading" class="loading">正在加载成绩…</div>
      <el-empty v-else-if="!filteredGroups.length" :description="keyword ? '没有匹配的学生' : '该作业暂无成绩记录'" />
      <table v-else>
        <thead><tr><th>学生</th><th>团队成绩</th><th>贡献比例</th><th>个人成绩</th></tr></thead>
        <tbody v-for="group in pageGroups" :key="group.key">
          <tr class="group-row">
            <td colspan="4">
              <span class="group-label">{{ group.label }}</span>
              <span v-if="group.leaderName" class="group-leader">组长 {{ group.leaderName }}</span>
              <span class="group-meta">{{ group.members.length }} 人<template v-if="group.groupScore != null"> · 团队 {{ displayScore(group.groupScore) }} 分</template></span>
              <span class="group-status" :class="`group-status--${groupStatusTone(group)}`">{{ groupStatusLabel(group) }}</span>
            </td>
          </tr>
          <tr v-for="student in group.members" :key="student.studentId">
            <td><strong>{{ student.name || '未命名' }}</strong><small>{{ student.studentNo }}</small></td>
            <td>{{ displayScore(student.groupScore) }}</td>
            <td>{{ displayRatio(student.contributionRatio) }}</td>
            <td class="final-score">{{ displayScore(student.individualFinalScore) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
    <el-empty v-else description="当前课程暂无作业" />

    <div v-if="selectedAssignment && groupPageCount > 1" class="pagination-bar">
      <button type="button" :disabled="page <= 1" @click="page--">上一页</button>
      <span>第 {{ page }} / {{ groupPageCount }} 页</span>
      <button type="button" :disabled="page >= groupPageCount" @click="page++">下一页</button>
    </div>
  </div>
</template>
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElEmpty, ElIcon, ElMessage, ElMessageBox, ElOption, ElSelect } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import ScoreAppealsDialog from '@/components/teacher/ScoreAppealsDialog.vue'
import { getAssignments, getCourses, getTeacherScores, publishScoringRun } from '@/api/teacher'
import { getErrorMessage } from '@/api/response'
import { groupNumber } from '@/utils/groupSearch'

const route = useRoute(); const router = useRouter(); const courseId = route.params.courseId
const courses = ref([]); const assignments = ref([]); const scores = ref([]); const selectedId = ref(null)
const keyword = ref(''); const page = ref(1); const loading = ref(false); const publishing = ref(false)
const publishProgress = ref(''); const errorMessage = ref(''); const appealsVisible = ref(false)
// 成绩按小组分段展示，一页固定几个组，避免把同一个组切到两页。
const GROUP_PAGE_SIZE = 4

const course = computed(() => courses.value.find((item) => String(item.id) === String(courseId)))
const selectedAssignment = computed(() => assignments.value.find((item) => String(item.id) === String(selectedId.value)))
const displayScore = (value) => value == null ? '—' : Number(value).toFixed(2)
const displayRatio = (value) => value == null ? '—' : `${(Number(value) * 100).toFixed(1)}%`

const groupLabel = (name) => {
  const no = groupNumber({ name })
  return no == null ? name : `第 ${no} 组`
}

// 组内按贡献比例从高到低，组间按组号升序，未分组永远排在最后。
const groups = computed(() => {
  const map = new Map()
  for (const row of scores.value) {
    const name = row.groupName || ''
    const key = name || '__ungrouped__'
    let group = map.get(key)
    if (!group) {
      group = {
        key,
        label: name ? groupLabel(name) : '未分组',
        number: name ? groupNumber({ name }) : null,
        ungrouped: !name,
        members: [],
        leaderName: '',
        runId: null,
        runStatus: null,
        groupScore: null
      }
      map.set(key, group)
    }
    group.members.push(row)
    // 组头显示组长姓名；组长被移出小组后就没有这个字段，组头只显示人数。
    if (Number(row.isLeader) === 1) group.leaderName = row.name || row.studentNo || ''
    if (row.scoringRunId && !group.runId) {
      group.runId = row.scoringRunId
      group.runStatus = row.runStatus
      group.groupScore = row.groupScore
    }
  }
  for (const group of map.values()) {
    group.members.sort((a, b) => (Number(b.contributionRatio ?? 0) - Number(a.contributionRatio ?? 0))
      || String(a.studentNo || '').localeCompare(String(b.studentNo || '')))
  }
  return [...map.values()].sort((a, b) => {
    if (a.ungrouped !== b.ungrouped) return a.ungrouped ? 1 : -1
    if (a.number !== b.number) return (a.number ?? Number.MAX_SAFE_INTEGER) - (b.number ?? Number.MAX_SAFE_INTEGER)
    return a.label.localeCompare(b.label, 'zh-CN')
  })
})

const filteredGroups = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return groups.value
  return groups.value
    .map((group) => ({ ...group, members: group.members.filter((student) => `${student.name || ''} ${student.studentNo || ''}`.toLowerCase().includes(query)) }))
    .filter((group) => group.members.length > 0)
})
const groupPageCount = computed(() => Math.max(1, Math.ceil(filteredGroups.value.length / GROUP_PAGE_SIZE)))
const pageGroups = computed(() => filteredGroups.value.slice((page.value - 1) * GROUP_PAGE_SIZE, page.value * GROUP_PAGE_SIZE))
const PUBLISHABLE_RUN_STATUS = ['MODEL_COMPLETE', 'DEGRADED', 'APPROVED']
const pendingGroups = computed(() => groups.value.filter((group) => PUBLISHABLE_RUN_STATUS.includes(group.runStatus)))
const pendingGroupCount = computed(() => pendingGroups.value.length)
const publishedGroupCount = computed(() => groups.value.filter((group) => group.runStatus === 'PUBLISHED').length)
const publishLabel = computed(() => {
  if (publishing.value) return publishProgress.value || '发布中…'
  return pendingGroupCount.value ? `发布成绩（${pendingGroupCount.value} 组）` : '成绩已全部发布'
})

const runStatusLabel = (status) => ({
  MODEL_COMPLETE: '待发布',
  DEGRADED: '待发布（规则评分）',
  APPROVED: '待发布',
  PUBLISHED: '已发布',
  PENDING_REVIEW: '评分处理中',
  FAILED: '评分失败'
}[status] || (status ? '评分处理中' : '未评分'))
const groupStatusLabel = (group) => runStatusLabel(group.runStatus || (group.members.every((item) => item.scorePublished) ? 'PUBLISHED' : null))
const groupStatusTone = (group) => {
  const label = groupStatusLabel(group)
  if (label === '已发布') return 'published'
  if (group.runId) return 'pending'
  return 'none'
}

watch([keyword, selectedId], () => { page.value = 1 })
watch(groupPageCount, (count) => { if (page.value > count) page.value = count })

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
  if (id == null) return
  selectedId.value = id; keyword.value = ''; page.value = 1; scores.value = []; loading.value = true; errorMessage.value = ''
  try {
    const result = await getTeacherScores(courseId, id)
    if (selectedId.value === id) scores.value = result.map(item => ({ ...item, individualFinalScore: item.individualFinalScore ?? item.finalScore ?? item.suggestedScore ?? item.modelScore ?? item.ruleScore }))
  } catch (error) { if (selectedId.value === id) errorMessage.value = getErrorMessage(error, '成绩加载失败') }
  finally { if (selectedId.value === id) loading.value = false }
}

// 逐人复核已取消：一次发布本次作业所有待发布的小组，每组单独一个请求，
// 某个组失败不影响其他组，最后汇总告诉老师哪些组没发出去。
const publishAll = async () => {
  const list = [...pendingGroups.value]
  if (!list.length) return
  const studentCount = list.reduce((sum, group) => sum + group.members.length, 0)
  try {
    await ElMessageBox.confirm(
      `将发布 ${list.length} 个小组、共 ${studentCount} 名学生的成绩。发布采用当前算法分，发布后学生即可查看；如需个别调整请走「成绩申诉」。`,
      '发布成绩',
      { confirmButtonText: '确认发布', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }
  publishing.value = true
  const failed = []
  let done = 0
  try {
    for (const group of list) {
      publishProgress.value = `发布中 ${done + 1}/${list.length}`
      try {
        await publishScoringRun(group.runId)
        done++
      } catch (error) {
        failed.push(`${group.label}（${getErrorMessage(error, '发布失败')}）`)
      }
    }
  } finally {
    publishing.value = false; publishProgress.value = ''
  }
  await selectAssignment(selectedId.value)
  if (failed.length) ElMessage.warning(`已发布 ${done} 个小组，${failed.length} 个未发布：${failed.slice(0, 2).join('、')}${failed.length > 2 ? ' 等' : ''}`)
  else ElMessage.success(`已发布 ${done} 个小组的成绩`)
}

const refreshSelectedAssignment = () => { if (selectedId.value != null) return selectAssignment(selectedId.value) }

onMounted(async () => {
  try {
    [courses.value, assignments.value] = await Promise.all([getCourses(), getAssignments(courseId)])
    // 从「查看作业」的「前往成绩查看」跳过来时带上 assignmentId，直接落在对应作业上。
    const wanted = route.query.assignmentId
    const target = assignments.value.find((item) => String(item.id) === String(wanted)) || assignments.value[0]
    if (target) await selectAssignment(target.id)
  } catch (error) { errorMessage.value = getErrorMessage(error, '课程成绩加载失败') }
})
</script>
<style scoped>
.scores-page{max-width:1180px;margin:0 auto}
.breadcrumb{display:flex;gap:8px;margin-bottom:24px;color:#8795a6;font-size:14px}
.breadcrumb button{padding:0;border:0;background:none;color:inherit;cursor:pointer}
.breadcrumb strong{color:#263b53}
.heading{display:flex;align-items:flex-end;justify-content:space-between;gap:16px;margin-bottom:18px}
.heading p{margin:0 0 7px;color:#6bb58b;font-size:11px;letter-spacing:1.5px}
.heading h1{margin:0 0 8px;color:#263b53;font-size:30px}
.heading span{color:#8795a6;font-size:14px}
.ghost-button{padding:8px 14px;border:1px solid #dce5ec;border-radius:6px;color:#52657a;background:#fff;font-size:13px;cursor:pointer}
.ghost-button:hover{border-color:#b9cde8;color:#356798}

.toolbar-bar{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-bottom:16px;padding:12px 16px;border:1px solid #e8edf2;border-radius:8px;background:#fff}
.field-label{color:#8795a6;font-size:13px}
.assignment-select{width:280px}
.chip{padding:3px 10px;border-radius:99px;font-size:12px}
.chip--pending{color:#9b6b29;background:#fff6e8}
.chip--published{color:#35845a;background:#edf8f1}
.bar-spacer{flex:1}
.export-button{padding:9px 14px;border:1px solid #356798;border-radius:6px;color:#356798;background:#fff;white-space:nowrap;cursor:pointer}
.export-button:disabled{border-color:#dce5ec;color:#aab6c2;cursor:not-allowed}
.publish-main{padding:9px 18px;border:0;border-radius:6px;color:#fff;background:#356798;white-space:nowrap;cursor:pointer}
.publish-main:hover:not(:disabled){background:#2c577f}
.publish-main:disabled{background:#c3d2e2;cursor:not-allowed}

.panel{overflow:hidden;border-radius:8px;background:#fff;box-shadow:0 2px 8px rgba(38,59,83,.05)}
.panel-head{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:18px 22px;border-bottom:1px solid #edf1f4}
.panel-head h2{margin:0 0 6px;color:#263b53;font-size:19px}
.panel-head span{color:#8795a6;font-size:12px}
.head-hint{color:#98a5b3;font-size:12px}
.toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:14px 22px}
.search{display:flex;align-items:center;gap:8px;width:280px;color:#9aa8b7}
.search input{width:100%;border:0;outline:0;color:#334155;font-size:14px}
.filter-hint{color:#98a5b3;font-size:12px}

table{width:100%;border-collapse:collapse;text-align:left}
th{padding:13px 22px;color:#8795a6;background:#fafcfd;font-size:12px;font-weight:500;border-top:1px solid #edf1f4}
td{padding:13px 22px;border-top:1px solid #edf1f4;color:#52657a;font-size:13px}
td strong,td small{display:block}
td small{margin-top:4px;color:#98a5b3;font-size:11px}
.final-score{color:#356798;font-size:15px;font-weight:600}
.group-row td{padding:9px 22px;background:#f5f9fd;border-top:1px solid #e3edf6}
.group-label{margin-right:10px;color:#2f5c85;font-size:13px;font-weight:600}
.group-leader{margin-right:10px;padding:2px 8px;border-radius:99px;color:#2f5c85;background:#e6eff8;font-size:11px}
.group-meta{margin-right:10px;color:#8b9aab;font-size:12px}
.group-status{padding:2px 8px;border-radius:99px;font-size:11px}
.group-status--published{color:#35845a;background:#e8f6ee}
.group-status--pending{color:#9b6b29;background:#fff2df}
.group-status--none{color:#8b9aab;background:#f0f3f6}

.pagination-bar{display:flex;align-items:center;justify-content:center;gap:16px;margin:22px 0 0;color:#718096;font-size:14px}
.pagination-bar button{min-width:76px;padding:9px 14px;border:1px solid #dce5ec;border-radius:5px;color:#52657a;background:#fff;cursor:pointer}
.pagination-bar button:disabled{color:#b5c0ca;background:#f5f7f9;cursor:not-allowed}
@media(max-width:720px){
  .assignment-select{width:100%}
  .bar-spacer{display:none}
  .panel-head{align-items:flex-start;flex-direction:column;gap:8px}
}
</style>
