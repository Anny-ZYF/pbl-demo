<template>
  <div class="student-page">
    <header class="heading">
      <button v-if="courseName" type="button" class="back" @click="router.back()">← 返回课程</button>
      <div>
        <p>COURSE SCORES</p>
        <h1>{{ courseName ? `${courseName} · 成绩` : '我的成绩' }}</h1>
        <span>查看已发布的个人成绩和团队贡献</span>
      </div>
    </header>
    <div v-if="errorMessage" class="error">{{ errorMessage }}</div>
    <section class="panel">
      <div class="panel-head"><h2>成绩记录</h2><span>{{ visibleScores.length }} 条</span></div>
      <div v-for="item in visibleScores" :key="item.scoreResultId" class="score-row">
        <div class="score-title">
          <strong>{{ item.assignmentTitle }}</strong>
          <small>{{ item.courseName }} · {{ item.groupName || '未分组' }}</small>
        </div>
        <div class="score-value">
          <span>贡献 {{ ratio(item.contribution) }}</span>
          <b>{{ item.finalScore == null ? '未发布' : Number(item.finalScore).toFixed(2) }}</b>
        </div>
        <button v-if="item.scoreResultId" type="button" class="appeal-button" @click="openAppeal(item)">
          我要申诉
        </button>
      </div>
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
import { appealScore, getScores } from '@/api/student'
import { getErrorMessage } from '@/api/response'

const route = useRoute()
const router = useRouter()
const scores = ref([])
const errorMessage = ref('')
const appealVisible = ref(false)
const appealTarget = ref(null)
const appealReason = ref('')
const appealError = ref('')
const saving = ref(false)

const courseName = computed(() => String(route.query.courseName || ''))
const visibleScores = computed(() => courseName.value
  ? scores.value.filter((item) => item.courseName === courseName.value)
  : scores.value)
const ratio = (value) => value == null ? '—' : `${(Number(value) * 100).toFixed(1)}%`

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
  } catch (error) {
    appealError.value = getErrorMessage(error, '申诉提交失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    scores.value = await getScores()
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
@media(max-width:600px){.heading h1{font-size:25px}.score-row{flex-wrap:wrap;padding:16px}.score-title{flex:1 1 100%}.score-value{text-align:left}.score-value b{font-size:20px}}
</style>
