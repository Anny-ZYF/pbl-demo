<template>
  <div class="page">
    <div class="crumb"><button @click="router.back()">返回作业</button><span>/</span><strong>{{ detail?.title || '作业详情' }}</strong></div>
    <div v-if="errorMessage" class="error">{{ errorMessage }}</div>
    <section v-if="detail" class="panel">
      <header><div><p>ASSIGNMENT DETAIL</p><h1>{{ detail.title }}</h1><span>截止时间：{{ formatDate(detail.deadline) }}</span></div><b class="status">{{ formation ? '组队中' : reviewed ? '已批阅' : '小组已锁定' }}</b></header>
      <div class="content">
        <div class="top-sections">
          <section class="plain-section"><h2>作业要求</h2><p>{{ detail.description || '暂无详细作业说明' }}</p></section>
          <section v-if="graded" class="grade-panel"><h2>我的成绩</h2><p v-if="!reviewed" class="unpublished-note">教师还未发布该作业成绩</p><div v-else class="grades"><div><small>个人成绩</small><strong>{{ score(currentScore?.finalScore) }}</strong></div><div><small>团队成绩</small><strong>{{ score(currentScore?.groupScore) }}</strong></div><button class="appeal" @click="appealVisible=true">我要申诉</button></div></section>
        </div>
        <section v-if="detail.parts?.length" class="plain-section"><h2>评价分支</h2><div v-for="part in detail.parts" :key="part.id" class="part"><span>{{ part.name }}</span><b>{{ Number(part.weight * 100).toFixed(0) }}%</b><small>{{ part.description || '暂无说明' }}</small></div></section>
        <section><h2>我的小组</h2><div v-if="detail.groupName" class="team"><strong>{{ detail.groupName }}</strong><label v-for="member in detail.members" :key="member.studentId"><span>{{ member.name }}（{{ member.studentNo }}）</span><input v-if="!reviewed" v-model.number="contributionScores[member.studentId]" type="number" min="0" max="100" step="0.1" placeholder="贡献占比 %"><span v-else class="contribution-readonly">贡献占比：{{ member.contributionRatio == null ? '—' : Number(member.contributionRatio).toFixed(1) + '%' }}</span></label></div><el-empty v-else description="暂未加入小组" /></section>
        <section v-if="reviewed" class="readonly"><h2>作业提交内容</h2><p>{{ detail.content || '本次提交使用附件或暂无正文内容' }}</p></section><StudentSubmissionPanel v-else :assignment-id="route.params.assignmentId" :contribution-scores="contributionScores" :initial-content="detail.content" :initial-note="detail.submitNote" @submitted="load" />
      </div>
    </section><div v-else class="loading">正在加载…</div>
    <div v-if="appealVisible" class="dialog"><div class="dialog-card"><h2>提交成绩申诉</h2><textarea v-model="appealReason" placeholder="请填写申诉理由" /><button class="secondary" @click="appealVisible=false">取消</button><button class="primary" :disabled="!appealReason.trim()" @click="submitAppeal">提交申诉</button></div></div>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import StudentSubmissionPanel from '@/components/student/StudentSubmissionPanel.vue'
import { getAssignment, getScores, appealScore } from '@/api/student'
import { getErrorMessage } from '@/api/response'
const route=useRoute(),router=useRouter(),detail=ref(null),scores=ref([]),contributionScores=ref({}),errorMessage=ref(''),appealVisible=ref(false),appealReason=ref('')
const formation=computed(()=>detail.value?.teamFormation===true||detail.value?.teamFormation===1),reviewed=computed(()=>detail.value?.scorePublished===true||detail.value?.scorePublished===1),graded=computed(()=>reviewed.value||detail.value?.teacherScore!=null||['GRADED','SCORED','REVIEWED'].includes(detail.value?.submissionStatus)),currentScore=computed(()=>scores.value.find(item=>Number(item.assignmentId)===Number(route.params.assignmentId)))
const score=v=>v==null?'—':Number(v).toFixed(2),formatDate=v=>v?new Date(v).toLocaleString('zh-CN'):'未设置'
const load=async()=>{detail.value=await getAssignment(route.params.assignmentId);scores.value=await getScores();detail.value.members?.forEach(m=>{if(!(m.studentId in contributionScores.value))contributionScores.value[m.studentId]=null})}
const submitAppeal=async()=>{try{await appealScore(currentScore.value.scoreResultId,{reason:appealReason.value});appealVisible.value=false;ElMessage.success('申诉已提交')}catch(e){errorMessage.value=getErrorMessage(e,'申诉提交失败')}}
onMounted(()=>load().catch(e=>errorMessage.value=getErrorMessage(e,'作业详情加载失败')))
</script>
<style scoped>
.page{max-width:980px;margin:0 auto}.crumb{display:flex;gap:8px;margin-bottom:24px;color:#8795a6}.crumb button{border:0;background:none;color:inherit;cursor:pointer}.panel{overflow:hidden;border-radius:9px;background:#fff;box-shadow:0 3px 12px #263b5310}header{display:flex;justify-content:space-between;padding:26px;border-bottom:1px solid #edf1f4}header p{color:#6b9ed8;letter-spacing:1.5px;font-size:11px}h1{margin:8px 0;color:#263b53}.status{padding:6px 13px;height:max-content;border-radius:99px;color:#356798;background:#eaf2fa}.content{padding:26px}.top-sections{display:flex;flex-direction:column;gap:18px}.plain-section,.grade-panel{padding:0;border:0;background:transparent}.plain-section p,.readonly p{color:#64748b;line-height:1.8}.grades{display:flex;align-items:center;gap:42px}.grades div{display:flex;align-items:baseline;gap:12px}.grades small{color:#334155;font-size:17px;font-weight:600}.grades strong{color:#356798;font-size:30px;font-weight:700}.appeal{margin-left:8px;padding:9px 14px;border:0;border-radius:6px;color:#fff;background:#d78842;cursor:pointer}.part{display:grid;grid-template-columns:1fr auto;gap:5px;padding:12px 0;border-bottom:1px solid #edf1f4}.part b{color:#356798}.part small{grid-column:1/-1;color:#98a5b3}.team{display:flex;flex-direction:column;gap:8px}.team strong,.team label{display:flex;align-items:center;justify-content:space-between;padding:9px 13px;border-radius:6px;background:#f3f7fa;color:#52657a}.team strong{color:#356798;background:#eaf2fa}.contribution-readonly{color:#52657a;font-size:13px}.readonly{margin-top:20px}.error{padding:12px;color:#b42318;background:#fff5f5}.loading{text-align:center;padding:40px;color:#8795a6}.dialog{position:fixed;inset:0;display:grid;place-items:center;background:#263b5340}.dialog-card{width:420px;padding:24px;border-radius:10px;background:#fff}.dialog-card textarea{width:100%;min-height:120px;margin:12px 0;padding:10px;box-sizing:border-box;border:1px solid #dbe3ea;border-radius:6px}.primary,.secondary{padding:10px 17px;border:0;border-radius:6px;cursor:pointer}.primary{color:#fff;background:#356798}.secondary{color:#356798;background:#eaf2fa}@media(max-width:700px){.grades{flex-wrap:wrap}}
</style>

