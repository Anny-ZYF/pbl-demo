<template>
  <section class="submit-card">
    <p class="group-shared-hint">本组共用一份提交：作业内容与附件仅组长可编辑，组员可查看和下载。成员分工在「小组与工作量分配」中单独填写。</p>
    <template v-if="submitted && !editing">
      <div class="success">
        <b>{{ returned ? '↩ 已退回，请重新提交' : '✓ 已提交' }}</b>
        <small>{{ submitterName ? `${submitterName} 代表小组提交 · ` : '' }}{{ attachments.length }} 个附件</small>
      </div>
      <button v-for="(file, index) in attachments" :key="file.id || file.storageKey" class="file-download" @click="download(file, index)"><span>📄 {{ attachmentDisplayName(file, index) }}</span><small>{{ formatSize(file.fileSize) }} · 下载</small></button>
      <div class="note"><b>作业内容</b><p>{{ initialNote || initialContent || '本次提交暂无内容' }}</p></div>
      <button v-if="canEdit" class="outline" @click="editing = true">重新提交</button>
    </template>
    <template v-else>
      <h2>{{ submitted ? '重新提交' : '小组提交' }}</h2>
      <p v-if="graded" class="locked-hint">作业已批阅，小组附件与提交内容已锁定。</p>
      <p v-else-if="!isLeader" class="locked-hint">只有小组组长可以编辑作业内容和上传附件；组长提交后全组可见。</p>
      <p v-else class="draft-hint">填写提交说明或上传附件即可提交，两者至少填一项；附件可选，重新提交前的小组附件仍会保留。</p>
      <div v-if="attachments.length" class="draft-files">
        <b>小组附件（{{ attachments.length }}）</b>
        <div v-for="(file, index) in attachments" :key="file.id || file.storageKey" class="draft-file-row">
          <button class="file-download" @click="download(file, index)"><span>📄 {{ attachmentDisplayName(file, index) }}</span><small>{{ formatSize(file.fileSize) }} · 下载</small></button>
          <button v-if="canEdit && canRemoveGroupAttachment(file, { graded })" class="remove" type="button" @click="removeAttachment(file, index)">移除</button>
        </div>
      </div>
      <template v-if="canEdit">
        <input ref="picker" type="file" multiple accept=".zip,.rar,.pdf,.doc,.docx,.ppt,.pptx" @change="pickFiles">
        <button class="drop" :disabled="uploading" @click="picker?.click()"><strong>☁</strong><b>{{ uploading ? '正在上传…' : '拖拽文件到这里' }}</b><span>或点击选择文件</span><small>支持 PDF / ZIP / DOCX / PPTX / RAR，单个文件不超过 20MB（可选）</small></button>
        <ul v-if="files.length"><li v-for="file in files" :key="file.name + file.size"><span>📄 {{ file.name }} · {{ formatSize(file.size) }}</span><button :disabled="uploading" @click="removeFile(file)">删除</button></li></ul>
      </template>
      <label>
        <b>提交说明</b>
        <textarea v-model="note" maxlength="500" :disabled="!canEdit" placeholder="例如：作业标题、组长姓名、小组成员……"></textarea>
        <small>{{ note.length }}/500</small>
      </label>
      <div class="actions"><button v-if="submitted" class="outline" @click="editing = false">取消</button><button v-if="canEdit" class="primary" :disabled="!canSubmit" @click="submit">{{ saving ? '提交中…' : '提交作业' }}</button></div>
      <p v-if="canEdit && !ready" class="ready-hint">{{ readyHint }}</p>
    </template>
  </section>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteSubmissionAttachment, submitAssignment, uploadStudentFile, registerDraftAttachment } from '@/api/student'
import { getErrorMessage } from '@/api/response'
import { attachmentDisplayName, downloadAuthenticatedFile } from '@/utils/downloadFile'
import { buildSubmissionPayload, canRemoveGroupAttachment } from '@/utils/submission'
const props = defineProps({
  assignmentId: [String, Number],
  initialContent: String,
  initialNote: String,
  submitted: Boolean,
  submissionStatus: String,
  submitterName: String,
  graded: Boolean,
  isLeader: { type: Boolean, default: false },
  attachments: { type: Array, default: () => [] }
})
const emit = defineEmits(['submitted', 'changed'])
const returned = computed(() => props.submissionStatus === 'RETURNED')
const picker = ref()
const files = ref([])
const saving = ref(false)
const uploading = ref(false)
const editing = ref(!props.submitted || returned.value)
// Only the leader edits the content and the attachments; teammates read the shared result.
const canEdit = computed(() => props.isLeader && !props.graded)
// Older submissions may only have `content`; the note is the single editable text field.
const note = ref(props.initialNote || props.initialContent || '')
watch(() => [props.submitted, props.submissionStatus], () => {
  editing.value = !props.submitted || props.submissionStatus === 'RETURNED'
})
watch(() => [props.initialNote, props.initialContent], ([newNote, newContent]) => {
  note.value = newNote || newContent || ''
})
const allowed = ['zip', 'rar', 'pdf', 'doc', 'docx', 'ppt', 'pptx']
const formatSize = (value) => { const size = Number(value || 0); if (!Number.isFinite(size) || size <= 0) return '大小未知'; return size < 1048576 ? `${Math.ceil(size / 1024)} KB` : `${(size / 1048576).toFixed(1)} MB` }
const download = async (file, index) => { try { await downloadAuthenticatedFile(file, index) } catch (error) { ElMessage.error(getErrorMessage(error, '文件下载失败')) } }
const removeFile = (file) => { files.value = files.value.filter((item) => item !== file) }
const removeAttachment = async (file, index) => {
  try {
    await ElMessageBox.confirm(`确定移除小组附件「${attachmentDisplayName(file, index)}」吗？提交作业前均可移除，移除后全组成员都会同步。`, '移除小组附件', { type: 'warning' })
  } catch { return }
  try {
    await deleteSubmissionAttachment(props.assignmentId, file.id)
    ElMessage.success('附件已移除')
    emit('changed')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '附件移除失败'))
  }
}
const pickFiles = async (event) => {
  const picked = Array.from(event.target.files || [])
  event.target.value = ''
  for (const file of picked) {
    const extension = file.name.split('.').pop().toLowerCase()
    if (!allowed.includes(extension) || file.size > 20971520) { ElMessage.error(`文件格式或大小不符合要求：${file.name}`); continue }
    if (files.value.some((item) => item.name === file.name && item.size === file.size)) continue
    files.value.push(file)
  }
  if (!files.value.length || uploading.value) return
  uploading.value = true
  try {
    for (const file of [...files.value]) {
      try {
        const stored = await uploadStudentFile(file)
        if (!stored?.storageKey) throw new Error(`文件 ${file.name} 上传失败`)
        await registerDraftAttachment(props.assignmentId, {
          storageKey: stored.storageKey,
          originalName: stored.originalName || file.name,
          fileSize: stored.size ?? file.size ?? 0
        })
        files.value = files.value.filter((item) => item !== file)
        emit('changed')
      } catch (error) {
        ElMessage.error(getErrorMessage(error, `文件 ${file.name} 上传失败`))
      }
    }
  } finally {
    uploading.value = false
  }
}

