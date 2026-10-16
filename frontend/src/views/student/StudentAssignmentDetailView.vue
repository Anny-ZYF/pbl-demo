<template>
  <div class="page"><button class="back" @click="router.back()">← 返回作业列表</button><div v-if="errorMessage" class="error">{{ errorMessage }}</div>
    <template v-if="detail"><header><div><div class="title"><h1>{{ detail.title }}</h1><span :class="statusClass">{{ statusText }}</span></div><p>截止时间：{{ formatDate(detail.deadline) }}　　作业类型：{{ assignmentType }}</p></div><small v-if="submitted">当前为最新提交</small></header>
      <main><div class="left"><section class="card"><h2>作业要求</h2><p class="description">{{ detail.description || '暂无详细作业说明' }}</p><div v-if="detail.parts?.length" class="rubric"><h3>评分标准</h3><div v-for="part in detail.parts" :key="part.id"><span>{{ part.name }}</span><b>{{ Number(part.weight * 100).toFixed(0) }}%</b><small>{{ part.description }}</small><i><em :style="{ width: `${Number(part.weight) * 100}%` }"></em></i></div></div></section>
        <section v-if="detail.groupName" class="card team"><h2>我的小组</h2><strong>{{ detail.groupName }}</strong><label v-for="member in detail.members" :key="member.studentId"><span>{{ member.name }}（{{ member.studentNo }}）</span><input v-if="!reviewed" v-model.number="contributionScores[member.studentId]" type="number" min="0" max="100" placeholder="贡献占比 %"><em v-else>贡献占比：{{ member.contributionRatio == null ? '—' : Number(member.contributionRatio).toFixed(1) + '%' }}</em></label></section>
        </div><div class="right"><section v-if="formation" class="card grouping"><div class="grouping-head"><div><h2>作业组队</h2><p>组队期结束后才能提交作业</p></div><span>{{ formationDeadline }}</span></div><template v-if="detail.groupName"><div class="current-group"><strong>{{ detail.groupName }}</strong><small>{{ detail.members?.length || 0 }} / {{ Number(detail.standardGroupSize || 1) + 1 }} 人</small></div><div class="member-list"><span v-for="member in detail.members" :key="member.studentId">{{ member.name }}（{{ member.studentNo }}）</span></div><button class="danger-button" :disabled="groupOperating" @click="leaveGroup">退出当前小组</button></template><template v-else><div class="group-actions"><button class="primary-button" :disabled="groupOperating" @click="createGroup">创建小组</button><span>或加入已有小组</span></div><div v-if="groups.length" class="available-groups"><article v-for="group in groups" :key="group.id"><div><strong>{{ group.name }}</strong><small>{{ group.memberCount }} / {{ group.maxMembers }} 人</small></div><button :disabled="groupOperating || Number(group.memberCount) >= Number(group.maxMembers)" @click="joinGroup(group.groupNo)">{{ Number(group.memberCount) >= Number(group.maxMembers) ? '已满' : '加入' }}</button></article></div><el-empty v-else description="暂无可加入的小组，可创建新小组" /></template></section><StudentSubmissionPanel v-else-if="!reviewed && detail.groupName" :assignment-id="route.params.assignmentId" :contribution-scores="contributionScores" :initial-content="detail.content" :initial-note="detail.submitNote" :submitted="submitted" :attachments="detail.attachments" @submitted="load" />
        <section v-else-if="!reviewed" class="card locked-group"><h2>未加入有效小组</h2><p>组队期已经结束，当前无法提交作业。请联系任课教师处理小组归属。</p></section>
        <section v-else class="card readonly"><h2>已提交内容</h2><b>✓ 成绩已发布</b><p>{{ detail.submitNote || detail.content || '本次提交暂无说明' }}</p><button v-for="file in detail.attachments" :key="file.id" @click="download(file)">📄 {{ file.originalName }} <span>下载</span></button></section>
        <section v-if="graded" class="card grade"><h2>教师反馈</h2><div v-if="reviewed"><span>个人成绩 <b>{{ score(currentScore?.finalScore) }}</b></span><span>团队成绩 <b>{{ score(currentScore?.groupScore) }}</b></span><button @click="appealVisible = true">我要申诉</button></div><p>{{ detail.teacherComment || (reviewed ? '教师暂未填写评语' : '成绩尚未发布') }}</p></section></div>
      </main></template><div v-else class="loading">正在加载作业...</div>
    <div v-if="appealVisible" class="dialog"><div><h2>提交成绩申诉</h2><textarea v-model="appealReason" placeholder="请填写申诉理由"></textarea><button @click="appealVisible = false">取消</button><button :disabled="!appealReason.trim()" @click="submitAppeal">提交申诉</button></div></div>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import StudentSubmissionPanel from '@/components/student/StudentSubmissionPanel.vue'
