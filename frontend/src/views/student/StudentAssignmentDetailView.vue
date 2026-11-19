<template>
  <div class="page">
    <button class="back" type="button" @click="router.back()">← 返回作业列表</button>
    <div v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</div>

    <template v-if="detail">
      <header class="detail-head">
        <div>
          <div class="title-row">
            <h1>{{ detail.title }}</h1>
            <span class="status-tag" :class="statusClass">{{ statusText }}</span>
          </div>
          <p class="meta">
            截止时间 {{ formatDate(detail.deadline) }}　·　{{ assignmentType }}　·　{{ detail.groupName || '未加入小组' }}
          </p>
        </div>
        <div class="countdown">
          <small>{{ countdownTitle }}</small>
          <b>{{ countdownText }}</b>
        </div>
      </header>

      <section v-if="!reviewed" class="deadline-banner" :class="deadlineBannerClass">
        <span>{{ countdownDescription }}</span>
      </section>

      <nav class="steps" aria-label="作业进度">
        <button
          v-for="step in steps"
          :key="step.key"
          type="button"
          class="step"
          :class="{ 'step--active': activeStep === step.key }"
          @click="goTo(step)"
        >
          <span class="step-index">{{ step.index }}</span>
          <span class="step-text">
            <b>{{ step.label }}</b>
            <small>{{ step.hint }}</small>
          </span>
        </button>
      </nav>

      <main class="layout">
        <div class="primary">
          <section id="section-brief" class="card">
            <h2>作业要求</h2>
            <p class="description">{{ detail.description || '暂无详细作业说明' }}</p>
            <div v-if="detail.parts?.length" class="rubric">
              <h3>评分标准</h3>
              <ul class="rubric-list">
                <li v-for="part in detail.parts" :key="part.id">
                  <div class="rubric-top">
                    <span>{{ part.name }}</span>
                    <b>{{ pct(part.weight) }}</b>
                  </div>
                  <small v-if="part.description">{{ part.description }}</small>
                  <i><em :style="{ width: `${Number(part.weight) * 100}%` }"></em></i>
                </li>
              </ul>
            </div>
            <p v-else class="muted">教师尚未设置评价分支</p>
          </section>

          <div v-if="detail.groupId && detail.groupName" id="section-workload">
            <WorkloadAllocationPanel
              :assignment-id="route.params.assignmentId"
              :group-id="detail.groupId"
              :parts="detail.parts || []"
              :members="detail.members || []"
              :is-leader="isLeader"
              :read-only="workloadReadOnly"
              :current-student-id="currentStudentId"
              @status="onWorkloadStatus"
              @changed="load"
            />
          </div>

          <div id="section-submit">
            <section v-if="!reviewed && submissionOpen" class="card grouping">
              <div class="grouping-head">
                <div>
                  <h2>我的小组</h2>
                </div>
                <span>提交截止：{{ formatDate(submissionEnd) }}</span>
              </div>
              <template v-if="detail.groupName">
                <div class="current-group">
                  <strong>{{ detail.groupName }}</strong>
                  <small>{{ detail.members?.length || 0 }} / {{ Number(detail.standardGroupSize || 1) + 1 }} 人</small>
                </div>
                <div class="member-list">
                  <span v-for="member in detail.members" :key="member.studentId">
                    {{ member.name }}（{{ member.studentNo }}）{{ member.role === 'LEADER' ? ' · 组长' : '' }}
                  </span>
                </div>
                <button class="danger-button" type="button" :disabled="groupOperating" @click="leaveGroup">退出当前小组</button>
              </template>
              <template v-else>
                <div class="group-actions">
                  <button class="primary-button" type="button" :disabled="groupOperating" @click="createGroup">创建小组</button>
                  <span>或加入已有小组</span>
                </div>
                <div v-if="groups.length" class="available-groups">
                  <article v-for="group in groups" :key="group.id">
                    <div>
                      <strong>{{ group.name }}</strong>
                      <small>{{ group.memberCount }} / {{ group.maxMembers }} 人</small>
                    </div>
                    <button
                      type="button"
                      :disabled="groupOperating || Number(group.memberCount) >= Number(group.maxMembers)"
                      @click="joinGroup(group.groupNo)"
                    >
                      {{ Number(group.memberCount) >= Number(group.maxMembers) ? '已满' : '加入' }}
                    </button>
                  </article>
                </div>
                <el-empty v-else description="暂无可加入的小组，可创建新小组" />
              </template>
            </section>

            <StudentSubmissionPanel
              v-if="!reviewed && submissionOpen && detail.groupName"
              :assignment-id="route.params.assignmentId"
              :initial-content="detail.content"
              :initial-note="detail.submitNote"
              :submitted="submitted"
              :submission-status="detail.submissionStatus"
              :submitter-name="submitterName"
              :graded="graded"
              :attachments="detail.attachments"
              @submitted="load"
              @changed="load"
            />

            <section v-if="!reviewed && !submissionOpen" class="card locked-group">
              <h2>作业已截止</h2>
              <p>截止时间 {{ formatDate(submissionEnd) }} 已过，小组、工作量分配与作业提交均已锁定；未全员确认的分工版本已由系统按截止时间自动锁定。</p>
              <template v-if="submitted || detail.attachments?.length">
                <p class="group-shared-hint">本组共用一份提交，附件对全组成员可见、可下载。</p>
                <p>{{ detail.submitNote || detail.content || '本次提交暂无说明' }}</p>
                <button v-for="(file, index) in detail.attachments || []" :key="`closed-${file.id || file.storageKey}`" type="button" @click="download(file, index)">
                  <span>{{ attachmentDisplayName(file, index) }}</span><small>下载</small>
                </button>
              </template>
            </section>

            <section v-if="reviewed" class="card readonly">
              <h2>已提交内容</h2>
              <b>✓ 成绩已发布</b>
              <p class="group-shared-hint">本组共用一份提交，附件对全组成员可见、可下载。</p>
              <p>{{ detail.submitNote || detail.content || '本次提交暂无说明' }}</p>
              <button v-for="(file, index) in detail.attachments || []" :key="file.id || file.storageKey" type="button" @click="download(file, index)">
                <span>{{ attachmentDisplayName(file, index) }}</span><small>下载</small>
              </button>
            </section>
          </div>

          <section v-if="graded" class="card grade">
            <h2>教师反馈</h2>
            <div v-if="reviewed" class="grade-scores">
              <span>个人成绩 <b>{{ score(currentScore?.finalScore) }}</b></span>
              <span>团队成绩 <b>{{ score(currentScore?.groupScore) }}</b></span>
              <button type="button" @click="appealVisible = true">我要申诉</button>
            </div>
            <p>{{ detail.teacherComment || (reviewed ? '教师暂未填写评语' : '成绩尚未发布') }}</p>
          </section>
        </div>

        <aside class="side">
          <section class="card checklist">
            <h2>提交清单</h2>
            <dl>
              <div>
                <dt>小组</dt>
                <dd>{{ detail.groupName ? `${detail.groupName} · ${detail.members?.length || 0} 人` : '未加入小组' }}</dd>
              </div>
              <div>
                <dt>分工确认</dt>
                <dd :class="{ ok: workloadStatus?.locked }">{{ workloadHint }}</dd>
              </div>
              <div>
                <dt>附件</dt>
                <dd>{{ detail.attachments?.length || 0 }} 个</dd>
              </div>
              <div>
                <dt>提交状态</dt>
                <dd>{{ statusText }}</dd>
              </div>
            </dl>
          </section>

          <section class="card team">
            <h2>我的小组</h2>
            <template v-if="detail.groupName">
              <strong class="team-name">
                {{ detail.groupName }}<small>{{ detail.members?.length || 0 }} 人</small>
              </strong>
              <ul>
                <li v-for="member in detail.members" :key="member.studentId">
                  <span>
                    {{ member.name }}（{{ member.studentNo }}）
                    <em v-if="member.role === 'LEADER'">组长</em>
                  </span>
                  <b>{{ ratioText(member.contributionRatio) }}</b>
                </li>
              </ul>
              <p class="muted">贡献值为已锁定分工的加权贡献 C</p>
            </template>
            <p v-else class="muted">尚未加入小组</p>
          </section>
        </aside>
      </main>
    </template>
    <div v-else class="loading">正在加载作业...</div>

    <div v-if="appealVisible" class="dialog">
      <div>
        <h2>提交成绩申诉</h2>
        <textarea v-model="appealReason" placeholder="请填写申诉理由"></textarea>
        <div class="dialog-actions">
          <button type="button" @click="appealVisible = false">取消</button>
          <button type="button" :disabled="!appealReason.trim()" @click="submitAppeal">提交申诉</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import WorkloadAllocationPanel from '@/components/student/WorkloadAllocationPanel.vue'
