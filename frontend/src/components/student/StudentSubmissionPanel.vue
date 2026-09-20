<template>
  <section class="submission">
    <h2>提交作业</h2>
    <textarea v-model="form.content" placeholder="填写作业内容"></textarea>
    <div class="upload"><input ref="picker" type="file" multiple accept=".zip,.pdf,.doc,.docx,.ppt,.pptx" @change="selectFiles" /><button type="button" @click="picker?.click()">选择附件</button><span>支持 ZIP、PDF、Word、PPT，单个文件不超过 20MB</span></div>
    <ul v-if="files.length"><li v-for="file in files" :key="file.name + file.size"><span>{{ file.name }}（{{ formatSize(file.size) }}）</span><button type="button" @click="removeFile(file)">删除</button></li></ul>
    <textarea v-model="form.note" placeholder="备注（可选）"></textarea>
    <button class="primary" :disabled="saving" @click="submit">{{ saving ? '提交中…' : '提交作业' }}</button>
  </section>
</template>
<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { submitAssignment, uploadStudentFile } from '@/api/student'
import { getErrorMessage } from '@/api/response'
const props = defineProps({ assignmentId: [String, Number], contributionScores: { type: Object, default: () => ({}) }, initialContent: String, initialNote: String })
const emit = defineEmits(['submitted']); const picker = ref(); const files = ref([]); const saving = ref(false); const form = ref({ content: props.initialContent || '', note: props.initialNote || '' }); const allowed = ['zip', 'pdf', 'doc', 'docx', 'ppt', 'pptx']
const formatSize = (value) => value < 1024 * 1024 ? `${Math.ceil(value / 1024)}KB` : `${(value / 1024 / 1024).toFixed(1)}MB`
const selectFiles = (event) => { for (const file of event.target.files) { const ext = file.name.split('.').pop().toLowerCase(); if (!allowed.includes(ext)) { ElMessage.error(`不支持文件：${file.name}`); continue }; if (file.size > 20 * 1024 * 1024) { ElMessage.error(`文件超过20MB：${file.name}`); continue }; if (!files.value.some((item) => item.name === file.name && item.size === file.size)) files.value.push(file) }; event.target.value = '' }
const removeFile = (file) => { files.value = files.value.filter((item) => item !== file) }
const submit = async () => { saving.value = true; try { const attachments = []; for (const file of files.value) attachments.push((await uploadStudentFile(file)).storageKey); await submitAssignment(props.assignmentId, { content: form.value.content, note: form.value.note, attachments, contributionScores: props.contributionScores }); ElMessage.success('作业已提交'); emit('submitted') } catch (error) { ElMessage.error(getErrorMessage(error, '提交失败')) } finally { saving.value = false } }
</script>
<style scoped>
.submission{padding:0}.submission h2{margin:0 0 15px;color:#334155;font-size:17px}.submission textarea{box-sizing:border-box;width:100%;min-height:108px;margin-bottom:13px;padding:13px 14px;border:1px solid #dbe3ea;border-radius:8px;resize:vertical;color:#334155;background:#fbfdff;font:inherit;outline:none;transition:border-color .2s,box-shadow .2s}.submission textarea:focus{border-color:#6b9ed8;background:#fff;box-shadow:0 0 0 3px rgba(107,158,216,.12)}.upload{display:flex;align-items:center;gap:12px;margin-bottom:12px;padding:13px;border:1px dashed #bfd0e2;border-radius:9px;background:#f7fafe}.upload input{display:none}.upload button,.primary{padding:10px 18px;border:0;border-radius:7px;color:#fff;background:#356798;cursor:pointer;transition:background .2s}.upload button:hover,.primary:hover{background:#285a8c}.upload span{color:#8795a6;font-size:12px}.submission ul{margin:0 0 13px;padding:0;border:1px solid #e6edf4;border-radius:8px;list-style:none}.submission li{display:flex;justify-content:space-between;padding:10px 12px;border-bottom:1px solid #edf1f4;color:#52657a;font-size:13px}.submission li:last-child{border-bottom:0}.submission li button{border:0;color:#b42318;background:none;cursor:pointer}.primary:disabled{opacity:.5;cursor:not-allowed}@media(max-width:600px){.upload{align-items:flex-start;flex-direction:column}.primary{width:100%}}
</style>

