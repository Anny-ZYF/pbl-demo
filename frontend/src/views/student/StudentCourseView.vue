<template>
  <div class="course-page">
    <div v-if="errorMessage" class="feedback feedback--error"><el-icon><WarningFilled /></el-icon><span>{{ errorMessage }}</span><button type="button" @click="load">重新加载</button></div>
    <div v-if="loading" class="feedback"><el-icon class="is-loading"><Loading /></el-icon><span>正在加载课程...</span></div>
    <template v-else-if="course">
      <section class="course-hero">
        <div class="course-cover"><i></i><i></i><i></i></div>
        <div class="course-intro">
          <div class="course-title-row"><h1>{{ course.name }}</h1><span>{{ courseStatus }}</span></div>
          <div class="course-facts"><span><el-icon><User /></el-icon>{{ course.teacherName || '教师未设置' }}</span><i></i><span><el-icon><Calendar /></el-icon>{{ course.term || '学期未设置' }}</span><i></i><span v-if="course.grade"><el-icon><School /></el-icon>{{ course.grade }}级</span></div>
          <p></p>
          <div class="course-period"><el-icon><Clock /></el-icon>{{ formatDate(course.beginDate) }} 至 {{ formatDate(course.endDate) }}</div>
        </div>
      </section>
      <nav class="course-tabs" aria-label="课程功能"><button v-for="tab in tabs" :key="tab.value" type="button" :class="{ active: activeTab === tab.value }" :disabled="tab.disabled" :title="tab.disabled ? '该功能建设中' : ''" @click="activeTab = tab.value">{{ tab.label }}</button></nav>
      <main v-if="activeTab === 'assignments'" class="assignment-section">
        <header class="section-heading"><div><h2>作业</h2><p>完成课程中的学习任务，及时提交作业</p></div><div class="assignment-tools"><select v-model="sortMode" aria-label="作业排序方式"><option value="published-desc">按发布时间：最新优先</option><option value="published-asc">按发布时间：最早优先</option><option value="deadline-asc">按截止时间：最近优先</option><option value="deadline-desc">按截止时间：最晚优先</option></select><span>{{ filteredAssignments.length }} / {{ assignments.length }} 项</span></div></header>
        <div class="filters" role="group" aria-label="作业状态筛选"><button v-for="filter in filters" :key="filter.value" type="button" :class="{ active: activeFilter === filter.value }" @click="activeFilter = filter.value">{{ filter.label }}<small>{{ filterCount(filter.value) }}</small></button></div>
        <div v-if="filteredAssignments.length" class="assignment-list">
          <article v-for="(item, index) in pagedAssignments" :key="item.id" class="assignment-card" tabindex="0" @click="openAssignment(item.id)" @keydown.enter="openAssignment(item.id)">
            <div class="assignment-icon" :class="`tone-${index % 3}`"><el-icon><Document /></el-icon></div>
            <div class="assignment-main"><h3>{{ item.title }}</h3><p><span>截止时间：{{ formatDeadline(item.deadline) }}</span><i></i><span>作业类型：{{ item.standardGroupSize > 1 ? '小组作业' : '个人作业' }}</span></p><small v-if="item.description">{{ item.description }}</small></div>
            <div class="assignment-actions"><span class="status" :class="statusClass(item)"><el-icon><CircleCheck v-if="item.submitted || item.scorePublished" /><Clock v-else /></el-icon>{{ statusText(item) }}</span><button type="button">查看详情 <el-icon><ArrowRight /></el-icon></button></div>
          </article>
        </div>
        <nav v-if="pageCount > 1" class="pagination" aria-label="作业分页"><button type="button" :disabled="page === 1" @click="page--">上一页</button><button v-for="pageNumber in pageCount" :key="pageNumber" type="button" :class="{ active: page === pageNumber }" @click="page = pageNumber">{{ pageNumber }}</button><button type="button" :disabled="page === pageCount" @click="page++">下一页</button></nav>
        <div v-else class="empty-state"><el-icon><Document /></el-icon><strong>{{ assignments.length ? '当前筛选下没有作业' : '课程暂未发布作业' }}</strong><span>{{ assignments.length ? '请选择其他状态查看作业。' : '教师发布作业后会显示在这里。' }}</span></div>
      </main>
      <main v-else class="assignment-section scores-section">
        <header class="section-heading"><div><h2>成绩</h2><p>查看本课程已发布的个人成绩和团队贡献</p></div><button type="button" class="review-button" :disabled="!courseScores.length" @click="openReview">成绩复核</button></header>
        <div v-if="courseScores.length" class="score-list"><article v-for="item in courseScores" :key="item.scoreResultId"><div><h3>{{ item.assignmentTitle }}</h3><p>{{ item.groupName || '未分组' }} · 贡献 {{ ratio(item.contribution) }}</p><p v-if="appealsByScore[item.scoreResultId]" class="review-state">复核{{ appealLabels[appealsByScore[item.scoreResultId].status] || '已提交' }}<span v-if="appealsByScore[item.scoreResultId].resolution">：{{ appealsByScore[item.scoreResultId].resolution }}</span></p></div><div class="score-values"><span class="group-score">团队成绩 <b>{{ numberScore(item.groupScore) }}</b></span><span class="final-score">最终得分 <b>{{ numberScore(item.finalScore) }}</b></span></div></article></div>
        <div v-else class="empty-state"><el-icon><Medal /></el-icon><strong>本课程暂无已发布成绩</strong><span>教师发布成绩后会显示在这里。</span></div>
      </main>
    </template>

    <div v-if="reviewVisible" class="dialog" role="dialog" aria-modal="true" aria-labelledby="review-title">
      <div class="dialog-card">
        <h2 id="review-title">成绩复核</h2>
        <p class="dialog-hint">选择需要复核的作业成绩，并填写复核理由。成绩发布后 7 天内可申请。</p>
        <div v-if="reviewOptions.length" class="review-options">
          <button v-for="option in reviewOptions" :key="option.item.scoreResultId" type="button" class="review-option" :class="{ active: reviewTarget?.scoreResultId === option.item.scoreResultId }" :disabled="option.disabled" @click="reviewTarget = option.item">
            <span class="review-option-main">
              <strong>{{ option.item.assignmentTitle }}</strong>
              <small>{{ option.item.groupName || '未分组' }} · 最终得分 {{ numberScore(option.item.finalScore) }}</small>
            </span>
            <em v-if="option.disabled">{{ option.reason }}</em>
            <em v-else-if="option.appeal" class="review-option-tag">上次复核{{ appealLabels[option.appeal.status] || '已提交' }}</em>
          </button>
        </div>
        <p v-else class="dialog-hint">本课程暂无可复核的成绩。</p>
        <textarea v-model="reviewReason" rows="5" maxlength="1000" placeholder="请说明需要复核的原因"></textarea>
        <p v-if="reviewError" class="dialog-error">{{ reviewError }}</p>
        <div class="dialog-actions">
          <button type="button" @click="closeReview">取消</button>
          <button type="button" :disabled="reviewSaving || !reviewReason.trim() || !reviewTarget" @click="submitReview">{{ reviewSaving ? '提交中…' : '提交复核申请' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Calendar, CircleCheck, Clock, Document, Loading, Medal, School, User, WarningFilled } from '@element-plus/icons-vue'
import { appealScore, getAssignments, getCourses, getScores, getStudentAppeals } from '@/api/student'
import { getErrorMessage } from '@/api/response'
const route=useRoute(),router=useRouter(),courses=ref([]),assignments=ref([]),scores=ref([]),activeFilter=ref('all'),activeTab=ref('assignments'),sortMode=ref('published-desc'),page=ref(1),loading=ref(false),errorMessage=ref('')
const appealsByScore=ref({}),reviewVisible=ref(false),reviewTarget=ref(null),reviewReason=ref(''),reviewError=ref(''),reviewSaving=ref(false)
const appealLabels={SUBMITTED:'待处理',PROCESSING:'处理中',ACCEPTED:'已接受',REJECTED:'已驳回'}
const course=computed(()=>courses.value.find(item=>String(item.id)===String(route.params.courseId)))
const courseStatus=computed(()=>{const now=new Date(),begin=parseDate(course.value?.beginDate),end=parseDate(course.value?.endDate);if(begin&&now<begin)return '未开始';if(end&&now>end)return '已结束';return '课程进行中'})
const courseDescription=computed(()=>assignments.value.length?`本课程当前包含 ${assignments.value.length} 项作业，请合理安排学习与团队协作进度。`:'查看课程安排，关注后续学习任务与作业通知。')
const filters=[{label:'全部',value:'all'},{label:'待完成',value:'pending'},{label:'已提交',value:'submitted'},{label:'已批改',value:'reviewed'}]
const tabs=[{label:'课程资料',value:'resources',disabled:true},{label:'作业',value:'assignments'},{label:'成绩',value:'scores'}]
const filteredAssignments=computed(()=>assignments.value.filter(item=>activeFilter.value==='all'||assignmentState(item)===activeFilter.value).sort((left,right)=>{const field=sortMode.value.startsWith('published')?'publishedAt':'deadline',direction=sortMode.value.endsWith('asc')?1:-1,leftTime=parseDate(left[field])?.getTime()??(direction===1?Number.MAX_SAFE_INTEGER:0),rightTime=parseDate(right[field])?.getTime()??(direction===1?Number.MAX_SAFE_INTEGER:0);return (leftTime-rightTime)*direction}))
const pageCount=computed(()=>Math.max(1,Math.ceil(filteredAssignments.value.length/8)))
const pagedAssignments=computed(()=>filteredAssignments.value.slice((page.value-1)*8,page.value*8))
const courseScores=computed(()=>scores.value.filter(item=>String(item.courseId)===String(route.params.courseId)))
const REVIEW_WINDOW=7*24*60*60*1000
const reviewOptions=computed(()=>courseScores.value.map(item=>{
  const appeal=appealsByScore.value[item.scoreResultId]
  const pending=Boolean(appeal&&['SUBMITTED','PROCESSING'].includes(appeal.status))
  const published=parseDate(item.publishedAt)
  const expired=!published||Date.now()>published.getTime()+REVIEW_WINDOW
  return {item,appeal,disabled:pending||expired,reason:pending?'复核处理中':(!published?'成绩发布时间缺失':(expired?'已超过 7 天复核期':''))}
}))
const openReview=()=>{reviewTarget.value=reviewOptions.value.find(option=>!option.disabled)?.item||null;reviewReason.value='';reviewError.value='';reviewVisible.value=true}
const closeReview=()=>{reviewVisible.value=false;reviewSaving.value=false}
const loadAppeals=async()=>{
  const latest={}
  for(const row of await getStudentAppeals())latest[row.scoreResultId]??=row
  appealsByScore.value=latest
}
const submitReview=async()=>{
  if(!reviewTarget.value||!reviewReason.value.trim())return
  reviewSaving.value=true;reviewError.value=''
  try{
    await appealScore(reviewTarget.value.scoreResultId,{reason:reviewReason.value.trim()})
    reviewVisible.value=false
    ElMessage.success('复核申请已提交')
    await loadAppeals()
  }catch(error){reviewError.value=getErrorMessage(error,'复核申请提交失败')}finally{reviewSaving.value=false}
}
const parseDate=value=>{if(!value)return null;const date=new Date(value);return Number.isNaN(date.getTime())?null:date}
const formatDate=value=>{const date=parseDate(value);return date?date.toLocaleDateString('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit'}):'未设置'}
const formatDeadline=value=>{const date=parseDate(value);return date?date.toLocaleString('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}):'未设置'}
const assignmentState=item=>item.scorePublished?'reviewed':item.submitted?'submitted':'pending'
// No separate team-formation phase; teamFormation is kept false for API compatibility.
const statusText=item=>item.scorePublished?'已批改':item.submitted?'已提交':(item.deadline&&parseDate(item.deadline)<new Date())?'已截止':'待提交'
const statusClass=item=>`status--${assignmentState(item)}`
const filterCount=value=>value==='all'?assignments.value.length:assignments.value.filter(item=>assignmentState(item)===value).length
const openAssignment=id=>router.push(`/student/assignment/${id}`)
const ratio=value=>value==null?'—':`${(Number(value)*100).toFixed(1)}%`
const numberScore=value=>value==null?'—':Number(value).toFixed(2)
const load=async()=>{loading.value=true;errorMessage.value='';try{const [courseRows,assignmentRows,scoreRows]=await Promise.all([getCourses(),getAssignments(route.params.courseId),getScores()]);courses.value=Array.isArray(courseRows)?courseRows:[];assignments.value=Array.isArray(assignmentRows)?assignmentRows:[];scores.value=Array.isArray(scoreRows)?scoreRows:[];if(!course.value)errorMessage.value='未找到该课程或当前账号无权访问'}catch(error){errorMessage.value=getErrorMessage(error,'课程数据加载失败')}finally{loading.value=false}loadAppeals().catch(()=>{appealsByScore.value={}})}
onMounted(load)
watch([activeFilter,sortMode],()=>{page.value=1})
watch(pageCount,(count)=>{if(page.value>count)page.value=count})
</script>

<style scoped>
.course-page{width:min(1180px,100%);margin:0 auto;color:#2b3c55}.course-hero{display:grid;grid-template-columns:210px minmax(0,1fr);gap:28px;padding:26px;border:1px solid #e0e8f3;border-radius:16px 16px 0 0;background:linear-gradient(120deg,#fff 50%,#f2f7ff);box-shadow:0 7px 24px rgba(39,71,112,.06)}.course-cover{position:relative;display:grid;height:150px;place-items:center;overflow:hidden;border-radius:13px;background:linear-gradient(145deg,#183e91,#406ce2);box-shadow:0 12px 24px rgba(44,82,177,.2)}.course-cover::before,.course-cover::after{position:absolute;border:1px solid rgba(255,255,255,.12);border-radius:18px;content:"";transform:rotate(12deg)}.course-cover::before{width:135px;height:95px}.course-cover::after{width:95px;height:65px}.course-cover>i{position:absolute;width:65px;height:65px;border-radius:50%;background:#4f8bff26}.course-cover>i:nth-of-type(1){top:-22px;left:-12px}.course-cover>i:nth-of-type(2){right:18px;bottom:-32px;width:95px;height:95px}.course-cover>i:nth-of-type(3){top:20px;right:15px;width:18px;height:18px}.course-intro{align-self:center;min-width:0}.course-title-row{display:flex;align-items:center;justify-content:space-between;gap:20px}.course-title-row h1{margin:0;color:#192c51;font-size:28px}.course-title-row>span{padding:7px 15px;border-radius:99px;color:#fff;background:linear-gradient(90deg,#5282ee,#7899ee);font-size:11px}.course-facts{display:flex;align-items:center;gap:14px;margin-top:14px;color:#72829b;font-size:12px}.course-facts span{display:flex;align-items:center;gap:6px}.course-facts i{width:1px;height:12px;background:#dce4ee}.course-intro>p{margin:17px 0 12px;color:#718198;font-size:13px}.course-period{display:flex;align-items:center;gap:7px;color:#8b99aa;font-size:11px}.course-tabs{display:flex;gap:48px;padding:0 28px;border:1px solid #e3eaf3;border-top:0;background:#fff}.course-tabs button{position:relative;padding:19px 4px 17px;border:0;color:#66758c;background:none;font-size:13px;font-weight:600;cursor:pointer}.course-tabs button.active{color:#356fe1}.course-tabs button.active::after{position:absolute;right:0;bottom:0;left:0;height:3px;background:#4d7ff0;content:""}.course-tabs button:disabled{color:#a8b1be}.assignment-section{padding:30px 28px 34px;border:1px solid #e3eaf3;border-top:0;border-radius:0 0 16px 16px;background:#fff}.section-heading{display:flex;align-items:end;justify-content:space-between}.section-heading h2{margin:0;font-size:21px}.section-heading p{margin:7px 0 0;color:#97a3b2;font-size:12px}.section-heading>span{color:#8291a4;font-size:11px}.filters{display:flex;gap:10px;margin:27px 0 20px}.filters button{display:flex;align-items:center;gap:7px;padding:8px 15px;border:1px solid #e3e9f2;border-radius:99px;color:#77869b;background:#fff}.filters button small{display:grid;min-width:16px;height:16px;place-items:center;border-radius:50%;background:#f0f3f8}.filters button.active{color:#fff;background:#527eea}.assignment-list,.score-list{display:grid;gap:12px}.assignment-card,.score-list article{display:grid;grid-template-columns:58px minmax(0,1fr) auto;gap:17px;align-items:center;padding:18px 20px;border:1px solid #e5ebf4;border-radius:12px}.assignment-card{cursor:pointer}.assignment-icon{display:grid;width:52px;height:52px;place-items:center;border-radius:11px;color:#4f7fec;background:#edf3ff;font-size:24px}.tone-1{color:#35a98e;background:#e9f8f4}.tone-2{color:#7259de;background:#f0edff}.assignment-main h3,.score-list h3{margin:0;font-size:14px}.assignment-main p,.score-list p{color:#8795a8;font-size:11px}.assignment-actions{display:grid;justify-items:end;gap:13px}.status{padding:5px 9px;border-radius:99px;font-size:10px}.status--pending{color:#d38831;background:#fff4e6}.status--submitted{color:#347bd3;background:#eaf3ff}.status--reviewed{color:#2e9b73;background:#e8f7f1}.assignment-actions button{padding:7px 13px;border:1px solid #8aabef;border-radius:99px;color:#4b77db;background:#fff}.scores-section{min-height:390px}.score-list{margin-top:24px}.score-list article{grid-template-columns:minmax(0,1fr) auto}.score-values{display:flex;gap:28px}.score-values span{display:grid;gap:5px;color:#8997a8;font-size:10px;text-align:right}.score-values .group-score b{color:#1f2937;font-size:18px}.score-values .final-score b{color:#3e75d4;font-size:22px}.feedback,.empty-state{display:flex;min-height:230px;align-items:center;justify-content:center;flex-direction:column;gap:10px;color:#8493a6}.feedback--error{min-height:auto}.empty-state>.el-icon{font-size:34px}@media(max-width:760px){.course-hero{grid-template-columns:1fr}.course-tabs{gap:0;justify-content:space-between}.assignment-card{grid-template-columns:48px 1fr}.assignment-actions{grid-column:1/-1;display:flex;justify-content:space-between}.score-list article{grid-template-columns:1fr}.score-values{justify-content:space-between}.assignment-section{padding:22px 16px}}
</style>
<style scoped>
.assignment-tools{display:flex;align-items:center;gap:12px}.assignment-tools select{height:34px;padding:0 30px 0 11px;border:1px solid #dfe7f1;border-radius:8px;color:#617188;background:#fff;font:inherit;font-size:11px;outline:0;cursor:pointer}.assignment-tools select:focus{border-color:#7fa4e9}.assignment-tools>span{color:#8291a4;font-size:11px}.pagination{display:flex;align-items:center;justify-content:center;gap:7px;margin-top:22px}.pagination button{min-width:32px;height:32px;padding:0 10px;border:1px solid #dfe7f1;border-radius:7px;color:#66768b;background:#fff;font-size:11px;cursor:pointer}.pagination button.active{border-color:#527ee0;color:#fff;background:#527ee0}.pagination button:disabled{color:#b5bfcc;background:#f6f8fb;cursor:not-allowed}@media(max-width:600px){.section-heading{align-items:flex-start;flex-direction:column;gap:14px}.assignment-tools{width:100%;justify-content:space-between}.assignment-tools select{max-width:230px}.pagination{flex-wrap:wrap}}
.assignment-main{min-width:0}.assignment-main h3{display:-webkit-box;overflow:hidden;overflow-wrap:anywhere;-webkit-box-orient:vertical;-webkit-line-clamp:2}.assignment-main>small{display:-webkit-box;max-height:36px;overflow:hidden;color:#94a0b0;font-size:11px;line-height:18px;overflow-wrap:anywhere;-webkit-box-orient:vertical;-webkit-line-clamp:2}.assignment-actions{min-width:106px}.assignment-tools{display:flex;align-items:center;gap:12px}.assignment-tools select{height:34px;padding:0 30px 0 11px;border:1px solid #dfe7f1;border-radius:8px;color:#617188;background:#fff;font:inherit;font-size:11px;outline:0;cursor:pointer}.assignment-tools select:focus{border-color:#7fa4e9}.assignment-tools>span{color:#8291a4;font-size:11px}.pagination{display:flex;align-items:center;justify-content:center;gap:7px;margin-top:22px}.pagination button{min-width:32px;height:32px;padding:0 10px;border:1px solid #dfe7f1;border-radius:7px;color:#66768b;background:#fff;font-size:11px;cursor:pointer}.pagination button.active{border-color:#527ee0;color:#fff;background:#527ee0}.pagination button:disabled{color:#b5bfcc;background:#f6f8fb;cursor:not-allowed}@media(max-width:600px){.section-heading{align-items:flex-start;flex-direction:column;gap:14px}.assignment-tools{width:100%;justify-content:space-between}.assignment-tools select{max-width:230px}.pagination{flex-wrap:wrap}}
</style>
<style scoped>
.scores-section .section-heading{align-items:center}
.review-button{padding:9px 18px;border:1px solid #bcd2ec;border-radius:99px;color:#356798;background:#fff;font-size:12px;white-space:nowrap;cursor:pointer}
.review-button:hover:not(:disabled){background:#f2f7fd}
.review-button:disabled{opacity:.5;cursor:not-allowed}
.review-state{margin:6px 0 0;color:#356798;font-size:11px}
.dialog{position:fixed;z-index:60;inset:0;display:grid;place-items:center;padding:20px;background:#263b5355}
.dialog-card{width:min(560px,100%);max-height:86vh;overflow:auto;padding:24px;border-radius:14px;background:#fff;box-shadow:0 20px 50px rgba(24,44,72,.2)}
.dialog h2{margin:0 0 6px;color:#1f3350;font-size:17px}
.dialog-hint{margin:0 0 16px;color:#7c8da0;font-size:12px;line-height:1.6}
.review-options{display:grid;gap:8px;max-height:250px;margin-bottom:14px;overflow:auto}
.review-option{display:flex;align-items:center;gap:12px;padding:12px 14px;border:1px solid #e3eaf3;border-radius:10px;background:#fff;font:inherit;text-align:left;cursor:pointer}
.review-option:hover:not(:disabled){border-color:#b9cfea;background:#f8fbff}
.review-option.active{border-color:#4d7ff0;background:#f2f7ff;box-shadow:inset 0 0 0 1px #4d7ff0}
.review-option:disabled{background:#fafbfd;cursor:not-allowed}
.review-option-main{display:grid;flex:1;min-width:0;gap:4px}
.review-option-main strong{color:#334155;font-size:13px;font-weight:600;overflow-wrap:anywhere}
.review-option:disabled .review-option-main strong{color:#a5b1c0}
.review-option-main small{color:#94a3b8;font-size:11px}
.review-option em{flex:0 0 auto;color:#b07525;font-size:11px;font-style:normal;white-space:nowrap}
.review-option .review-option-tag{color:#356798}
.dialog textarea{box-sizing:border-box;width:100%;min-height:110px;padding:11px 12px;border:1px solid #dfe7f1;border-radius:9px;outline:0;font:inherit;font-size:13px;line-height:1.6;resize:vertical}
.dialog textarea:focus{border-color:#7fa4e9}
.dialog-error{margin:10px 0 0;color:#b42318;font-size:12px}
.dialog-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:16px}
.dialog-actions button{padding:9px 18px;border-radius:8px;font-size:12px;cursor:pointer}
.dialog-actions button:first-child{border:1px solid #dce5ef;color:#60738c;background:#fff}
.dialog-actions button:last-child{border:0;color:#fff;background:#527de0}
.dialog-actions button:disabled{opacity:.5;cursor:not-allowed}
@media(max-width:600px){.review-button{width:100%}.review-option{flex-wrap:wrap}}
</style>