import StudentSubmissionPanel from '@/components/student/StudentSubmissionPanel.vue'
import { appealScore, createStudentGroup, getAssignment, getAssignmentGroups, getScores, joinStudentGroup, leaveStudentGroup } from '@/api/student'
import { getErrorMessage } from '@/api/response'
import { saveRecentProject } from '@/utils/studentRecentProject'
import { attachmentDisplayName, downloadAuthenticatedFile } from '@/utils/downloadFile'
import { readStoredUser } from '@/utils/session'
import { groupSubmitterName, isGroupSubmissionReturned, isGroupSubmitted } from '@/utils/submission'
import { groupSubmissionStatus, isSubmissionOpen, workloadChecklistHint } from '@/utils/assignmentWindow'

const route = useRoute()
const router = useRouter()
const detail = ref(null)
const scores = ref([])
const groups = ref([])
const groupOperating = ref(false)
const errorMessage = ref('')
const appealVisible = ref(false)
const appealReason = ref('')
const workloadStatus = ref(null)
const activeStep = ref('brief')
const now = ref(Date.now())
let countdownTimer

const reviewed = computed(() => detail.value?.scorePublished === true || detail.value?.scorePublished === 1)
// One submission per group: the backend resolves the same status for every ACTIVE member.
const submitted = computed(() => isGroupSubmitted(detail.value))
const returned = computed(() => isGroupSubmissionReturned(detail.value))
const submitterName = computed(() => groupSubmitterName(detail.value))
const graded = computed(() => reviewed.value || detail.value?.teacherScore != null
  || detail.value?.submissionStatus === 'GRADED')
