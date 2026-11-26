<template>
  <div class="grading-page">
    <!-- 批改视图独占整页：列表让位，返回后回到原来的分组列表 -->
    <template v-if="reviewOpen && selected">
      <header class="heading">
        <div>
          <p>CURRENT REVIEW</p>
          <h1>{{ submissionTeamLabel(selected, '团队提交') }}</h1>
          <span>提交于 {{ submitTimeText(selected) }}</span>
        </div>
        <div class="review-nav">
          <button type="button" :disabled="currentIndex <= 0" @click="switchSubmission(-1)">‹ 上一组</button>
          <strong>{{ currentIndex < 0 ? '—' : currentIndex + 1 }} / {{ reviewQueue.length }}</strong>
          <button type="button" :disabled="currentIndex >= reviewQueue.length - 1" @click="switchSubmission(1)">下一组 ›</button>
        </div>
      </header>

      <!-- 三排纵向信息：组员 → 选题题目 → 小组分工，打分统一放到页面底部 -->
      <div class="review-stack">
        <section class="panel info-panel">
          <div class="info-members">
            <span class="info-label">组员</span>
            <div class="member-chips">
              <span
                v-for="member in reviewMembers"
                :key="member.studentId"
                class="member-chip"
                :class="{ 'member-chip--leader': member.role === 'LEADER' }"
              >
                {{ member.name }}<small v-if="member.role === 'LEADER'">组长</small>
              </span>
              <span v-if="membersLoading" class="info-muted">正在加载组员…</span>
              <span v-else-if="!reviewMembers.length" class="info-muted">暂无组员</span>
            </div>
          </div>
          <div class="info-facts">
            <span class="status-chip" :class="`status-chip--${reviewState}`">{{ reviewStateText }}</span>
            <span class="info-fact">共 {{ reviewMembers.length }} 人</span>
            <span class="info-fact">{{ workloadFact }}</span>
          </div>
        </section>

        <section class="panel topic-panel">
          <div class="panel-head">
            <h2>选题题目</h2>
            <span>学生提交时填写的内容</span>
          </div>
          <div class="topic-body">{{ selected.content || selected.note || '该团队没有填写选题题目。' }}</div>
        </section>

        <!--
          提交附件：功能暂时下线（学生端已取消上传入口），后端接口与 submission_attachment 表仍保留。
          恢复时取消本段注释，并启用脚本区的附件函数 / 状态变量、样式区的 .attachment-* 规则。
        <section class="panel attachment-panel">
          <div class="panel-head">
            <h2>提交附件</h2>
            <span>{{ attachmentList.length }} 个文件</span>
          </div>
          <div class="attachment-body">
            <button
              v-for="(file, index) in attachmentList"
              :key="file.id || file.storageKey"
              type="button"
              class="attachment-item"
              :disabled="downloading === file.storageKey"
              @click="downloadAttachment(file, index)"
            >
              <span>{{ attachmentDisplayName(file, index) }}</span>
              <small>{{ downloading === file.storageKey ? '下载中…' : formatSize(file.fileSize) + ' · 下载' }}</small>
            </button>
            <p v-if="downloadError" class="attachment-error">{{ downloadError }}</p>
            <p v-else-if="!attachmentList.length" class="attachment-empty">该团队没有上传附件</p>
          </div>
        </section>
        -->

        <WorkloadAllocationPanel
          v-if="selected.groupId"
          :key="selected.groupId"
          teacher-mode
          :assignment-id="assignmentId"
          :group-id="selected.groupId"
          :parts="parts"
          :members="reviewMembers"
          :deadline="assignment?.deadline"
          :read-only="Boolean(selected.scorePublished ?? selected.isScorePublished)"
          @status="onWorkloadStatus"
        />
      </div>

      <footer class="review-foot">
        <p v-if="scoringHint" class="scoring-hint" :class="`scoring-hint--${scoringHint.tone}`">
          <span>{{ scoringHint.text }}</span>
          <button v-if="scoringHint.tone === 'ok'" type="button" @click="goScores">前往成绩查看 ›</button>
        </p>
        <div class="grade-bar">
          <label class="grade-field">
            <span>团队成绩</span>
            <input v-model.number="gradeForm.groupScore" type="number" min="0" max="100" step="0.5" placeholder="0-100">
            <em>分</em>
          </label>
          <label class="grade-field grade-field--comment">
            <span>教师评语</span>
            <textarea v-model="gradeForm.comment" rows="1" maxlength="2000" placeholder="批改意见（可留空）"></textarea>
          </label>
        </div>
        <div class="foot-actions">
          <span v-if="isDirty" class="dirty-hint">有未保存的修改</span>
          <span class="foot-spacer"></span>
          <button type="button" class="ghost" @click="attemptClose">返回查看作业</button>
          <button type="button" class="primary-save" :disabled="saving" @click="saveGrade">
            {{ saving ? '保存中…' : saveLabel }}
          </button>
        </div>
      </footer>
    </template>

    <template v-else>
      <header class="heading">
        <div>
          <p>SUBMISSION REVIEW</p>
          <h1>{{ assignment?.title || '查看作业' }}</h1>
          <span>查看团队提交、评分结果和评分运行状态</span>
        </div>
        <button type="button" class="back" @click="router.push(`/teacher/course/${courseId}/assignments`)">返回作业列表</button>
      </header>
      <div v-if="errorMessage" class="error-box">{{ errorMessage }}</div>
      <div v-if="loading" class="loading">正在加载提交数据…</div>
      <template v-else>
      <div class="summary">
        <div><span>团队总数</span><strong>{{ teamRows.length }}</strong></div>
        <div><span>已提交</span><strong>{{ submittedRows.length }}</strong></div>
        <div><span>待批改</span><strong>{{ pendingCount }}</strong></div>
        <div><span>未组队人数</span><strong>{{ ungroupedCount }}</strong></div>
      </div>

      <section class="panel">
        <div class="panel-head">
          <h2>组队情况</h2>
          <span v-if="hasFilters">{{ filteredRows.length }} / {{ teamRows.length }} 个团队</span>
          <span v-else>{{ teamRows.length }} 个团队 · {{ submittedRows.length }} 个已提交</span>
        </div>
        <div class="list-toolbar">
          <input v-model="keyword" type="search" placeholder="搜索组号或组长姓名" aria-label="搜索小组" />
          <div class="list-filters">
            <button v-for="item in filters" :key="item.value" type="button" :class="{ active: statusFilter === item.value }" @click="statusFilter = item.value">{{ item.label }}</button>
          </div>
        </div>
        <el-empty v-if="!teamRows.length" description="该作业暂无小组" />
        <el-empty v-else-if="!filteredRows.length" description="没有符合条件的小组" />
        <div v-else class="team-table">
          <div class="team-table-head">
            <span>小组</span>
            <span>成员</span>
            <span>选题题目</span>
            <span class="col-center">状态</span>
            <span class="col-center">操作</span>
          </div>
          <div
            v-for="row in pagedRows"
            :key="row.id"
            class="team-row"
            :class="{ 'team-row--waiting': !row.submission }"
          >
            <strong class="cell-group" :title="row.name">{{ row.name }}</strong>
            <span class="cell-members" :title="memberText(row)">{{ memberText(row) }}</span>
            <span class="cell-topic" :title="topicText(row)">{{ topicText(row) }}</span>
            <span class="cell-status">
              <span class="status-chip" :class="`status-chip--${rowState(row)}`">{{ statusText(row) }}</span>
            </span>
            <span class="cell-action">
              <button
                type="button"
                :disabled="!row.submission"
                :title="row.submission ? '查看该小组的提交与评分' : '该小组尚未提交作业'"
                @click="review(row.submission)"
              >查看</button>
            </span>
          </div>
        </div>
        <div v-if="pageCount > 1" class="list-pager">
          <button type="button" :disabled="page === 1" @click="page -= 1">上一页</button>
          <span>{{ (page - 1) * PAGE_SIZE + 1 }}–{{ Math.min(page * PAGE_SIZE, filteredRows.length) }} / 共 {{ filteredRows.length }} 组</span>
          <button type="button" :disabled="page === pageCount" @click="page += 1">下一页</button>
        </div>
      </section>
      </template>
    </template>

  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElEmpty, ElMessage, ElMessageBox } from 'element-plus'
