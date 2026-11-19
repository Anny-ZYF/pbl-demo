<template>
  <section class="workload-card">
    <div class="wl-head">
      <div>
        <h2>小组与工作量分配</h2>
        <p>按评价分支填写每位成员的工作量份额，每行合计须为 100</p>
      </div>
      <span class="wl-role">{{ roleHint }}</span>
    </div>

    <div v-if="errorMessage" class="wl-error" role="alert">{{ errorMessage }}</div>

    <div v-if="loading" class="wl-state">正在加载工作量分配…</div>
    <template v-else>
      <div class="wl-status" :class="statusClass">
        <span>{{ statusBanner }}</span>
      </div>

      <div v-if="objections.length" class="wl-objections">
        <strong>{{ isLeader ? '有成员提出异议，请修改后重新提交新版本' : '已有成员提出异议，等待组长重新提交' }}</strong>
        <ul>
          <li v-for="(item, index) in objections" :key="`obj-${item.studentId ?? index}`">
            <span>{{ item.studentName || '组员' }}</span>
            <p>{{ item.comment || '未填写原因' }}</p>
          </li>
        </ul>
        <small>该版本已暂停确认，组长提交新版本后自动作废</small>
      </div>

      <div class="wl-scroll">
        <table class="wl-table">
          <thead>
            <tr>
              <th scope="col" class="wl-part-head">评价分支 / 权重</th>
              <th v-for="member in members" :key="`head-${member.studentId}`" scope="col">
                {{ member.name }}
                <small v-if="member.role === 'LEADER'">组长</small>
              </th>
              <th scope="col" class="wl-remain-head">剩余可分配</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <th scope="row">
                <span class="wl-part-name">{{ row.name }}</span>
                <small class="wl-weight">权重 {{ pct(row.weight) }}</small>
                <small v-if="row.description" class="wl-part-desc" :title="row.description">{{ row.description }}</small>
              </th>
              <td v-for="member in members" :key="`${row.id}-${member.studentId}`">
                <input
                  v-model="draft[cellKey(row.id, member.studentId)]"
                  type="number"
                  min="0"
                  max="100"
                  step="1"
                  inputmode="numeric"
                  :disabled="!editable"
                  :aria-label="`${row.name} - ${member.name} 工作量份额`"
                  :class="{ 'wl-cell--mine': isMine(member) }"
                  placeholder="—"
                >
              </td>
              <td class="wl-remain" :class="remainClass(row)">{{ remainText(row) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-if="workload && editable" class="wl-note">
        {{ objections.length ? '修改后提交将生成新版本，当前版本自动作废' : '重新提交会生成新版本，并重置全员确认状态' }}
      </p>

      <div class="wl-foot">
        <div class="wl-contrib">
          <span class="wl-label">加权贡献 C = Σ（分支权重 W × 本人份额 Q）</span>
          <div class="wl-contrib-list">
            <span v-for="member in members" :key="`c-${member.studentId}`">
              {{ member.name }} <b>{{ contributionText(member) }}</b>
            </span>
          </div>
        </div>
        <div class="wl-actions">
          <span class="wl-hint" :class="hintClass">{{ validationHint }}</span>
          <button v-if="editable" class="wl-primary" :disabled="!canSubmit" @click="submit">
            {{ saving ? '提交中…' : '提交工作量，等待全员确认' }}
          </button>
        </div>
      </div>

      <div v-if="canConfirm" class="wl-confirm">
        <span>请确认本组的工作量分配</span>
        <button class="wl-primary" :disabled="confirming" @click="confirm('CONFIRMED')">确认</button>
        <button class="wl-outline" :disabled="confirming" @click="objectionVisible = !objectionVisible">有异议</button>
      </div>
      <div v-if="canConfirm && objectionVisible" class="wl-objection">
        <textarea v-model="objectionReason" maxlength="1000" placeholder="请填写异议原因（必填）"></textarea>
        <button class="wl-primary" :disabled="confirming || !objectionReason.trim()" @click="confirm('OBJECTED')">提交异议</button>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmWorkload, getWorkload, proposeWorkload } from '@/api/student'
import { getErrorMessage } from '@/api/response'
import { isAutoLocked } from '@/utils/assignmentWindow'
import { ROW_TOLERANCE, allRowsComplete, buildAllocationPayload, buildContributions, buildRows, cellKey } from '@/utils/workload'

const props = defineProps({
  assignmentId: { type: [String, Number], required: true },
  groupId: { type: [String, Number], required: true },
  parts: { type: Array, default: () => [] },
  members: { type: Array, default: () => [] },
  isLeader: { type: Boolean, default: false },
  readOnly: { type: Boolean, default: false },
  currentStudentId: { type: [String, Number], default: null }
})
const emit = defineEmits(['changed', 'status'])

const workload = ref(null)
const draft = reactive({})
const loading = ref(false)
const saving = ref(false)
const confirming = ref(false)
const errorMessage = ref('')
const objectionVisible = ref(false)
const objectionReason = ref('')

const pct = (weight) => `${(Number(weight) * 100).toFixed(0)}%`
const isMine = (member) => props.currentStudentId != null
  && String(member.studentId) === String(props.currentStudentId)

const rows = computed(() => buildRows(props.parts, props.members, draft))
const allComplete = computed(() => allRowsComplete(rows.value, props.members.length))
const contributions = computed(() => buildContributions(props.parts, props.members, draft))

const objections = computed(() => Array.isArray(workload.value?.objections)
  ? workload.value.objections : [])
const editable = computed(() => props.isLeader && !props.readOnly
  && (!workload.value || workload.value.status !== 'LOCKED'))
const canSubmit = computed(() => editable.value && allComplete.value && !saving.value)
const canConfirm = computed(() => workload.value?.status === 'PENDING_CONFIRMATION'
  && workload.value.myConfirmation === 'PENDING' && !objections.value.length && !props.readOnly)

const autoLocked = computed(() => isAutoLocked(workload.value?.autoLocked))

const roleHint = computed(() => {
  if (autoLocked.value) return '截止自动锁定 · 不可编辑'
  if (props.readOnly) return '当前阶段只读'
  if (objections.value.length) return props.isLeader ? '有异议 · 请重新修改' : '待组长重新提交'
  if (!props.isLeader) return '由组长填写，我可确认'
  return editable.value ? '我是组长 · 可编辑' : '已锁定 · 不可编辑'
})

const confirmationText = (status) => ({
  PENDING: '待确认',
  CONFIRMED: '已确认',
  OBJECTED: '已提出异议'
}[status] || status || '未知')

const statusBanner = computed(() => {
  if (!workload.value) {
    return props.isLeader ? '尚无分工方案，请由组长填写并提交' : '组长尚未提交工作量分配方案'
  }
  const version = workload.value.versionNo ?? '—'
  if (workload.value.status === 'LOCKED' && autoLocked.value) {
    return `第 ${version} 版已在截止时间自动锁定（含未确认成员），评分将采用此版本`
  }
  if (workload.value.status === 'LOCKED') {
    return `第 ${version} 版已全员确认并锁定，评分将采用此版本`
  }
  if (objections.value.length) {
    return props.isLeader
      ? `第 ${version} 版因异议退回，请修改后重新提交新版本`
      : `第 ${version} 版因异议退回，等待组长重新提交`
  }
  if (workload.value.status === 'PENDING_CONFIRMATION') {
    const pending = workload.value.pendingMembers || []
    const suffix = pending.length ? ` · 待确认：${pending.join('、')}` : ''
    return `第 ${version} 版等待全员确认 · 我的状态：${confirmationText(workload.value.myConfirmation)}${suffix}`
  }
  if (workload.value.status === 'SUPERSEDED') {
    return `第 ${version} 版已被新版本替代`
  }
  return `第 ${version} 版状态：${workload.value.status}`
})

const statusClass = computed(() => ({
  'wl-status--locked': workload.value?.status === 'LOCKED',
  'wl-status--objected': objections.value.length > 0,
  'wl-status--pending': workload.value?.status === 'PENDING_CONFIRMATION' && !objections.value.length,
  'wl-status--empty': !workload.value
}))

const validationHint = computed(() => {
  if (!editable.value) {
    return props.readOnly ? '当前阶段不可编辑' : '分配已锁定或由组长填写'
  }
  if (allComplete.value) return '全部分支合计均为 100，可提交'
  const over = rows.value.filter((row) => row.remaining < -ROW_TOLERANCE).length
  if (over) return `有 ${over} 个分支超出 100，请调减`
  const pending = rows.value.filter((row) => row.remaining > ROW_TOLERANCE).length
  return `还有 ${pending} 个分支未分配完`
})

const hintClass = computed(() => ({
  'wl-hint--ok': allComplete.value,
  'wl-hint--over': !allComplete.value && rows.value.some((row) => row.remaining < -ROW_TOLERANCE)
}))

const contributionText = (member) => {
  const value = contributions.value[member.studentId]
  if (!Number.isFinite(value)) return '—'
  return `${allComplete.value ? '' : '≈'}${value.toFixed(1)}%`
}
const remainText = (row) => Math.abs(row.remaining) < ROW_TOLERANCE
  ? '0 ✓' : String(Math.round(row.remaining * 100) / 100)
const remainClass = (row) => {
  if (row.remaining > ROW_TOLERANCE) return 'wl-remain--pending'
  if (row.remaining < -ROW_TOLERANCE) return 'wl-remain--over'
  return 'wl-remain--done'
}

const fillDraft = (items) => {
  Object.keys(draft).forEach((key) => delete draft[key])
  for (const part of props.parts) {
    for (const member of props.members) draft[cellKey(part.id, member.studentId)] = ''
  }
  for (const item of items) {
    const key = cellKey(item.assignmentPartId, item.studentId)
    if (key in draft) draft[key] = String(Math.round(Number(item.share) * 10000) / 100)
  }
}

const emitStatus = () => emit('status', {
  hasVersion: Boolean(workload.value),
  status: workload.value?.status ?? null,
  myConfirmation: workload.value?.myConfirmation ?? null,
  versionNo: workload.value?.versionNo ?? null,
  locked: workload.value?.status === 'LOCKED',
  autoLocked: autoLocked.value,
  objected: objections.value.length > 0,
  pendingMembers: workload.value?.pendingMembers || [],
  complete: allComplete.value
})

const load = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await getWorkload(props.assignmentId)
    workload.value = data
    fillDraft(data?.items || [])
  } catch (error) {
    if (error?.status === 404) {
      workload.value = null
      fillDraft([])
    } else {
      errorMessage.value = getErrorMessage(error, '工作量分配加载失败')
    }
  } finally {
    loading.value = false
    emitStatus()
  }
}