const isLeader = computed(() => detail.value?.memberRole === 'LEADER')
const submissionEnd = computed(() => detail.value?.deadline ? new Date(detail.value.deadline) : null)
// No formation phase: grouping, workload allocation and submission share one window.
const submissionOpen = computed(() => isSubmissionOpen(detail.value, now.value))
const currentScore = computed(() => scores.value.find((item) => Number(item.assignmentId) === Number(route.params.assignmentId)))
const submissionState = computed(() => groupSubmissionStatus({
  reviewed: reviewed.value,
  returned: returned.value,
  submitted: submitted.value,
  groupName: detail.value?.groupName,
  submissionOpen: submissionOpen.value
}))
const statusText = computed(() => submissionState.value.text)
const statusClass = computed(() => submissionState.value.tone)
const assignmentType = computed(() => detail.value?.standardGroupSize ? `${detail.value.standardGroupSize}人作业` : '人数未设置')
const workloadReadOnly = computed(() => reviewed.value || !submissionOpen.value)
const currentStudentId = computed(() => {
  const user = readStoredUser()
  return user?.id ?? user?.userId ?? user?.studentId ?? null
})

const countdownMilliseconds = computed(() => Math.max(0, (submissionEnd.value?.getTime() || 0) - now.value))
const countdownText = computed(() => {
  const totalMinutes = Math.ceil(countdownMilliseconds.value / 60000)
  if (totalMinutes <= 0) return '提交已截止'
  const hours = Math.floor(totalMinutes / 60)
  return `${hours} 小时 ${totalMinutes % 60} 分钟`
})
const countdownTitle = computed(() => submissionOpen.value ? '距离提交截止' : '作业提交已截止')
const countdownDescription = computed(() => submissionOpen.value
  ? `请在 ${formatDate(submissionEnd.value)} 前完成组队、分工与提交`
  : `截止时间：${formatDate(submissionEnd.value)}，小组、分工与提交均已锁定`)
const deadlineBannerClass = computed(() => submissionOpen.value ? 'deadline-banner--submission' : 'deadline-banner--closed')

const workloadHint = computed(() => detail.value?.groupName
  ? workloadChecklistHint(workloadStatus.value)
  : '尚未加入小组')