import WorkloadAllocationPanel from '@/components/workload/WorkloadAllocationPanel.vue'
import { getAssignments, getAssignmentDetail, getGroupMembers, getGroups, getStudents, getSubmissions, gradeSubmission } from '@/api/teacher'
import { getErrorMessage } from '@/api/response'
// 提交附件功能暂时下线：恢复时取消下一行注释（模板注释块、附件函数、.attachment-* 样式一并启用）
// import { attachmentDisplayName, downloadAuthenticatedFile } from '@/utils/downloadFile'
import { groupNumber } from '@/utils/groupSearch'
import { submissionGroupName, submissionTeamLabel } from '@/utils/submission'


const route = useRoute()
const router = useRouter()
const courseId = route.params.courseId
const assignmentId = route.params.assignmentId

const assignment = ref(null)
const groups = ref([])
const submissions = ref([])
const students = ref([])
// 评价分支（含权重）来自作业详情，分工表要用它画出每个分支一行。
const parts = ref([])
const loading = ref(false)
const errorMessage = ref('')
const reviewOpen = ref(false)
const selected = ref(null)
const saving = ref(false)
const gradeForm = ref({ groupScore: null, comment: '' })
// 批改页顶部要展示组员和分工版本，组员复用列表的缓存，分工状态由分工面板回传。
const reviewMembers = ref([])
const membersLoading = ref(false)
const workloadStatus = ref(null)
// 自动评分结果提示：保存团队成绩后告诉老师生成了几名学生的个人成绩，或为什么没生成。
const scoringHint = ref(null)
// 提交附件功能暂时下线：恢复时取消下面两行注释
// const downloading = ref('')
// const downloadError = ref('')