const submit = async () => {
  if (!canSubmit.value) return
  saving.value = true
  errorMessage.value = ''
  try {
    await proposeWorkload(props.assignmentId, props.groupId,
      buildAllocationPayload(props.parts, props.members, draft))
    ElMessage.success('工作量分配已提交，等待全员确认')
    await load()
    emit('changed')
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '工作量分配提交失败')
  } finally {
    saving.value = false
  }
}

const confirm = async (status) => {
  if (!workload.value || confirming.value) return
  if (status === 'OBJECTED' && !objectionReason.value.trim()) {
    errorMessage.value = '提出异议时必须填写原因'
    return
  }
  confirming.value = true
  errorMessage.value = ''
  try {
    await confirmWorkload(workload.value.id, {
      status,
      comment: objectionReason.value.trim() || null
    })
    ElMessage.success(status === 'CONFIRMED' ? '已确认工作量分配' : '已提出异议')
    objectionReason.value = ''
    objectionVisible.value = false
    await load()
    emit('changed')
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '确认失败')
  } finally {
    confirming.value = false
  }
}

watch(() => [props.assignmentId, props.groupId], () => load(), { immediate: true })
watch(allComplete, emitStatus)
</script>

<style scoped>
.workload-card {
  box-sizing: border-box;
  width: 100%;
  padding: 22px;
  border: 1px solid #e2e9f3;
  border-radius: 13px;
  background: #fff;
  box-shadow: 0 5px 18px #2c486e0d;
}

