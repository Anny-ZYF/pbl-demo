<template>
  <section class="submit-card">
    <p class="group-shared-hint">本组共用一份提交：选题题目仅组长可编辑，组员可查看。成员分工在「小组与工作量分配」中单独填写。</p>
    <template v-if="submitted && !editing">
      <div class="success">
        <b>{{ returned ? '↩ 已退回，请重新提交' : '✓ 已提交' }}</b>
        <small v-if="submitterName">{{ submitterName }} 代表小组提交</small>
      </div>
      <div v-if="attachments.length" class="legacy-files">
        <b>历史附件（{{ attachments.length }}）</b>
        <button v-for="(file, index) in attachments" :key="file.id || file.storageKey" class="file-download" @click="download(file, index)"><span>📄 {{ attachmentDisplayName(file, index) }}</span><small>{{ formatSize(file.fileSize) }} · 下载</small></button>
      </div>
      <div class="note"><b>选题题目</b><p>{{ initialNote || initialContent || '本次提交暂无内容' }}</p></div>
      <button v-if="canEdit" class="outline" @click="editing = true">重新提交</button>
    </template>
    <template v-else>
      <h2>{{ submitted ? '重新提交' : '小组提交' }}</h2>
      <p v-if="graded" class="locked-hint">作业已批阅，提交内容已锁定。</p>
      <p v-else-if="!isLeader" class="locked-hint">只有小组组长可以编辑选题题目；组长提交后全组可见。</p>
      <p v-else class="draft-hint">填写选题题目即可提交，提交后仍可重新提交，直到作业截止或成绩发布。</p>
      <p v-if="workloadMissing" class="workload-hint" :class="{ 'workload-hint--alert': workloadError }">
        <b>贡献度尚未填写</b>：请先在上方「小组分工」中填写并保存，再回来提交作业。
      </p>
      <div v-if="attachments.length" class="legacy-files">
        <b>历史附件（{{ attachments.length }}）</b>
        <button v-for="(file, index) in attachments" :key="file.id || file.storageKey" class="file-download" @click="download(file, index)"><span>📄 {{ attachmentDisplayName(file, index) }}</span><small>{{ formatSize(file.fileSize) }} · 下载</small></button>
      </div>
      <label>
        <b>选题题目</b>
        <textarea v-model="note" maxlength="500" :disabled="!canEdit" placeholder="请填写本小组的选题题目"></textarea>
        <small>{{ note.length }}/500</small>
      </label>
      <div class="actions"><button v-if="submitted" class="outline" @click="editing = false">取消</button><button v-if="canEdit" class="primary" :disabled="!canSubmit" @click="submit">{{ saving ? '提交中…' : '提交作业' }}</button></div>
      <p v-if="canEdit && !ready" class="ready-hint">{{ readyHint }}</p>
    </template>
  </section>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { submitAssignment } from '@/api/student'
import { getErrorMessage } from '@/api/response'
import { attachmentDisplayName, downloadAuthenticatedFile } from '@/utils/downloadFile'
import { buildSubmissionPayload } from '@/utils/submission'
const props = defineProps({
  assignmentId: [String, Number],
  initialContent: String,
  initialNote: String,
  submitted: Boolean,
  submissionStatus: String,
  submitterName: String,
  graded: Boolean,
  isLeader: { type: Boolean, default: false },
  // A saved (LOCKED) contribution matrix is required before the group may submit; the backend
  // enforces the same rule, this flag only drives the red hint and the early guard.
  workloadReady: { type: Boolean, default: true },
  attachments: { type: Array, default: () => [] }
})
const emit = defineEmits(['submitted'])
const returned = computed(() => props.submissionStatus === 'RETURNED')
const saving = ref(false)
const editing = ref(!props.submitted || returned.value)
// Only the leader edits the content; teammates read the shared result.
const canEdit = computed(() => props.isLeader && !props.graded)
// Older submissions may only have `content`; the note is the single editable text field.
const note = ref(props.initialNote || props.initialContent || '')
watch(() => [props.submitted, props.submissionStatus], () => {
  editing.value = !props.submitted || props.submissionStatus === 'RETURNED'
})
watch(() => [props.initialNote, props.initialContent], ([newNote, newContent]) => {
  note.value = newNote || newContent || ''
})
const formatSize = (value) => { const size = Number(value || 0); if (!Number.isFinite(size) || size <= 0) return '大小未知'; return size < 1048576 ? `${Math.ceil(size / 1024)} KB` : `${(size / 1048576).toFixed(1)} MB` }
const download = async (file, index) => { try { await downloadAuthenticatedFile(file, index) } catch (error) { ElMessage.error(getErrorMessage(error, '文件下载失败')) } }