const scoreValue = (item) => item.teacherScore ?? item.teacher_score ?? item.finalScore ?? item.final_score ?? '待批改'

const submitTimeText = (item) => item?.submitTime || item?.submit_time || '未记录'

/* 提交附件功能暂时下线（学生端已取消上传），恢复时取消整段注释：
const parseAttachments = (value) => {
  if (Array.isArray(value)) return value
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  return []
}

const attachmentList = computed(() => parseAttachments(selected.value?.attachments))

const formatSize = (value) => {
  const size = Number(value || 0)
  if (!Number.isFinite(size) || size <= 0) return '大小未知'
  return size < 1048576 ? `${Math.ceil(size / 1024)} KB` : `${(size / 1048576).toFixed(1)} MB`
}

const downloadAttachment = async (file, index) => {
  if (!file?.storageKey) {
    downloadError.value = '附件缺少存储标识，无法下载'
    return
  }
  downloadError.value = ''
  downloading.value = file.storageKey
  try {
    await downloadAuthenticatedFile(file, index)
  } catch (error) {
    downloadError.value = getErrorMessage(error, '文件下载失败')
  } finally {
    downloading.value = ''
  }
}
*/

const reviewState = computed(() => (scoreValue(selected.value) === '待批改' ? 'pending' : 'graded'))
const reviewStateText = computed(() => (reviewState.value === 'pending' ? '待批改' : '已评分'))
const workloadFact = computed(() => {
  const status = workloadStatus.value
  if (!status?.hasVersion) return '分工未填写 · 评分按组内均分'
  return `分工第 ${status.versionNo ?? '—'} 版 · ${status.locked ? '已锁定' : '未锁定'}`
})
const onWorkloadStatus = (status) => { workloadStatus.value = status }

// 打开某一组时补齐成员名单：列表页已经拉过的直接从缓存取。
const loadReviewMembers = async (groupId) => {
  reviewMembers.value = []
  if (groupId == null) return
  const cached = membersByGroup.value[String(groupId)]
  if (Array.isArray(cached)) {
    reviewMembers.value = cached.filter((item) => !item.status || item.status === 'ACTIVE')
    return
  }
  membersLoading.value = true
  try {
    const list = await getGroupMembers(assignmentId, groupId)
    membersByGroup.value[String(groupId)] = Array.isArray(list) ? list : []
    reviewMembers.value = membersByGroup.value[String(groupId)]
      .filter((item) => !item.status || item.status === 'ACTIVE')
  } catch {
    reviewMembers.value = []
  } finally {
    membersLoading.value = false
  }
}

const pendingCount = computed(() => submissions.value.filter((item) => scoreValue(item) === '待批改').length)
// 未组队人数按学生算：还没加入本作业任何有效小组的人；已组队但没交作业的仍算「未提交」。
const ungroupedCount = computed(() => students.value.filter((item) => !Number(item.assignedToGroup)).length)

// 组队情况以小组为准：没提交的小组也要出现在列表里，已提交的排在前面，其余按组号升序。
const teamRows = computed(() => {
  const byGroup = new Map(submissions.value.map((item) => [String(item.groupId), item]))
  const rows = groups.value.map((group) => ({
    id: group.id,
    name: group.name,
    leaderName: group.leaderName,
    memberCount: group.memberCount,
    submission: byGroup.get(String(group.id)) ?? null
  }))
  const known = new Set(rows.map((row) => String(row.id)))
  // 小组被删除但历史提交仍在时，也要能看到这条提交。
  for (const item of submissions.value) {
    if (known.has(String(item.groupId))) continue
    rows.push({
      id: item.groupId,
      name: submissionGroupName(item, '未知小组'),
      leaderName: item.leaderName,
      memberCount: null,
      submission: item
    })
  }
  return rows.sort((left, right) => {
    const submittedDiff = (right.submission ? 1 : 0) - (left.submission ? 1 : 0)
    if (submittedDiff !== 0) return submittedDiff
    return (groupNumber(left) ?? Number.MAX_SAFE_INTEGER) - (groupNumber(right) ?? Number.MAX_SAFE_INTEGER)
  })
})