// A filled note is a complete submission; any already-registered group attachment also counts,
// so a team that only uploaded files can re-submit without retyping or re-uploading anything.
const ready = computed(() => note.value.trim().length > 0 || props.attachments.length > 0)
const canSubmit = computed(() => canEdit.value && ready.value && !saving.value && !uploading.value)
const readyHint = computed(() => '请填写提交说明或上传至少一个附件')

const submit = async () => {
  if (!canSubmit.value) return
  saving.value = true
  try {
    await submitAssignment(props.assignmentId, buildSubmissionPayload(note.value))
    files.value = []
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
.submit-card{padding:22px;border:1px solid #e2e9f3;border-radius:13px;background:#fff;box-shadow:0 5px 18px #2c486e0d}h2{margin:0 0 20px;font-size:17px}input{display:none}.drop{display:flex;width:100%;min-height:170px;align-items:center;justify-content:center;flex-direction:column;border:1px dashed #9eb9ea;border-radius:11px;color:#5a83dd;background:#fbfdff;cursor:pointer}.drop strong{font-size:32px}.drop b{color:#667891}.drop span{margin:5px}.drop small{margin-top:12px;color:#a0acba;font-size:9px}ul{padding:0;list-style:none}li,.file-download{display:flex;width:100%;box-sizing:border-box;gap:8px;align-items:center;justify-content:space-between;padding:10px;border:1px solid #e5ecf4;border-radius:8px;color:#557bd0;background:#fff}.file-download{margin-bottom:8px;cursor:pointer}.file-download small{color:#8d9aad}li button{border:0;color:#b85a52;background:none}label{position:relative;display:block;margin-top:18px}label>b{display:block;margin-bottom:9px;font-size:12px}textarea{box-sizing:border-box;width:100%;min-height:115px;padding:12px;border:1px solid #dfe7f1;border-radius:9px;resize:vertical;font:inherit}label>small{position:absolute;right:10px;bottom:9px;color:#a4afbc}.actions{display:flex;justify-content:flex-end;gap:10px;margin-top:18px}.primary,.outline{padding:10px 22px;border-radius:99px;cursor:pointer}.primary{min-width:145px;border:0;color:#fff;background:linear-gradient(90deg,#4e7ae8,#7194f0)}.primary:disabled{cursor:not-allowed;opacity:.55}.outline{border:1px solid #aec3ec;color:#5279d2;background:#fff}.success{display:flex;justify-content:space-between;margin-bottom:12px;padding:14px;color:#319b70;background:#effaf4}.note{padding:14px;color:#50627a}.note p{color:#7f8ea2;font-size:11px;line-height:1.7}.submit-card>.outline{width:100%}
.group-shared-hint{margin:0 0 16px;padding:10px 12px;border-radius:8px;color:#4a6ba8;background:#f2f6ff;font-size:12px;line-height:1.6}.draft-hint{margin:-10px 0 16px;color:#6c7a8d;font-size:12px;line-height:1.6}.locked-hint{margin:-8px 0 16px;padding:9px 12px;border-radius:8px;color:#92652b;background:#fff4e2;font-size:12px;line-height:1.6}.draft-files{margin-bottom:16px}.draft-files>b{display:block;margin-bottom:8px;font-size:12px;color:#50627a}.draft-file-row{display:flex;gap:8px;align-items:stretch}.draft-file-row .file-download{flex:1}.draft-file-row .remove{padding:0 12px;border:1px solid #e6c6c2;border-radius:8px;color:#b85a52;background:#fff;font-size:12px;cursor:pointer;margin-bottom:8px}
.ready-hint{margin:10px 0 0;color:#b85a52;font-size:11px;text-align:right}
</style>