import { appealScore, createStudentGroup, getAssignment, getAssignmentGroups, getScores, joinStudentGroup, leaveStudentGroup } from '@/api/student'
import { getErrorMessage } from '@/api/response'
import { saveRecentProject } from '@/utils/studentRecentProject'
import { downloadAuthenticatedFile } from '@/utils/downloadFile'
const route = useRoute(); const router = useRouter(); const detail = ref(null); const scores = ref([]); const groups = ref([]); const groupOperating = ref(false); const contributionScores = ref({}); const errorMessage = ref(''); const appealVisible = ref(false); const appealReason = ref('')
const reviewed = computed(() => detail.value?.scorePublished === true || detail.value?.scorePublished === 1); const submitted = computed(() => Boolean(detail.value?.submissionStatus || detail.value?.content || detail.value?.attachments?.length)); const graded = computed(() => reviewed.value || detail.value?.teacherScore != null); const formationEnd = computed(() => detail.value?.publishedAt ? new Date(new Date(detail.value.publishedAt).getTime() + 2 * 86400000) : null); const formation = computed(() => formationEnd.value && Date.now() < formationEnd.value.getTime()); const formationDeadline = computed(() => formationEnd.value ? `组队截止：${formatDate(formationEnd.value)}` : '组队时间未设置'); const currentScore = computed(() => scores.value.find((item) => Number(item.assignmentId) === Number(route.params.assignmentId))); const statusText = computed(() => reviewed.value ? '已批阅' : submitted.value ? '已提交' : formation.value ? '组队中' : '待提交'); const statusClass = computed(() => reviewed.value ? 'reviewed' : submitted.value ? 'submitted' : formation.value ? 'forming' : 'pending'); const assignmentType = computed(() => detail.value?.standardGroupSize ? `${detail.value.standardGroupSize}人作业` : '人数未设置')
const score = (value) => value == null ? '—' : Number(value).toFixed(2); const formatDate = (value) => value ? new Date(value).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }) : '未设置'; const download = async (file) => { try { await downloadAuthenticatedFile(file) } catch (error) { ElMessage.error(getErrorMessage(error, '文件下载失败')) } }
const load = async () => { detail.value = await getAssignment(route.params.assignmentId); saveRecentProject({ assignmentId: route.params.assignmentId, courseId: detail.value.courseId, title: detail.value.title, courseName: detail.value.courseName, deadline: detail.value.deadline }); const [scoreRows, groupRows] = await Promise.all([getScores(), formation.value ? getAssignmentGroups(route.params.assignmentId) : Promise.resolve([])]); scores.value = scoreRows; groups.value = groupRows; detail.value.members?.forEach((member) => { if (!(member.studentId in contributionScores.value)) contributionScores.value[member.studentId] = null }) }
const runGroupAction = async (action, successMessage) => { groupOperating.value = true; try { await action(); ElMessage.success(successMessage); await load() } catch (error) { ElMessage.error(getErrorMessage(error, '组队操作失败')) } finally { groupOperating.value = false } }
const createGroup = () => runGroupAction(() => createStudentGroup(route.params.assignmentId), '小组创建成功')
const joinGroup = (groupNo) => runGroupAction(() => joinStudentGroup(route.params.assignmentId, groupNo), '已加入小组')
const leaveGroup = () => runGroupAction(() => leaveStudentGroup(route.params.assignmentId), '已退出小组')
const submitAppeal = async () => { try { await appealScore(currentScore.value.scoreResultId, { reason: appealReason.value }); appealVisible.value = false; ElMessage.success('申诉已提交') } catch (error) { errorMessage.value = getErrorMessage(error, '申诉提交失败') } }
onMounted(() => load().catch((error) => errorMessage.value = getErrorMessage(error, '作业详情加载失败')))
</script>
<style scoped>
.page {
  width: min(1120px, 100%);
  margin: auto;
  color: #30435f;
}

.back {
  margin-bottom: 16px;
  padding: 0;
  border: 0;
  color: #6982a5;
  background: none;
  cursor: pointer;
}

.error {
  margin-bottom: 15px;
  padding: 12px;
  color: #b42318;
  background: #fff1f0;
}

header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 17px;
  padding: 3px 2px;
}
.title {
  display: flex;
  align-items: center;
  gap: 13px;
}
.title h1 {
  margin: 0;
  color: #203454;
  font-size: 25px;
}
.title span {
  padding: 5px 10px;
  border-radius: 99px;
  font-size: 10px;
}