// 小组接口只返回人数，成员名单按页从成员接口补齐并缓存，避免每行都请求一次。
const membersByGroup = ref({})
const loadingMembers = ref({})
const membersOf = (row) => (membersByGroup.value[String(row.id)] ?? [])
  .filter((item) => !item.status || item.status === 'ACTIVE')
const memberText = (row) => {
  // 成员接口已按「组长在前」排序，这里给组长名字补一个（组长）标记，组号列只留组号。
  const names = membersOf(row)
    .map((item) => {
      if (!item.name) return ''
      return item.role === 'LEADER' ? `${item.name}（组长）` : item.name
    })
    .filter(Boolean)
  if (names.length) return names.join('、')
  if (loadingMembers.value[String(row.id)]) return '加载中…'
  return row.memberCount ? `${row.memberCount} 人` : '—'
}
// 列表里的「选题题目」就是该组最新一次提交的内容（与批改页同一取值口径：content 优先，其次 note）。
// 列表只给一行，长题目截断，完整内容看 title 提示或点「查看」。
const topicText = (row) => {
  const raw = row.submission?.content || row.submission?.note
  const text = String(raw ?? '').replace(/\s+/g, ' ').trim()
  return text || '—'
}
const loadMembers = async (rows) => {
  const targets = rows.filter((row) => row.id != null
    && membersByGroup.value[String(row.id)] === undefined
    && !loadingMembers.value[String(row.id)])
  if (!targets.length) return
  await Promise.all(targets.map(async (row) => {
    loadingMembers.value[String(row.id)] = true
    try {
      const list = await getGroupMembers(assignmentId, row.id)
      membersByGroup.value[String(row.id)] = Array.isArray(list) ? list : []
    } catch {
      membersByGroup.value[String(row.id)] = []
    } finally {
      loadingMembers.value[String(row.id)] = false
    }
  }))
}

const submittedRows = computed(() => teamRows.value.filter((row) => row.submission))

const keyword = ref('')
const statusFilter = ref('all')
const filters = [
  { label: '全部', value: 'all' },
  { label: '已提交', value: 'submitted' },
  { label: '待批改', value: 'pending' },
  { label: '已评分', value: 'graded' },
  { label: '未提交', value: 'waiting' }
]
const rowState = (row) => {
  if (!row.submission) return 'waiting'
  return scoreValue(row.submission) === '待批改' ? 'pending' : 'graded'
}
const statusText = (row) => ({ waiting: '未提交', pending: '待批改', graded: '已评分' }[rowState(row)])
const hasFilters = computed(() => Boolean(keyword.value.trim()) || statusFilter.value !== 'all')
const filteredRows = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return teamRows.value.filter((row) => {
    const state = rowState(row)
    if (statusFilter.value === 'submitted' && !row.submission) return false
    if (statusFilter.value !== 'all' && statusFilter.value !== 'submitted' && state !== statusFilter.value) return false
    if (!query) return true
    const number = groupNumber(row)
    return String(row.name ?? '').toLowerCase().includes(query)
      || String(row.leaderName ?? '').toLowerCase().includes(query)
      || (number != null && String(number).includes(query))
  })
})

// 一屏 10 组，其余翻页；和作业管理、学生端组队列表保持同样的节奏。
const PAGE_SIZE = 10
const page = ref(1)
const pageCount = computed(() => Math.max(1, Math.ceil(filteredRows.value.length / PAGE_SIZE)))
const pagedRows = computed(() => filteredRows.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE))
watch([keyword, statusFilter], () => { page.value = 1 })
watch(pageCount, (count) => { if (page.value > count) page.value = count })
watch(pagedRows, (rows) => { loadMembers(rows) }, { immediate: true })

// 上一组 / 下一组按表格里看到的顺序走：已提交在前、组号升序，未提交的小组不进入批改队列。
const reviewQueue = computed(() => filteredRows.value
  .filter((row) => row.submission)
  .map((row) => row.submission))