// A filled note is a complete submission; attachments are temporarily not uploaded from the UI,
// so they are neither required nor used to decide whether the form can be submitted.
const ready = computed(() => note.value.trim().length > 0)
const canSubmit = computed(() => canEdit.value && ready.value && !saving.value)
const readyHint = computed(() => '请填写选题题目后再提交')
// The leader must save the contribution matrix before submitting. The button stays clickable so
// a click can explain why, instead of leaving a dead grey button with no feedback.
const workloadError = ref(false)
const workloadMissing = computed(() => canEdit.value && !props.workloadReady)
watch(() => props.workloadReady, (value) => { if (value) workloadError.value = false })

const submit = async () => {
  if (!canSubmit.value) return
  if (workloadMissing.value) {
    workloadError.value = true
    ElMessage.error('请先在「小组分工」中填写并保存贡献度，再提交作业')
    document.getElementById('section-workload')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    return
  }
  saving.value = true
  try {
    await submitAssignment(props.assignmentId, buildSubmissionPayload(note.value))
    editing.value = false
    ElMessage.success('作业已提交，全组可见')
    emit('submitted')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '提交失败'))
  } finally {
    saving.value = false
  }
}
</script>
<style scoped>
.submit-card{padding:22px;border:1px solid #e2e9f3;border-radius:13px;background:#fff;box-shadow:0 5px 18px #2c486e0d}h2{margin:0 0 20px;font-size:17px}.file-download{display:flex;width:100%;box-sizing:border-box;gap:8px;align-items:center;justify-content:space-between;padding:10px;border:1px solid #e5ecf4;border-radius:8px;color:#557bd0;background:#fff}.file-download{margin-bottom:8px;cursor:pointer}.file-download small{color:#8d9aad}label{position:relative;display:block;margin-top:18px}label>b{display:block;margin-bottom:9px;font-size:12px}textarea{box-sizing:border-box;width:100%;min-height:115px;padding:12px;border:1px solid #dfe7f1;border-radius:9px;resize:vertical;font:inherit}label>small{position:absolute;right:10px;bottom:9px;color:#a4afbc}.actions{display:flex;justify-content:flex-end;gap:10px;margin-top:18px}.primary,.outline{padding:10px 22px;border-radius:99px;cursor:pointer}.primary{min-width:145px;border:0;color:#fff;background:linear-gradient(90deg,#4e7ae8,#7194f0)}.primary:disabled{cursor:not-allowed;opacity:.55}.outline{border:1px solid #aec3ec;color:#5279d2;background:#fff}.success{display:flex;justify-content:space-between;margin-bottom:12px;padding:14px;color:#319b70;background:#effaf4}.note{padding:14px;color:#50627a}.note p{color:#7f8ea2;font-size:11px;line-height:1.7}.submit-card>.outline{width:100%}
.group-shared-hint{margin:0 0 16px;padding:10px 12px;border-radius:8px;color:#4a6ba8;background:#f2f6ff;font-size:12px;line-height:1.6}.draft-hint{margin:-10px 0 16px;color:#6c7a8d;font-size:12px;line-height:1.6}.locked-hint{margin:-8px 0 16px;padding:9px 12px;border-radius:8px;color:#92652b;background:#fff4e2;font-size:12px;line-height:1.6}.legacy-files{margin-bottom:16px}.legacy-files>b{display:block;margin-bottom:8px;font-size:12px;color:#50627a}
.ready-hint{margin:10px 0 0;color:#b85a52;font-size:11px;text-align:right}
.workload-hint{margin:-6px 0 16px;padding:10px 12px;border-left:3px solid #e0a33a;border-radius:8px;color:#8a5a12;background:#fff8e8;font-size:12px;line-height:1.6}
.workload-hint--alert{border-left-color:#d64545;color:#b42318;background:#fdecec;animation:hintPulse .9s ease 2}
@keyframes hintPulse{0%,100%{transform:scale(1)}50%{transform:scale(1.012)}}
</style>
