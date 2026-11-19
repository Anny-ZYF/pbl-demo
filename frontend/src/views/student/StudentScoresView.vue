<template>
  <div class="student-page">
    <header class="heading">
      <button v-if="courseName" type="button" class="back" @click="courseId ? router.push({ name: 'student-course', params: { courseId } }) : router.back()">← 返回课程</button>
      <div>
        <p>COURSE SCORES</p>
        <h1>{{ courseName ? `${courseName} · 成绩` : '我的成绩' }}</h1>
        <span>查看已发布的个人成绩和团队贡献</span>
      </div>
    </header>
    <div v-if="errorMessage" class="error">{{ errorMessage }}</div>
    <div v-if="appealsError" class="error" role="alert">{{ appealsError }}</div>
    <section class="panel">
      <div class="panel-head"><h2>成绩记录</h2><span>{{ visibleScores.length }} 条</span></div>
      <article v-for="item in visibleScores" :key="item.scoreResultId" class="score-record">
      <div class="score-row">
        <div class="score-title">
          <strong>{{ item.assignmentTitle }}</strong>
          <small>{{ item.courseName }} · {{ item.groupName || '未分组' }}</small>
        </div>
        <div class="score-value">
          <span>贡献 {{ ratio(item.contribution) }}</span>
          <b>{{ item.finalScore == null ? '未发布' : Number(item.finalScore).toFixed(2) }}</b>
        </div>
        <button v-if="canAppeal(item)" type="button" class="appeal-button" @click="openAppeal(item)">
          我要申诉
        </button>
      </div>
      <p v-if="appealsByScore[item.scoreResultId]" class="appeal-status">
        申诉{{ appealLabels[appealsByScore[item.scoreResultId].status] || '已提交' }}
        <span v-if="appealsByScore[item.scoreResultId].resolution">：{{ appealsByScore[item.scoreResultId].resolution }}</span>
      </p>
      <details class="score-details">
        <summary>评分明细</summary>
        <dl>
          <dt>团队成绩</dt><dd>{{ score(item.groupScore) }}</dd>
          <dt>规则分</dt><dd>{{ score(item.ruleScore) }}</dd>
          <dt>模型建议</dt><dd>{{ item.modelScore == null ? '未采用模型' : score(item.modelScore) }}</dd>
          <dt>最终成绩</dt><dd>{{ score(item.finalScore) }}</dd>
          <dt>规则版本</dt><dd>{{ item.ruleVersion || '—' }}</dd>
          <dt>模型版本</dt><dd>{{ item.modelScore == null ? '—' : item.modelVersion }}</dd>
          <template v-if="item.reviewReason"><dt>教师调整理由</dt><dd>{{ item.reviewReason }}</dd></template>
        </dl>
        <ul v-if="item.branchDetails.length">
          <li v-for="branch in item.branchDetails" :key="branch.id">
            {{ branch.name }} · 权重 {{ ratio(branch.weight) }} · 本人份额 {{ ratio(branch.share) }}
          </li>
        </ul>
      </details>
      </article>
      <el-empty v-if="!visibleScores.length" description="该课程暂无已发布成绩" />
    </section>

    <el-dialog v-model="appealVisible" title="提交成绩申诉" width="480px">
      <p class="appeal-target">{{ appealTarget?.assignmentTitle || '当前成绩' }}</p>
      <textarea v-model="appealReason" rows="5" maxlength="1000" placeholder="请填写申诉理由" />
      <p v-if="appealError" class="error">{{ appealError }}</p>
      <template #footer>
        <button type="button" class="cancel" @click="appealVisible = false">取消</button>
        <button type="button" class="save" :disabled="saving || !appealReason.trim()" @click="submitAppeal">
          {{ saving ? '提交中…' : '提交申诉' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElDialog, ElEmpty, ElMessage } from 'element-plus'
import { appealScore, getScores, getStudentAppeals } from '@/api/student'
import { getErrorMessage } from '@/api/response'

const route = useRoute()
const router = useRouter()
const scores = ref([])
const errorMessage = ref('')
const appealsError = ref('')
const appealsByScore = ref({})
const appealLabels = { SUBMITTED: '待处理', PROCESSING: '处理中', ACCEPTED: '已接受', REJECTED: '已驳回' }
const appealVisible = ref(false)
const appealTarget = ref(null)
const appealReason = ref('')
const appealError = ref('')
const saving = ref(false)

const courseName = computed(() => String(route.query.courseName || ''))
const courseId = computed(() => String(route.query.courseId || ''))
const visibleScores = computed(() => courseId.value || courseName.value
  ? scores.value.filter(item => courseId.value ? String(item.courseId) === courseId.value : item.courseName === courseName.value)
  : scores.value)
const ratio = (value) => value == null ? '—' : `${(Number(value) * 100).toFixed(1)}%`
const score = value => value == null ? '—' : Number(value).toFixed(2)
const isPending = id => ['SUBMITTED', 'PROCESSING'].includes(appealsByScore.value[id]?.status)
const canAppeal = item => {
  const publishedAt = new Date(item.publishedAt).getTime()
  return item.scoreResultId && !isPending(item.scoreResultId)
    && Number.isFinite(publishedAt) && Date.now() <= publishedAt + 7 * 24 * 60 * 60 * 1000
}

function scoreBranches(value) {
  let snapshot = value.scoringSnapshot
  if (typeof snapshot === 'string') {
    try { snapshot = JSON.parse(snapshot) } catch { return [] }
  }
  // The score snapshot contains every member's allocation. Render only the recipient's share.
  const ownShares = (snapshot?.allocations || []).filter(row => String(row.studentId) === String(value.studentId))
  return (snapshot?.parts || []).map(part => ({ ...part,
    share: ownShares.find(row => String(row.partId) === String(part.id))?.share }))
}

async function loadAppeals() {
  appealsError.value = ''
  try {
    const latest = {}
    for (const item of await getStudentAppeals()) {
      latest[item.scoreResultId] ??= item
    }
    appealsByScore.value = latest
  } catch (error) {
    appealsError.value = getErrorMessage(error, '申诉进度加载失败')
  }
}

const openAppeal = (item) => {
  appealTarget.value = item
  appealReason.value = ''
  appealError.value = ''
  appealVisible.value = true
}

const submitAppeal = async () => {
  if (!appealReason.value.trim() || !appealTarget.value) return
  saving.value = true
  appealError.value = ''
  try {
    await appealScore(appealTarget.value.scoreResultId, { reason: appealReason.value.trim() })
    ElMessage.success('申诉已提交')
    appealVisible.value = false
    await loadAppeals()
  } catch (error) {
    appealError.value = getErrorMessage(error, '申诉提交失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  void loadAppeals()
  try {
    scores.value = (await getScores()).map(item => ({ ...item, branchDetails: scoreBranches(item) }))
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '成绩加载失败')
  }
})
</script>

<style scoped>
.student-page{max-width:1180px;margin:0 auto}
.heading{margin-bottom:27px;padding:4px 2px}
.back{margin-bottom:17px;padding:0;border:0;color:#4f86c6;background:none;cursor:pointer}
.heading p{margin:0 0 7px;color:#4f86c6;font-size:11px;font-weight:700;letter-spacing:1.6px}
.heading h1{margin:0 0 8px;color:#22364d;font-size:30px}
.heading span{color:#7c8da0}
.panel{overflow:hidden;border:1px solid #e3eaf2;border-radius:13px;background:#fff;box-shadow:0 5px 18px rgba(38,59,83,.06)}
.panel-head{display:flex;justify-content:space-between;padding:19px 22px;border-bottom:1px solid #e9eef5;background:#fbfdff}
.panel-head h2{margin:0;color:#263b53;font-size:17px}
.panel-head span{color:#4f86c6;font-size:13px}
.score-row{display:flex;align-items:center;gap:18px;padding:19px 22px;border-bottom:1px solid #edf1f5;transition:background .2s}
.score-row:hover{background:#f8fbff}
.score-row:last-child{border-bottom:0}
.score-title{flex:1;min-width:0}
.score-title strong,.score-title small{display:block}
.score-title strong{color:#334155}
.score-title small{margin-top:6px;color:#94a3b8;font-size:12px}
.score-value{display:grid;gap:5px;text-align:right;color:#7c8da0;font-size:12px}
.score-value b{color:#356798;font-size:23px}
.appeal-button{flex:0 0 auto;padding:8px 15px;border:1px solid #bcd2ec;border-radius:6px;color:#356798;background:#fff;font-size:12px;cursor:pointer}
.appeal-button:hover{background:#f2f7fd}
.appeal-target{margin:0 0 12px;color:#52657a;font-size:13px}
textarea{box-sizing:border-box;width:100%;padding:11px 12px;border:1px solid #dbe3ea;border-radius:7px;outline:0;color:#334155;font:inherit;font-size:13px;line-height:1.6;resize:vertical}
textarea:focus{border-color:#6b9ed8}
.cancel,.save{padding:9px 18px;border-radius:6px;font-size:13px;cursor:pointer}
.cancel{border:1px solid #dbe3ea;color:#5a6b7f;background:#fff}
.save{border:0;color:#fff;background:#356798}
.save:disabled{opacity:.5;cursor:not-allowed}
.error{margin:12px 0 0;padding:12px;border-radius:9px;color:#b42318;background:#fff5f5;font-size:13px}
.score-record { border-bottom: 1px solid #edf1f5; }
.score-record .score-row { border: 0; }
.score-details, .appeal-status { margin: 0 22px 14px; color: #52657a; font-size: 13px; }
.score-details summary { cursor: pointer; color: #356798; }
.score-details dl { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 6px 16px; }
.score-details dd { margin: 0; overflow-wrap: anywhere; }
@media(max-width:600px){.heading h1{font-size:25px}.score-row{flex-wrap:wrap;padding:16px}.score-title{flex:1 1 100%}.score-value{text-align:left}.score-value b{font-size:20px}}
</style>