const currentIndex = computed(() => reviewQueue.value.findIndex((item) => item.id === selected.value?.id))
const isLastReview = computed(() => currentIndex.value >= 0 && currentIndex.value >= reviewQueue.value.length - 1)
const saveLabel = computed(() => (isLastReview.value ? '保存批改结果' : '保存并下一组'))

const savedScore = (item) => {
  const value = item?.teacherScore ?? item?.teacher_score ?? null
  return value == null || value === '' ? null : Number(value)
}
const savedComment = (item) => String(item?.teacherComment ?? item?.teacher_comment ?? '')
// 改了分数或评语但没保存，切组 / 关闭前都要拦一下。
// 改了分数或评语但没保存，切组 / 关闭前都要拦一下；分工表自己的改动也算在内。
const scoreDirty = computed(() => {
  if (!selected.value) return false
  const currentScore = gradeForm.value.groupScore == null || gradeForm.value.groupScore === ''
    ? null
    : Number(gradeForm.value.groupScore)
  return currentScore !== savedScore(selected.value)
    || String(gradeForm.value.comment ?? '') !== savedComment(selected.value)
})
const isDirty = computed(() => scoreDirty.value)

const fillReview = async (item) => {
  selected.value = item
  scoringHint.value = null
  workloadStatus.value = null
  gradeForm.value = { groupScore: savedScore(item), comment: savedComment(item) }
  await loadReviewMembers(item?.groupId)
}

const confirmDiscard = async () => {
  if (!isDirty.value) return true
  try {
    await ElMessageBox.confirm('当前修改（成绩、评语或分工）尚未保存，继续操作会丢失这些修改。', '有未保存的修改', {
      confirmButtonText: '放弃修改',
      cancelButtonText: '继续编辑',
      type: 'warning'
    })
    return true
  } catch {
    return false
  }
}

// 批改视图与列表在同一个路由里切换，记住列表滚动位置，返回时回到原处。
let listScrollTop = 0

const review = async (item) => {
  if (!item) return
  if (reviewOpen.value && selected.value?.id === item.id) return
  if (!(await confirmDiscard())) return
  if (!reviewOpen.value) listScrollTop = window.scrollY
  await fillReview(item)
  reviewOpen.value = true
  await nextTick()
  window.scrollTo({ top: 0, behavior: 'auto' })
}

const attemptClose = async () => {
  if (!(await confirmDiscard())) return
  reviewOpen.value = false
  await nextTick()
  window.scrollTo({ top: listScrollTop, behavior: 'auto' })
}

const switchSubmission = async (direction) => {
  const index = currentIndex.value
  const next = index < 0
    ? reviewQueue.value[0]
    : reviewQueue.value[index + direction]
  if (next) await review(next)
}

// 保存团队成绩时后端会顺手跑一次算法，这里把结果翻译成一句人话给老师看。
const describeScoring = (payload) => {
  if (!payload) return null
  if (payload.scoringSkipped) {
    return {
      tone: 'warn',
      text: `团队成绩已保存，但个人成绩未生成：${payload.scoringMessage || '评分条件不满足'}。补齐后再次点保存即可重跑`
    }
  }
  const count = Number(payload.memberCount || 0)
  const base = count ? `已为 ${count} 名成员生成个人成绩（待发布）` : '已生成个人成绩（待发布）'
  return {
    tone: 'ok',
    text: payload.contributionSource === 'equal_split'
      ? `${base}；该组尚未保存分工，本次按组内均分计算`
      : base
  }
}
const goScores = () => { router.push({ path: `/teacher/course/${courseId}/scores`, query: { assignmentId } }) }

const saveGrade = async () => {
  if (gradeForm.value.groupScore == null || gradeForm.value.groupScore < 0 || gradeForm.value.groupScore > 100) {
    ElMessage.warning('请输入 0-100 的团队成绩')
    return
  }
  // 分工由学生组长维护，教师端只读，录分时不需要再检查分工是否未保存。
  const wasLast = isLastReview.value
  saving.value = true
  try {
    const payload = await gradeSubmission(selected.value.id, gradeForm.value)
    const hint = describeScoring(payload)
    scoringHint.value = hint
    const index = submissions.value.findIndex((item) => item.id === selected.value.id)
    if (index >= 0) {
      submissions.value[index] = {
        ...submissions.value[index],
        teacherScore: gradeForm.value.groupScore,
        teacherComment: gradeForm.value.comment,
        status: 'GRADED'
      }
      // 指向保存后的记录，否则表单会被判成「未保存的修改」
      selected.value = submissions.value[index]
    }
    // 保存后当前组可能被筛掉（例如停在「待批改」筛选），此时队列第一项就是下一组待批改。
    const nextIndex = currentIndex.value
    const next = nextIndex >= 0
      ? reviewQueue.value[nextIndex + 1] ?? null
      : reviewQueue.value[0] ?? null
    if (hint?.tone === 'warn') {
      ElMessage.warning(hint.text)
    } else if (next && next.id !== selected.value.id) {
      await fillReview(next)
      ElMessage.success(hint ? `已保存，进入下一组 · ${hint.text}` : '已保存，进入下一组')
      await nextTick()
      window.scrollTo({ top: 0, behavior: 'auto' })
    } else if (wasLast) {
      ElMessage.success(hint ? `已是最后一组，本组已保存 · ${hint.text}` : '已是最后一组，本组已保存')
    } else {
      ElMessage.success(hint ? `批改结果已保存 · ${hint.text}` : '批改结果已保存')
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '保存批改结果失败'))
  } finally {
    saving.value = false
  }
}