.wl-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.wl-head h2 {
  margin: 0 0 5px;
  color: #203454;
  font-size: 16px;
}

.wl-head p {
  margin: 0;
  color: #8795a7;
  font-size: 12px;
}

.wl-role {
  flex: none;
  padding: 5px 11px;
  border-radius: 99px;
  color: #5279d2;
  background: #edf3ff;
  font-size: 11px;
}

.wl-error {
  margin-bottom: 14px;
  padding: 11px 13px;
  border-radius: 8px;
  color: #b42318;
  background: #fff1f0;
  font-size: 12px;
}

.wl-state {
  padding: 36px 0;
  color: #8795a7;
  text-align: center;
  font-size: 13px;
}

.wl-status {
  margin-bottom: 15px;
  padding: 11px 14px;
  border-radius: 9px;
  color: #60738c;
  background: #f6f9fd;
  font-size: 12px;
}

.wl-status--empty { color: #7b899b; background: #f6f9fd; }
.wl-status--pending { color: #426fc9; background: #edf3ff; }
.wl-status--locked { color: #2e8a62; background: #ebf8f2; }
.wl-status--objected { color: #a35a1f; background: #fff3e6; }

.wl-objections {
  margin-bottom: 15px;
  padding: 12px 14px;
  border: 1px solid #f0d6b8;
  border-radius: 9px;
  background: #fffaf3;
}

.wl-objections > strong { color: #a35a1f; font-size: 12px; font-weight: 500; }
.wl-objections ul { display: grid; gap: 8px; margin: 10px 0 0; padding: 0; list-style: none; }
.wl-objections li { display: grid; gap: 3px; padding: 9px 11px; border-radius: 7px; background: #fff; }
.wl-objections li span { color: #40556f; font-size: 11px; }
.wl-objections li p { margin: 0; color: #7b899b; font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.wl-objections > small { display: block; margin-top: 9px; color: #b08b68; font-size: 11px; }

.wl-scroll {
  overflow-x: auto;
}

.wl-table {
  width: 100%;
  min-width: 560px;
  border-collapse: collapse;
  table-layout: fixed;
}

.wl-table th,
.wl-table td {
  padding: 9px 8px;
  border-bottom: 1px solid #eef2f8;
  font-size: 12px;
  text-align: center;
  vertical-align: middle;
}

.wl-table thead th {
  color: #8795a7;
  font-weight: 400;
  font-size: 11px;
  white-space: nowrap;
}

.wl-table thead th small {
  display: block;
  color: #5279d2;
  font-size: 10px;
}

.wl-part-head { width: 30%; text-align: left !important; }
.wl-remain-head { width: 110px; text-align: right !important; }

.wl-table tbody th {
  color: #40556f;
  font-weight: 400;
  text-align: left;
}

.wl-part-name {
  display: block;
  color: #40556f;
  font-size: 12px;
}

.wl-weight {
  display: block;
  margin-top: 3px;
  color: #5279d2;
  font-size: 11px;
}

.wl-part-desc {
  display: block;
  overflow: hidden;
  max-width: 200px;
  margin-top: 3px;
  color: #9aa6b5;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wl-table input {
  box-sizing: border-box;
  width: 100%;
  min-width: 62px;
  padding: 7px 6px;
  border: 1px solid #dce5ef;
  border-radius: 7px;
  color: #30435f;
  background: #fff;
  font-size: 12px;
  text-align: center;
}

.wl-table input:focus {
  outline: none;
  border-color: #527de0;
}

.wl-table input:disabled {
  color: #5b6c82;
  background: #f6f9fd;
  border-color: #e4ebf4;
}

.wl-cell--mine:not(:disabled) {
  border-color: #a9c1ee;
  background: #fbfdff;
}

.wl-remain {
  font-size: 12px;
  text-align: right !important;
  white-space: nowrap;
}

.wl-remain--done { color: #2e8a62; }
.wl-remain--pending { color: #c1832f; }
.wl-remain--over { color: #c0483f; }

.wl-note {
  margin: 12px 0 0;
  color: #9aa6b5;
  font-size: 11px;
}

.wl-foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #eef2f8;
}

.wl-label {
  color: #8795a7;
  font-size: 11px;
}

.wl-contrib-list {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 6px;
  color: #60738c;
  font-size: 12px;
}

.wl-contrib-list b {
  color: #4575d2;
  font-weight: 500;
}

.wl-actions {
  display: grid;
  gap: 7px;
  justify-items: end;
}

.wl-hint {
  color: #c1832f;
  font-size: 11px;
}

.wl-hint--ok { color: #2e8a62; }
.wl-hint--over { color: #c0483f; }

.wl-primary,
.wl-outline {
  padding: 9px 18px;
  border-radius: 99px;
  font-size: 12px;
  cursor: pointer;
}

.wl-primary {
  border: 0;
  color: #fff;
  background: linear-gradient(90deg, #4e7ae8, #7194f0);
}

.wl-outline {
  border: 1px solid #aec3ec;
  color: #5279d2;
  background: #fff;
}

.wl-primary:disabled,
.wl-outline:disabled {
  opacity: .5;
  cursor: not-allowed;
}

.wl-confirm {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 16px;
  padding: 13px 15px;
  border-radius: 9px;
  color: #426fc9;
  background: #edf3ff;
  font-size: 12px;
}

.wl-confirm .wl-primary { padding: 7px 16px; }
.wl-confirm .wl-outline { padding: 7px 16px; }

.wl-objection {
  display: grid;
  gap: 10px;
  justify-items: end;
  margin-top: 12px;
}

.wl-objection textarea {
  box-sizing: border-box;
  width: 100%;
  min-height: 84px;
  padding: 11px;
  border: 1px solid #dfe7f1;
  border-radius: 9px;
  resize: vertical;
  font-size: 12px;
}

@media (max-width: 820px) {
  .workload-card { padding: 17px; }
  .wl-head { flex-direction: column; }
  .wl-foot { align-items: flex-start; }
  .wl-actions { justify-items: start; width: 100%; }
}
</style>
