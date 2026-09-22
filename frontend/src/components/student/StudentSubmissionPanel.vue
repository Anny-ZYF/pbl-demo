<template>
  <section class="submit-card">
    <template v-if="submitted && !editing">
      <div class="success"><b>✓ 已提交</b><small>{{ attachments.length }} 个附件</small></div>
      <button v-for="file in attachments" :key="file.id" class="file-download" @click="download(file)"><span>📄 {{ file.originalName }}</span><small>{{ formatSize(file.fileSize) }} · 下载</small></button>
      <div class="note"><b>提交说明</b><p>{{ initialNote || initialContent || '本次提交暂无说明' }}</p></div>
      <button class="outline" @click="editing = true">重新提交</button>
    </template>
    <template v-else>
      <h2>{{ submitted ? '重新提交' : '我的提交' }}</h2>
      <input ref="picker" type="file" multiple accept=".zip,.rar,.pdf,.doc,.docx,.ppt,.pptx" @change="pickFiles">
      <button class="drop" @click="picker?.click()"><strong>☁</strong><b>拖拽文件到这里</b><span>或点击选择文件</span><small>支持 PDF / ZIP / DOCX / PPTX / RAR，单个文件不超过 20MB</small></button>
      <ul><li v-for="file in files" :key="file.name + file.size"><span>📄 {{ file.name }} · {{ formatSize(file.size) }}</span><button @click="removeFile(file)">删除</button></li></ul>
      <label><b>提交说明</b><textarea v-model="note" maxlength="500" placeholder="请输入本次提交的说明（可选）..."></textarea><small>{{ note.length }}/500</small></label>
      <div class="actions"><button v-if="submitted" class="outline" @click="editing = false">取消</button><button class="primary" :disabled="saving" @click="submit">{{ saving ? '提交中…' : '提交作业' }}</button></div>
    </template>
  </section>
</template>
<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { submitAssignment, uploadStudentFile } from '@/api/student'
import { getErrorMessage } from '@/api/response'
import { downloadAuthenticatedFile } from '@/utils/downloadFile'
const props = defineProps({ assignmentId: [String, Number], contributionScores: { type: Object, default: () => ({}) }, initialContent: String, initialNote: String, submitted: Boolean, attachments: { type: Array, default: () => [] } })
const emit = defineEmits(['submitted']); const picker = ref(); const files = ref([]); const saving = ref(false); const editing = ref(!props.submitted); const note = ref(props.initialNote || ''); const allowed = ['zip', 'rar', 'pdf', 'doc', 'docx', 'ppt', 'pptx']
const formatSize = (value) => Number(value || 0) < 1048576 ? `${Math.max(1, Math.ceil(Number(value || 0) / 1024))} KB` : `${(Number(value) / 1048576).toFixed(1)} MB`
const download = async (file) => { try { await downloadAuthenticatedFile(file) } catch (error) { ElMessage.error(getErrorMessage(error, '文件下载失败')) } }
const pickFiles = (event) => { for (const file of event.target.files) { const extension = file.name.split('.').pop().toLowerCase(); if (!allowed.includes(extension) || file.size > 20971520) { ElMessage.error(`文件格式或大小不符合要求：${file.name}`); continue } if (!files.value.some((item) => item.name === file.name && item.size === file.size)) files.value.push(file) } event.target.value = '' }
const removeFile = (file) => { files.value = files.value.filter((item) => item !== file) }
const submit = async () => { saving.value = true; try { const attachments = []; for (const file of files.value) attachments.push((await uploadStudentFile(file)).storageKey); await submitAssignment(props.assignmentId, { content: props.initialContent || '', note: note.value, attachments, contributionScores: props.contributionScores }); files.value = []; editing.value = false; ElMessage.success('作业已提交'); emit('submitted') } catch (error) { ElMessage.error(getErrorMessage(error, '提交失败')) } finally { saving.value = false } }
</script>
<style scoped>
.submit-card{padding:22px;border:1px solid #e2e9f3;border-radius:13px;background:#fff;box-shadow:0 5px 18px #2c486e0d}h2{margin:0 0 20px;font-size:17px}input{display:none}.drop{display:flex;width:100%;min-height:170px;align-items:center;justify-content:center;flex-direction:column;border:1px dashed #9eb9ea;border-radius:11px;color:#5a83dd;background:#fbfdff;cursor:pointer}.drop strong{font-size:32px}.drop b{color:#667891}.drop span{margin:5px}.drop small{margin-top:12px;color:#a0acba;font-size:9px}ul{padding:0;list-style:none}li,.file-download{display:flex;width:100%;box-sizing:border-box;gap:8px;align-items:center;justify-content:space-between;padding:10px;border:1px solid #e5ecf4;border-radius:8px;color:#557bd0;background:#fff}.file-download{margin-bottom:8px;cursor:pointer}.file-download small{color:#8d9aad}li button{border:0;color:#b85a52;background:none}label{position:relative;display:block;margin-top:18px}label>b{display:block;margin-bottom:9px;font-size:12px}textarea{box-sizing:border-box;width:100%;min-height:115px;padding:12px;border:1px solid #dfe7f1;border-radius:9px;resize:vertical}label>small{position:absolute;right:10px;bottom:9px;color:#a4afbc}.actions{display:flex;justify-content:flex-end;gap:10px;margin-top:18px}.primary,.outline{padding:10px 22px;border-radius:99px;cursor:pointer}.primary{min-width:145px;border:0;color:#fff;background:linear-gradient(90deg,#4e7ae8,#7194f0)}.outline{border:1px solid #aec3ec;color:#5279d2;background:#fff}.success{display:flex;justify-content:space-between;margin-bottom:12px;padding:14px;color:#319b70;background:#effaf4}.note{padding:14px;color:#50627a}.note p{color:#7f8ea2;font-size:11px;line-height:1.7}.submit-card>.outline{width:100%}
</style>