const steps = computed(() => [
  { key: 'brief', index: '1', label: '作业要求', hint: `${detail.value?.parts?.length || 0} 个评价分支`, target: 'section-brief' },
  { key: 'workload', index: '2', label: '小组分工', hint: workloadHint.value, target: 'section-workload' },
  { key: 'upload', index: '3', label: '上传附件', hint: `${detail.value?.attachments?.length || 0} 个附件`, target: 'section-submit' },
  { key: 'submit', index: '4', label: '确认提交', hint: statusText.value, target: 'section-submit' }
])

const score = (value) => value == null ? '—' : Number(value).toFixed(2)
const pct = (weight) => `${(Number(weight) * 100).toFixed(0)}%`
const ratioText = (value) => value == null ? '—' : `${Number(value).toFixed(1)}%`
const formatDate = (value) => value
  ? new Date(value).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
  : '未设置'
const goTo = (step) => {
  activeStep.value = step.key
  document.getElementById(step.target)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
const onWorkloadStatus = (status) => { workloadStatus.value = status }
const download = async (file, index) => {
  try { await downloadAuthenticatedFile(file, index) } catch (error) { ElMessage.error(getErrorMessage(error, '文件下载失败')) }
}

const load = async () => {
  detail.value = await getAssignment(route.params.assignmentId)
  saveRecentProject({
    assignmentId: route.params.assignmentId,
    courseId: detail.value.courseId,
    title: detail.value.title,
    courseName: detail.value.courseName,
    deadline: detail.value.deadline
  })
  const [scoreRows, groupRows] = await Promise.all([
    getScores(),
    submissionOpen.value && !detail.value.groupName
      ? getAssignmentGroups(route.params.assignmentId)
      : Promise.resolve([])
  ])
  scores.value = scoreRows
  groups.value = groupRows
}

const runGroupAction = async (action, successMessage) => {
  groupOperating.value = true
  try {
    await action()
    ElMessage.success(successMessage)
    await load()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '组队操作失败'))
  } finally {
    groupOperating.value = false
  }
}
const createGroup = () => runGroupAction(() => createStudentGroup(route.params.assignmentId), '小组创建成功')
const joinGroup = (groupNo) => runGroupAction(() => joinStudentGroup(route.params.assignmentId, groupNo), '已加入小组')
const leaveGroup = () => runGroupAction(() => leaveStudentGroup(route.params.assignmentId), '已退出小组')
const submitAppeal = async () => {
  try {
    await appealScore(currentScore.value.scoreResultId, { reason: appealReason.value })
    appealVisible.value = false
    ElMessage.success('申诉已提交')
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '申诉提交失败')
  }
}

// The submission is shared by the whole group: refresh when the tab regains focus
// so another member's submit is reflected without a manual page reload.
const refreshOnFocus = () => { load().catch(() => {}) }
onMounted(() => {
  countdownTimer = window.setInterval(() => { now.value = Date.now() }, 1000)
  window.addEventListener('focus', refreshOnFocus)
  load().catch((error) => { errorMessage.value = getErrorMessage(error, '作业详情加载失败') })
})
onBeforeUnmount(() => {
  window.clearInterval(countdownTimer)
  window.removeEventListener('focus', refreshOnFocus)
})
</script>

<style scoped>
.page {
  width: min(1180px, 100%);
  margin: auto;
  color: #30435f;
}

.back {
  margin-bottom: 16px;
  padding: 0;
  border: 0;
  color: #6982a5;
  background: none;
  font-size: 13px;
  cursor: pointer;
}

.error {
  margin-bottom: 15px;
  padding: 12px;
  border-radius: 8px;
  color: #b42318;
  background: #fff1f0;
  font-size: 12px;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 13px;
  flex-wrap: wrap;
}

.title-row h1 {
  margin: 0;
  color: #203454;
  font-size: 24px;
}

.status-tag {
  padding: 5px 11px;
  border-radius: 99px;
  font-size: 11px;
}