.pending   { color: #d58a31; background: #fff3df; }
.forming   { color: #5279d2; background: #edf3ff; }
.submitted { color: #2f9b70; background: #eaf8f1; }
.reviewed  { color: #477cdc; background: #ebf2ff; }

header p,
header small {
  color: #7e8da1;
  font-size: 11px;
}

main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 0.9fr);
  gap: 18px;
  align-items: stretch;
}
.left,
.right {
  display: grid;
  gap: 18px;
  width: 100%;
}

.grouping-head,.current-group,.available-groups article{display:flex;align-items:center;justify-content:space-between;gap:14px}
.grouping-head{margin-bottom:17px}.grouping-head h2{margin-bottom:5px}.grouping-head p,.grouping-head>span,.current-group small,.available-groups small{margin:0;color:#8795a7;font-size:10px}.current-group{padding:13px 14px;border-radius:9px;color:#426fc9;background:#edf3ff}.member-list{display:grid;gap:7px;margin:12px 0}.member-list span{padding:9px 11px;border-radius:7px;color:#60738c;background:#f7f9fc;font-size:11px}.group-actions{display:flex;align-items:center;gap:12px;margin-bottom:15px;color:#98a4b3;font-size:10px}.primary-button,.danger-button,.available-groups button{padding:8px 14px;border-radius:7px;cursor:pointer}.primary-button{border:0;color:#fff;background:#527de0}.danger-button{width:100%;border:1px solid #e3b7b3;color:#b64f47;background:#fff}.available-groups{display:grid;gap:8px}.available-groups article{padding:11px 12px;border:1px solid #e4ebf4;border-radius:8px}.available-groups article div{display:grid;gap:3px}.available-groups button{border:1px solid #9db7eb;color:#5279d2;background:#fff}.available-groups button:disabled,.primary-button:disabled,.danger-button:disabled{opacity:.5;cursor:not-allowed}.locked-group p{margin:0;color:#7b899b;font-size:12px;line-height:1.7}

.card {
  box-sizing: border-box;
  width: 100% !important;
  max-width: none !important;
  padding: 22px;
  border: 1px solid #e2e9f3;
  border-radius: 13px;
  background: #fff;
  box-shadow: 0 5px 18px #2c486e0d;
}
.card h2 {
  margin: 0 0 19px;
  font-size: 16px;
}

.description {
  color: #64768e;
  font-size: 12px;
  line-height: 1.9;
  white-space: pre-line;
}

.rubric { margin-top: 27px; }
.rubric h3 { font-size: 13px; }
.rubric > div {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 5px;
  padding: 9px 0;
  color: #60738c;
  font-size: 11px;
}
.rubric b { color: #547bd3; }
.rubric small {
  grid-column: 1 / -1;
  color: #9aa6b5;
}
.rubric i {
  grid-column: 1 / -1;
  display: block;
  height: 6px;
  margin-top: 4px;
  overflow: hidden;
  border-radius: 99px;
  background: #edf2f8;
}
.rubric em {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #4f7de3, #83a8f3);
}

.team > strong,
.team label {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 11px;
  border-radius: 8px;
  background: #f6f9fd;
  font-size: 11px;
}
.team > strong {
  margin-bottom: 7px;
  color: #4c73c8;
  background: #edf3ff;
}
.team label + label { margin-top: 6px; }
.team input {
  width: 110px;
  padding: 7px;
  border: 1px solid #dce5ef;
  border-radius: 6px;
}
.team em { font-style: normal; }

.grade > div {
  display: flex;
  align-items: center;
  gap: 20px;
}
.grade span {
  display: grid;
  font-size: 10px;
}
.grade b {
  color: #4575d2;
  font-size: 22px;
}
.grade button {
  margin-left: auto;
  padding: 7px;
  border: 0;
  color: #fff;
  background: #d98c4a;
}
.grade p {
  color: #77879b;
  font-size: 11px;
}

.readonly {
  display: grid;
  gap: 10px;
}
.readonly > b {
  padding: 12px;
  color: #2e9870;
  background: #ebf8f2;
}
.readonly p {
  color: #718198;
  font-size: 11px;
}
.readonly a {
  display: flex;
  justify-content: space-between;
  padding: 10px;
  border: 1px solid #e2e9f2;
  border-radius: 8px;
  color: #526fbd;
  text-decoration: none;
}

.loading {
  text-align: center;
  padding: 50px;
}

.dialog {
  position: fixed;
  z-index: 50;
  inset: 0;
  display: grid;
  place-items: center;
  background: #263b5355;
}
.dialog > div {
  width: min(420px, 90%);
  padding: 24px;
  background: #fff;
}
.dialog textarea {
  box-sizing: border-box;
  width: 100%;
  min-height: 120px;
  margin: 12px 0;
}

@media (max-width: 820px) {
  main { grid-template-columns: 1fr; }
  header { flex-direction: column; }
}

@media (max-width: 520px) {
  .title {
    align-items: flex-start;
    flex-direction: column;
  }
  .card { padding: 17px; }
}</style>
