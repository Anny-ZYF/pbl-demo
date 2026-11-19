<template>
  <section class="submit-card">
    <p class="group-shared-hint">本次作业只用在作业说明内填写以下内容：1.作业标题；2.组长姓名；3.小组成员；</p>
    <template v-if="submitted && !editing">
      <div class="success">
        <b>{{ returned ? '↩ 已退回，请重新提交' : '✓ 已提交' }}</b>
        <small>{{ submitterName ? `${submitterName} 代表小组提交 · ` : '' }}{{ attachments.length }} 个附件</small>
      </div>
      <button v-for="(file, index) in attachments" :key="file.id || file.storageKey" class="file-download" @click="download(file, index)"><span>📄 {{ attachmentDisplayName(file, index) }}</span><small>{{ formatSize(file.fileSize) }} · 下载</small></button>
      <div class="note"><b>提交说明</b><p>{{ initialNote || initialContent || '本次提交暂无说明' }}</p></div>
      <button class="outline" @click="editing = true">重新提交</button>
    </template>
    <template v-else>
      <h2>{{ submitted ? '重新提交' : '小组提交' }}</h2>
      <p class="draft-hint">填写提交说明即可提交作业，附件可选；{{ submitted ? '重新提交前的附件仍属于本组共用提交，教师看到的是当前最新内容' : '上传的附件会立即对全组成员可见，点击「提交作业」后教师才会看到' }}。提交前可移除小组附件。</p>
      <p v-if="graded" class="locked-hint">作业已批阅，小组附件与提交内容已锁定。</p>
      <div v-if="attachments.length" class="draft-files">
        <b>小组附件（{{ attachments.length }}）</b>
        <div v-for="(file, index) in attachments" :key="file.id || file.storageKey" class="draft-file-row">
          <button class="file-download" @click="download(file, index)"><span>📄 {{ attachmentDisplayName(file, index) }}</span><small>{{ formatSize(file.fileSize) }} · 下载</small></button>
          <button v-if="canRemoveGroupAttachment(file, { graded })" class="remove" type="button" @click="removeAttachment(file, index)">移除</button>
        </div>
      </div>
      <input ref="picker" type="file" multiple accept=".zip,.rar,.pdf,.doc,.docx,.ppt,.pptx" @change="pickFiles">
      <button class="drop" :disabled="uploading" @click="picker?.click()"><strong>☁</strong><b>{{ uploading ? '正在上传…' : '拖拽文件到这里' }}</b><span>或点击选择文件</span><small>支持 PDF / ZIP / DOCX / PPTX / RAR，单个文件不超过 20MB</small></button>
      <ul><li v-for="file in files" :key="file.name + file.size"><span>📄 {{ file.name }} · {{ formatSize(file.size) }}</span><button :disabled="uploading" @click="removeFile(file)">删除</button></li></ul>
      <label><b>提交说明</b><textarea v-model="note" maxlength="500" placeholder="填写提交说明即可提交作业，无需上传附件..."></textarea><small>{{ note.length }}/500</small></label>
      <div class="actions"><button v-if="submitted" class="outline" @click="editing = false">取消</button><button class="primary" :disabled="saving || uploading || graded" @click="submit">{{ saving ? '提交中…' : '提交作业' }}</button></div>
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
  attachments: { type: Array, default: () => [] }
})
const emit = defineEmits(['submitted', 'changed'])
const returned = computed(() => props.submissionStatus === 'RETURNED')
const picker = ref()
const files = ref([])
const saving = ref(false)
const uploading = ref(false)
const editing = ref(!props.submitted || returned.value)
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
const submit = async () => {
  if (uploading.value) return
  saving.value = true
  try {
    await submitAssignment(props.assignmentId, buildSubmissionPayload(note.value))
    files.value = []
    editing.value = false
    ElMessage.success('作业已提交，本组状态已同步')
    emit('submitted')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '提交失败'))
  } finally {
    saving.value = false
  }
}
</script>
<style scoped>
.submit-card{padding:22px;border:1px solid #e2e9f3;border-radius:13px;background:#fff;box-shadow:0 5px 18px #2c486e0d}h2{margin:0 0 20px;font-size:17px}input{display:none}.drop{display:flex;width:100%;min-height:170px;align-items:center;justify-content:center;flex-direction:column;border:1px dashed #9eb9ea;border-radius:11px;color:#5a83dd;background:#fbfdff;cursor:pointer}.drop strong{font-size:32px}.drop b{color:#667891}.drop span{margin:5px}.drop small{margin-top:12px;color:#a0acba;font-size:9px}ul{padding:0;list-style:none}li,.file-download{display:flex;width:100%;box-sizing:border-box;gap:8px;align-items:center;justify-content:space-between;padding:10px;border:1px solid #e5ecf4;border-radius:8px;color:#557bd0;background:#fff}.file-download{margin-bottom:8px;cursor:pointer}.file-download small{color:#8d9aad}li button{border:0;color:#b85a52;background:none}label{position:relative;display:block;margin-top:18px}label>b{display:block;margin-bottom:9px;font-size:12px}textarea{box-sizing:border-box;width:100%;min-height:115px;padding:12px;border:1px solid #dfe7f1;border-radius:9px;resize:vertical}label>small{position:absolute;right:10px;bottom:9px;color:#a4afbc}.actions{display:flex;justify-content:flex-end;gap:10px;margin-top:18px}.primary,.outline{padding:10px 22px;border-radius:99px;cursor:pointer}.primary{min-width:145px;border:0;color:#fff;background:linear-gradient(90deg,#4e7ae8,#7194f0)}.outline{border:1px solid #aec3ec;color:#5279d2;background:#fff}.success{display:flex;justify-content:space-between;margin-bottom:12px;padding:14px;color:#319b70;background:#effaf4}.note{padding:14px;color:#50627a}.note p{color:#7f8ea2;font-size:11px;line-height:1.7}.submit-card>.outline{width:100%}
.group-shared-hint{margin:0 0 16px;padding:10px 12px;border-radius:8px;color:#4a6ba8;background:#f2f6ff;font-size:12px;line-height:1.6}.draft-hint{margin:-10px 0 16px;color:#6c7a8d;font-size:12px;line-height:1.6}.locked-hint{margin:-8px 0 16px;padding:9px 12px;border-radius:8px;color:#92652b;background:#fff4e2;font-size:12px;line-height:1.6}.draft-files{margin-bottom:16px}.draft-files>b{display:block;margin-bottom:8px;font-size:12px;color:#50627a}.draft-file-row{display:flex;gap:8px;align-items:stretch}.draft-file-row .file-download{flex:1}.draft-file-row .remove{padding:0 12px;border:1px solid #e6c6c2;border-radius:8px;color:#b85a52;background:#fff;font-size:12px;cursor:pointer;margin-bottom:8px}
</style>