.pending { color: #d58a31; background: #fff3df; }
.submitted { color: #2f9b70; background: #eaf8f1; }
.reviewed { color: #477cdc; background: #ebf2ff; }
.closed { color: #a4534d; background: #fff0ef; }

.meta {
  margin: 8px 0 0;
  color: #7e8da1;
  font-size: 12px;
}

.countdown {
  flex: none;
  text-align: right;
}

.countdown small {
  display: block;
  color: #8795a7;
  font-size: 11px;
}

.countdown b {
  color: #203454;
  font-size: 20px;
  white-space: nowrap;
}

.deadline-banner {
  margin-bottom: 16px;
  padding: 12px 17px;
  border: 1px solid;
  border-radius: 11px;
  font-size: 12px;
}
.deadline-banner--submission { border-color: #b9dfca; color: #28815c; background: #edf9f2; }
.deadline-banner--closed { border-color: #e3c9c7; color: #a4534d; background: #fff3f2; }

.steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 18px;
}

.step {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 13px;
  border: 1px solid #e2e9f3;
  border-radius: 11px;
  color: #60738c;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.step--active {
  border-color: #a9c1ee;
  color: #426fc9;
  background: #edf3ff;
}

.step-index {
  display: grid;
  place-items: center;
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: #60738c;
  background: #f1f5fb;
  font-size: 12px;
}

.step--active .step-index { color: #fff; background: #527de0; }

.step-text {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.step-text b { font-size: 13px; font-weight: 500; }

.step-text small {
  overflow: hidden;
  color: #8795a7;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 18px;
  align-items: start;
}

.primary,
.side {
  display: grid;
  gap: 18px;
  min-width: 0;
}

.side {
  position: sticky;
  top: 18px;
  grid-auto-rows: 1fr;
}

.card {
  box-sizing: border-box;
  width: 100%;
  padding: 22px;
  border: 1px solid #e2e9f3;
  border-radius: 13px;
  background: #fff;
  box-shadow: 0 5px 18px #2c486e0d;
}

.card h2 {
  margin: 0 0 17px;
  color: #203454;
  font-size: 16px;
}

.card h3 {
  margin: 0 0 12px;
  font-size: 13px;
}

.description {
  margin: 0;
  color: #64768e;
  font-size: 13px;
  line-height: 1.9;
  overflow-wrap: anywhere;
  white-space: pre-line;
}

.rubric { margin-top: 26px; }

.rubric-list {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.rubric-list li {
  display: grid;
  gap: 5px;
  padding: 9px 0;
  border-bottom: 1px solid #eef2f8;
}

.rubric-list li:last-child { border-bottom: 0; }

.rubric-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: #40556f;
  font-size: 12px;
}

.rubric-top b { color: #5279d2; font-size: 13px; }

.rubric-list small {
  color: #9aa6b5;
  font-size: 11px;
  line-height: 1.6;
}

.rubric-list i {
  display: block;
  height: 6px;
  margin-top: 4px;
  overflow: hidden;
  border-radius: 99px;
  background: #edf2f8;
}

.rubric-list em {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #4f7de3, #83a8f3);
}

.muted { margin: 0; color: #9aa6b5; font-size: 12px; }

.checklist dl {
  display: grid;
  gap: 12px;
  margin: 0;
}

.checklist dl > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.checklist dt { color: #8795a7; font-size: 12px; }

.checklist dd {
  margin: 0;
  color: #40556f;
  font-size: 12px;
  text-align: right;
}

.checklist dd.ok { color: #2e8a62; }

.team-name {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding: 11px 13px;
  border-radius: 9px;
  color: #4c73c8;
  background: #edf3ff;
  font-size: 13px;
}

.team-name small { color: #7b8da7; font-size: 11px; font-weight: 400; }

.team ul {
  display: grid;
  gap: 7px;
  margin: 0 0 12px;
  padding: 0;
  list-style: none;
}

.team li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 11px;
  border-radius: 8px;
  color: #60738c;
  background: #f6f9fd;
  font-size: 12px;
}

.team li em { margin-left: 5px; color: #5279d2; font-size: 11px; font-style: normal; }
.team li b { color: #4575d2; font-size: 12px; font-weight: 500; }

.grouping-head,
.current-group,
.available-groups article {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.grouping-head { margin-bottom: 17px; }
.grouping-head h2 { margin-bottom: 5px; }
.grouping-head p,
.grouping-head > span,
.current-group small,
.available-groups small { margin: 0; color: #8795a7; font-size: 11px; }
.current-group { padding: 13px 14px; border-radius: 9px; color: #426fc9; background: #edf3ff; }
.member-list { display: grid; gap: 7px; margin: 12px 0; }
.member-list span { padding: 9px 11px; border-radius: 7px; color: #60738c; background: #f7f9fc; font-size: 12px; }
.group-actions { display: flex; align-items: center; gap: 12px; margin-bottom: 15px; color: #98a4b3; font-size: 11px; }
.primary-button,
.danger-button,
.available-groups button { padding: 8px 14px; border-radius: 7px; font-size: 12px; cursor: pointer; }
.primary-button { border: 0; color: #fff; background: #527de0; }
.danger-button { width: 100%; border: 1px solid #e3b7b3; color: #b64f47; background: #fff; }
.available-groups { display: grid; gap: 8px; }
.available-groups article { padding: 11px 12px; border: 1px solid #e4ebf4; border-radius: 8px; }
.available-groups article div { display: grid; gap: 3px; }
.available-groups button { border: 1px solid #9db7eb; color: #5279d2; background: #fff; }
.available-groups button:disabled,
.primary-button:disabled,
.danger-button:disabled { opacity: .5; cursor: not-allowed; }
.locked-group p { margin: 0 0 10px; color: #7b899b; font-size: 13px; line-height: 1.7; }
.locked-group .group-shared-hint { color: #5a78b8; font-size: 11px; }
.locked-group button {
  display: flex;
  box-sizing: border-box;
  width: 100%;
  justify-content: space-between;
  margin-bottom: 8px;
  padding: 10px 12px;
  border: 1px solid #e2e9f2;
  border-radius: 8px;
  color: #526fbd;
  background: #fff;
  font-size: 12px;
  cursor: pointer;
}
.locked-group button small { color: #8795a7; }

.readonly { display: grid; gap: 10px; }
.readonly > b { padding: 12px; border-radius: 8px; color: #2e9870; background: #ebf8f2; }
.readonly p { margin: 0; color: #718198; font-size: 12px; }
.readonly .group-shared-hint { color: #5a78b8; font-size: 11px; }
.readonly button {
  display: flex;
  justify-content: space-between;
  padding: 10px 12px;
  border: 1px solid #e2e9f2;
  border-radius: 8px;
  color: #526fbd;
  background: #fff;
  font-size: 12px;
  cursor: pointer;
}
.readonly button small { color: #8795a7; }

.grade-scores { display: flex; align-items: center; gap: 20px; }
.grade-scores span { display: grid; color: #8795a7; font-size: 11px; }
.grade-scores b { color: #4575d2; font-size: 22px; }
.grade-scores button {
  margin-left: auto;
  padding: 7px 14px;
  border: 0;
  border-radius: 7px;
  color: #fff;
  background: #d98c4a;
  font-size: 12px;
  cursor: pointer;
}
.grade p { margin: 14px 0 0; color: #77879b; font-size: 12px; }

.loading { padding: 50px; text-align: center; color: #8795a7; }

.dialog {
  position: fixed;
  z-index: 50;
  inset: 0;
  display: grid;
  place-items: center;
  background: #263b5355;
}
.dialog > div { width: min(420px, 90%); padding: 24px; border-radius: 12px; background: #fff; }
.dialog h2 { margin: 0 0 12px; font-size: 16px; }
.dialog textarea {
  box-sizing: border-box;
  width: 100%;
  min-height: 120px;
  padding: 11px;
  border: 1px solid #dfe7f1;
  border-radius: 9px;
  resize: vertical;
  font-size: 13px;
}
.dialog-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 14px; }
.dialog-actions button { padding: 8px 16px; border-radius: 8px; font-size: 12px; cursor: pointer; }
.dialog-actions button:first-child { border: 1px solid #dce5ef; color: #60738c; background: #fff; }
.dialog-actions button:last-child { border: 0; color: #fff; background: #527de0; }
.dialog-actions button:disabled { opacity: .5; cursor: not-allowed; }

@media (max-width: 1000px) {
  .layout { grid-template-columns: 1fr; }
  .side { position: static; }
}

@media (max-width: 700px) {
  .detail-head { flex-direction: column; }
  .countdown { text-align: left; }
  .steps { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .card { padding: 17px; }
}
</style>