const load = async () => {
  loading.value = true
  try {
    const [assignments, detail, groupRows, submissionRows, studentRows] = await Promise.all([
      getAssignments(courseId),
      getAssignmentDetail(assignmentId),
      getGroups(assignmentId),
      getSubmissions(assignmentId),
      getStudents(courseId, assignmentId)
    ])
    assignment.value = assignments.find((item) => String(item.id) === String(assignmentId)) || detail
    parts.value = Array.isArray(detail?.parts) ? detail.parts : []
    groups.value = Array.isArray(groupRows) ? groupRows : []
    submissions.value = Array.isArray(submissionRows) ? submissionRows : []
    students.value = Array.isArray(studentRows) ? studentRows : []
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '作业数据加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.review-nav {
  display: flex;
  flex: none;
  align-items: center;
  gap: 8px;
}
.review-nav button {
  padding: 9px 14px;
  border: 1px solid #dbe4ee;
  border-radius: 6px;
  color: #356798;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}
.review-nav button:hover:not(:disabled) {
  border-color: #b9cde8;
  background: #f4f8ff;
}
.review-nav button:disabled {
  opacity: .45;
  cursor: not-allowed;
}
.review-nav strong {
  color: #52657a;
  font-size: 13px;
  white-space: nowrap;
}
.review-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
/* 第一排：组员与提交信息 */
.info-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
  padding: 14px 20px;
}
.info-members {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  min-width: 0;
}
.info-label {
  flex: none;
  color: #8795a6;
  font-size: 12px;
}
.member-chips {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.member-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border: 1px solid #e4ebf4;
  border-radius: 999px;
  color: #40556f;
  background: #fafcfd;
  font-size: 12.5px;
}
.member-chip--leader {
  border-color: #b9d0ea;
  color: #356798;
  background: #eaf2fa;
}
.member-chip small {
  color: #8b99a8;
  font-size: 10.5px;
}
.member-chip--leader small {
  color: #356798;
}
.info-muted {
  color: #98a5b3;
  font-size: 12px;
}
.info-facts {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.info-fact {
  color: #98a5b3;
  font-size: 12px;
}
/* 第二排：选题题目 */
.topic-body {
  min-height: 84px;
  padding: 16px 20px;
  color: #3d4f63;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
}
/* 提交附件功能暂时下线：恢复模板注释块时一并启用
.attachment-body {
  padding: 14px 20px 18px;
}
.attachment-item {
  display: flex;
  width: 100%;
  box-sizing: border-box;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 8px;
  padding: 9px 12px;
  border: 1px solid #dce8f1;
  border-radius: 6px;
  color: #356798;
  background: #fff;
  cursor: pointer;
  text-align: left;
}
.attachment-item:hover {
  background: #f2f7fd;
}
.attachment-item:disabled {
  opacity: .6;
  cursor: wait;
}
.attachment-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.attachment-item small {
  flex: none;
  color: #8b99a8;
}
.attachment-error {
  margin: 8px 0 0;
  color: #b42318;
  font-size: 12px;
}
.attachment-empty {
  margin: 0;
  color: #98a5b3;
  font-size: 12px;
}
*/
.review-foot {
  position: sticky;
  bottom: 0;
  z-index: 5;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 18px;
  padding: 14px 20px;
  border: 1px solid #e5ecf4;
  border-radius: 10px;
  background: rgba(255, 255, 255, .97);
  backdrop-filter: blur(4px);
  box-shadow: 0 -2px 12px rgba(38, 59, 83, .05);
}
.grade-bar {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  flex-wrap: wrap;
}
.grade-field {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #52657a;
  font-size: 12.5px;
}
.grade-field > span {
  flex: none;
  color: #8795a6;
}
.grade-field input {
  width: 88px;
  padding: 8px 10px;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  outline: 0;
  color: #263b53;
  background: #fff;
  font: inherit;
  font-size: 15px;
  font-weight: 500;
}
.grade-field input:focus {
  border-color: #9db7eb;
  box-shadow: 0 0 0 2px #edf3ff;
}
.grade-field em {
  color: #98a5b3;
  font-size: 12px;
  font-style: normal;
}
.grade-field--comment {
  flex: 1 1 240px;
  min-width: 0;
}
.grade-field--comment textarea {
  box-sizing: border-box;
  width: 100%;
  height: 38px;
  min-height: 38px;
  max-height: 96px;
  padding: 9px 11px;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  outline: 0;
  color: #334155;
  background: #fff;
  font: inherit;
  font-size: 13px;
  line-height: 1.5;
  resize: vertical;
}
.grade-field--comment textarea:focus {
  border-color: #9db7eb;
  box-shadow: 0 0 0 2px #edf3ff;
}
.foot-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.foot-spacer {
  flex: 1;
}
.dirty-hint {
  padding: 3px 10px;
  border-radius: 999px;
  color: #a3711a;
  background: #fdf4e3;
  font-size: 12px;
}
.review-foot .ghost {
  padding: 9px 16px;
  border: 1px solid #dbe4ee;
  border-radius: 6px;
  color: #52657a;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}
.review-foot .ghost:hover {
  border-color: #b9cde8;
  background: #f6f9fd;
}
.primary-save {
  min-width: 148px;
  padding: 9px 18px;
  border: 0;
  border-radius: 6px;
  color: #fff;
  background: #356798;
  font-size: 13.5px;
  cursor: pointer;
}
.primary-save:hover:not(:disabled) {
  background: #2c567f;
}
.primary-save:disabled {
  opacity: .6;
  cursor: not-allowed;
}
.content-panel, .grade-panel {
  min-width: 0;
}
.grading-page {
  max-width: 1180px;
  margin: 0 auto;
}
.heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 26px;
}
.heading p {
  margin: 0 0 7px;
  color: #6bb58b;
  font-size: 11px;
  letter-spacing: 1.5px;
}
.heading h1 {
  margin: 0 0 8px;
  color: #263b53;
  font-size: 30px;
}
.heading span {
  color: #8795a6;
  font-size: 14px;
}
.back, .actions button {
  padding: 9px 15px;
  border: 0;
  border-radius: 5px;
  color: #356798;
  background: #eaf2fa;
  cursor: pointer;
}
.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
  margin-bottom: 20px;
}
.summary div {
  min-width: 0;
  padding: 18px 22px;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
}
.summary span {
  display: block;
  color: #8795a6;
  font-size: 13px;
}
.summary strong {
  display: block;
  margin-top: 8px;
  color: #263b53;
  font-size: 28px;
  font-weight: 500;
}
.summary div:nth-child(3) strong { color: #b07818; }
.summary div:nth-child(4) strong { color: #97a3b1; }
.list-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 22px;
  border-bottom: 1px solid #edf1f4;
}
.list-toolbar input {
  flex: 0 0 220px;
  padding: 7px 11px;
  border: 1px solid #e4ebf4;
  border-radius: 6px;
  outline: none;
  color: #334155;
  font: inherit;
  font-size: 12px;
}
.list-toolbar input:focus { border-color: #9db7eb; box-shadow: 0 0 0 2px #edf3ff; }
.list-filters {
  display: flex;
  gap: 6px;
}
.list-filters button {
  padding: 5px 12px;
  border: 1px solid #e4ebf4;
  border-radius: 999px;
  background: #fff;
  color: #718096;
  font-size: 12px;
  cursor: pointer;
}
.list-filters button.active {
  border-color: #b9d0ea;
  color: #356798;
  background: #eaf2fa;
}
.list-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 22px;
  border-top: 1px solid #edf1f4;
  color: #98a5b3;
  font-size: 12px;
}
.list-pager button {
  padding: 6px 13px;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  background: #fff;
  color: #356798;
  font-size: 12px;
  cursor: pointer;
}
.list-pager button:disabled {
  color: #b6c0cc;
  background: #f7f9fb;
  cursor: not-allowed;
}
.panel {
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(38, 59, 83, .05);
}
.panel-head {
  display: flex;
  justify-content: space-between;
  padding: 18px 22px;
  border-bottom: 1px solid #edf1f4;
}
.panel-head h2 {
  margin: 0;
  color: #263b53;
  font-size: 17px;
}
.panel-head span {
  color: #6bb58b;
  font-size: 13px;
}
.team-table {
  display: flex;
  flex-direction: column;
}
.team-table-head,
.team-row {
  display: grid;
  /* 组号列只放「第 N 组」，用固定宽度即可；剩下的空间全给成员和选题题目。 */
  grid-template-columns: 104px minmax(0, 1.5fr) minmax(0, 1.55fr) 82px 72px;
  gap: 14px;
  align-items: center;
  padding: 13px 22px;
}
.team-table-head {
  padding-top: 11px;
  padding-bottom: 11px;
  border-bottom: 1px solid #eef2f6;
  background: #fafcfd;
  color: #8795a6;
  font-size: 12px;
}
.team-row {
  border-bottom: 1px solid #f1f5f8;
}
.team-row:last-child {
  border-bottom: 0;
}
/* 尚未提交的小组排在已提交之后，用更淡的样式区分，但仍可读。 */
.team-row--waiting {
  background: #fbfcfd;
}
.cell-group {
  overflow: hidden;
  color: #334155;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.team-row--waiting .cell-group {
  color: #64748b;
  font-weight: 500;
}
.cell-members {
  display: -webkit-box;
  overflow: hidden;
  color: #5c6b7d;
  font-size: 12.5px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.team-row--waiting .cell-members {
  color: #93a1b1;
}
/* 选题题目最长，单行截断，完整内容看悬停提示。 */
.cell-topic {
  overflow: hidden;
  color: #4a5969;
  font-size: 12.5px;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.team-row--waiting .cell-topic {
  color: #a7b3c0;
}
/* 状态和操作两列的内容居中，表头用同一对齐，否则会出现表头靠左、按钮靠右的错位观感。 */
.cell-status,
.col-center {
  text-align: center;
}
.status-chip {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  white-space: nowrap;
}
.status-chip--graded {
  color: #3f7d5c;
  background: #eaf6ef;
}
.status-chip--pending {
  color: #a3711a;
  background: #fdf4e3;
}
.status-chip--waiting {
  color: #97a3b1;
  background: #f1f4f8;
}
.cell-action {
  text-align: center;
}
.cell-action button {
  padding: 5px 14px;
  border: 1px solid #d7e2ef;
  border-radius: 6px;
  color: #356798;
  background: #fff;
  font-size: 12.5px;
  cursor: pointer;
}
.cell-action button:hover:not(:disabled) {
  border-color: #b9cde8;
  background: #f4f8ff;
}
.cell-action button:disabled {
  border-color: #e6ebf1;
  color: #b3bdc9;
  background: #f7f9fb;
  cursor: not-allowed;
}
.error-box, .loading {
  padding: 14px;
  border-radius: 7px;
  background: #fff5f5;
  color: #b42318;
}
/* 自动生成个人成绩的结果提示：成功为绿色，跳过为琥珀色，成功时可直接跳到成绩查看。 */
.scoring-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 0;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
}
.scoring-hint--ok {
  color: #30745a;
  background: #eef8f2;
}
.scoring-hint--warn {
  color: #96631a;
  background: #fff7e8;
}
.scoring-hint button {
  flex: none;
  padding: 0;
  border: 0;
  color: #356798;
  background: none;
  font-size: 12px;
  white-space: nowrap;
  cursor: pointer;
}
.scoring-hint button:hover {
  text-decoration: underline;
}
@media(max-width:720px) {
  .review-nav {
    justify-content: space-between;
  }
  .review-foot {
    padding: 12px 16px;
  }
  .grade-bar {
    align-items: stretch;
  }
  .grade-field--comment {
    flex: 1 1 100%;
  }
  .foot-actions {
    flex-wrap: wrap;
  }
  .primary-save {
    flex: 1;
  }
  .info-panel {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }
}
@media (max-width: 720px) {
  .heading {
    align-items: stretch;
    flex-direction: column;
    gap: 16px;
  }
  .summary { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
  .summary div { padding: 14px 16px; }
  .summary strong { font-size: 22px; }
  .list-toolbar { flex-direction: column; align-items: stretch; gap: 10px; }
  .list-toolbar input { flex: 1 1 auto; }
  .list-filters { flex-wrap: wrap; }
  .team-table-head, .team-row { grid-template-columns: 84px minmax(0, 1.4fr) 66px 56px; gap: 10px; padding: 12px 16px; }
  /* 窄屏放不下五列，优先保留选题题目，成员名单在「查看」里仍可见。 */
  .team-table-head > span:nth-child(2), .cell-members { display: none; }
  .cell-action button { padding: 5px 10px; }
}
</style>
