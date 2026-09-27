<template>
  <div class="course-page">
    <div v-if="errorMessage" class="feedback feedback--error"><el-icon><WarningFilled /></el-icon><span>{{ errorMessage }}</span><button type="button" @click="load">重新加载</button></div>
    <div v-if="loading" class="feedback"><el-icon class="is-loading"><Loading /></el-icon><span>正在加载课程...</span></div>
    <template v-else-if="course">
      <section class="course-hero">
        <div class="course-cover"><div class="code-symbol"><span>&lt;</span><b>/</b><span>&gt;</span></div><i></i><i></i><i></i></div>
        <div class="course-intro">
          <div class="course-title-row"><h1>{{ course.name }}</h1><span>{{ courseStatus }}</span></div>
          <div class="course-facts"><span><el-icon><User /></el-icon>{{ course.teacherName || '教师未设置' }}</span><i></i><span><el-icon><Calendar /></el-icon>{{ course.term || '学期未设置' }}</span><i></i><span v-if="course.grade"><el-icon><School /></el-icon>{{ course.grade }}</span></div>
          <p>{{ courseDescription }}</p>
          <div class="course-period"><el-icon><Clock /></el-icon>{{ formatDate(course.beginDate) }} 至 {{ formatDate(course.endDate) }}</div>
        </div>
      </section>
      <nav class="course-tabs" aria-label="课程功能"><button v-for="tab in tabs" :key="tab.value" type="button" :class="{ active: activeTab === tab.value }" :disabled="tab.disabled" :title="tab.disabled ? '该功能建设中' : ''" @click="activeTab = tab.value">{{ tab.label }}</button></nav>
      <main v-if="activeTab === 'assignments'" class="assignment-section">
        <header class="section-heading"><div><h2>作业</h2><p>完成课程中的学习任务，及时提交作业</p></div><span>{{ filteredAssignments.length }} / {{ assignments.length }} 项</span></header>
        <div class="filters" role="group" aria-label="作业状态筛选"><button v-for="filter in filters" :key="filter.value" type="button" :class="{ active: activeFilter === filter.value }" @click="activeFilter = filter.value">{{ filter.label }}<small>{{ filterCount(filter.value) }}</small></button></div>
        <div v-if="filteredAssignments.length" class="assignment-list">
          <article v-for="(item, index) in filteredAssignments" :key="item.id" class="assignment-card" tabindex="0" @click="openAssignment(item.id)" @keydown.enter="openAssignment(item.id)">
            <div class="assignment-icon" :class="`tone-${index % 3}`"><el-icon><Document /></el-icon></div>
            <div class="assignment-main"><h3>{{ item.title }}</h3><p><span>截止时间：{{ formatDeadline(item.deadline) }}</span><i></i><span>作业类型：{{ item.standardGroupSize > 1 ? '小组作业' : '个人作业' }}</span></p><small v-if="item.description">{{ item.description }}</small></div>
            <div class="assignment-actions"><span class="status" :class="statusClass(item)"><el-icon><CircleCheck v-if="item.submitted || item.scorePublished" /><Clock v-else /></el-icon>{{ statusText(item) }}</span><button type="button">查看详情 <el-icon><ArrowRight /></el-icon></button></div>
          </article>
        </div>
        <div v-else class="empty-state"><el-icon><Document /></el-icon><strong>{{ assignments.length ? '当前筛选下没有作业' : '课程暂未发布作业' }}</strong><span>{{ assignments.length ? '请选择其他状态查看作业。' : '教师发布作业后会显示在这里。' }}</span></div>
      </main>
      <main v-else class="assignment-section scores-section">
        <header class="section-heading"><div><h2>成绩</h2><p>查看本课程已发布的个人成绩和团队贡献</p></div><span>{{ courseScores.length }} 条</span></header>
        <div v-if="courseScores.length" class="score-list"><article v-for="item in courseScores" :key="item.scoreResultId"><div><h3>{{ item.assignmentTitle }}</h3><p>{{ item.groupName || '未分组' }} · 贡献 {{ ratio(item.contribution) }}</p></div><div class="score-values"><span class="group-score">团队成绩 <b>{{ numberScore(item.groupScore) }}</b></span><span class="final-score">最终得分 <b>{{ numberScore(item.finalScore) }}</b></span></div></article></div>
        <div v-else class="empty-state"><el-icon><Medal /></el-icon><strong>本课程暂无已发布成绩</strong><span>教师发布成绩后会显示在这里。</span></div>
      </main>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Calendar, CircleCheck, Clock, Document, Loading, Medal, School, User, WarningFilled } from '@element-plus/icons-vue'
import { getAssignments, getCourses, getScores } from '@/api/student'
import { getErrorMessage } from '@/api/response'
const route=useRoute(),router=useRouter(),courses=ref([]),assignments=ref([]),scores=ref([]),activeFilter=ref('all'),activeTab=ref('assignments'),loading=ref(false),errorMessage=ref('')
const course=computed(()=>courses.value.find(item=>String(item.id)===String(route.params.courseId)))
const courseStatus=computed(()=>{const now=new Date(),begin=parseDate(course.value?.beginDate),end=parseDate(course.value?.endDate);if(begin&&now<begin)return '未开始';if(end&&now>end)return '已结束';return '课程进行中'})
const courseDescription=computed(()=>assignments.value.length?`本课程当前包含 ${assignments.value.length} 项作业，请合理安排学习与团队协作进度。`:'查看课程安排，关注后续学习任务与作业通知。')
const filters=[{label:'全部',value:'all'},{label:'待完成',value:'pending'},{label:'已提交',value:'submitted'},{label:'已批改',value:'reviewed'}]
const tabs=[{label:'课程资料',value:'resources',disabled:true},{label:'作业',value:'assignments'},{label:'成绩',value:'scores'}]
const filteredAssignments=computed(()=>assignments.value.filter(item=>activeFilter.value==='all'||assignmentState(item)===activeFilter.value))
const courseScores=computed(()=>scores.value.filter(item=>item.courseName===course.value?.name))
const parseDate=value=>{if(!value)return null;const date=new Date(value);return Number.isNaN(date.getTime())?null:date}
const formatDate=value=>{const date=parseDate(value);return date?date.toLocaleDateString('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit'}):'未设置'}
const formatDeadline=value=>{const date=parseDate(value);return date?date.toLocaleString('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}):'未设置'}
const assignmentState=item=>item.scorePublished?'reviewed':item.submitted?'submitted':'pending'
const statusText=item=>item.scorePublished?'已批改':item.submitted?'已提交':item.teamFormation?'组队中':'待提交'
const statusClass=item=>`status--${assignmentState(item)}`
const filterCount=value=>value==='all'?assignments.value.length:assignments.value.filter(item=>assignmentState(item)===value).length
const openAssignment=id=>router.push(`/student/assignment/${id}`)
const ratio=value=>value==null?'—':`${(Number(value)*100).toFixed(1)}%`
const numberScore=value=>value==null?'—':Number(value).toFixed(2)
const load=async()=>{loading.value=true;errorMessage.value='';try{const [courseRows,assignmentRows,scoreRows]=await Promise.all([getCourses(),getAssignments(route.params.courseId),getScores()]);courses.value=Array.isArray(courseRows)?courseRows:[];assignments.value=Array.isArray(assignmentRows)?assignmentRows:[];scores.value=Array.isArray(scoreRows)?scoreRows:[];if(!course.value)errorMessage.value='未找到该课程或当前账号无权访问'}catch(error){errorMessage.value=getErrorMessage(error,'课程数据加载失败')}finally{loading.value=false}}
onMounted(load)
</script>

<style scoped>
.course-page{width:min(1180px,100%);margin:0 auto;color:#2b3c55}.course-hero{display:grid;grid-template-columns:210px minmax(0,1fr);gap:28px;padding:26px;border:1px solid #e0e8f3;border-radius:16px 16px 0 0;background:linear-gradient(120deg,#fff 50%,#f2f7ff);box-shadow:0 7px 24px rgba(39,71,112,.06)}.course-cover{position:relative;display:grid;height:150px;place-items:center;overflow:hidden;border-radius:13px;background:linear-gradient(145deg,#183e91,#406ce2);box-shadow:0 12px 24px rgba(44,82,177,.2)}.course-cover::before,.course-cover::after{position:absolute;border:1px solid rgba(255,255,255,.12);border-radius:18px;content:"";transform:rotate(12deg)}.course-cover::before{width:135px;height:95px}.course-cover::after{width:95px;height:65px}.code-symbol{position:relative;z-index:2;color:#72d8ff;font-size:34px;font-weight:700;letter-spacing:3px}.code-symbol b{color:#fff}.course-cover>i{position:absolute;width:65px;height:65px;border-radius:50%;background:#4f8bff26}.course-cover>i:nth-of-type(1){top:-22px;left:-12px}.course-cover>i:nth-of-type(2){right:18px;bottom:-32px;width:95px;height:95px}.course-cover>i:nth-of-type(3){top:20px;right:15px;width:18px;height:18px}.course-intro{align-self:center;min-width:0}.course-title-row{display:flex;align-items:center;justify-content:space-between;gap:20px}.course-title-row h1{margin:0;color:#192c51;font-size:28px}.course-title-row>span{padding:7px 15px;border-radius:99px;color:#fff;background:linear-gradient(90deg,#5282ee,#7899ee);font-size:11px}.course-facts{display:flex;align-items:center;gap:14px;margin-top:14px;color:#72829b;font-size:12px}.course-facts span{display:flex;align-items:center;gap:6px}.course-facts i{width:1px;height:12px;background:#dce4ee}.course-intro>p{margin:17px 0 12px;color:#718198;font-size:13px}.course-period{display:flex;align-items:center;gap:7px;color:#8b99aa;font-size:11px}.course-tabs{display:flex;gap:48px;padding:0 28px;border:1px solid #e3eaf3;border-top:0;background:#fff}.course-tabs button{position:relative;padding:19px 4px 17px;border:0;color:#66758c;background:none;font-size:13px;font-weight:600;cursor:pointer}.course-tabs button.active{color:#356fe1}.course-tabs button.active::after{position:absolute;right:0;bottom:0;left:0;height:3px;background:#4d7ff0;content:""}.course-tabs button:disabled{color:#a8b1be}.assignment-section{padding:30px 28px 34px;border:1px solid #e3eaf3;border-top:0;border-radius:0 0 16px 16px;background:#fff}.section-heading{display:flex;align-items:end;justify-content:space-between}.section-heading h2{margin:0;font-size:21px}.section-heading p{margin:7px 0 0;color:#97a3b2;font-size:12px}.section-heading>span{color:#8291a4;font-size:11px}.filters{display:flex;gap:10px;margin:27px 0 20px}.filters button{display:flex;align-items:center;gap:7px;padding:8px 15px;border:1px solid #e3e9f2;border-radius:99px;color:#77869b;background:#fff}.filters button small{display:grid;min-width:16px;height:16px;place-items:center;border-radius:50%;background:#f0f3f8}.filters button.active{color:#fff;background:#527eea}.assignment-list,.score-list{display:grid;gap:12px}.assignment-card,.score-list article{display:grid;grid-template-columns:58px minmax(0,1fr) auto;gap:17px;align-items:center;padding:18px 20px;border:1px solid #e5ebf4;border-radius:12px}.assignment-card{cursor:pointer}.assignment-icon{display:grid;width:52px;height:52px;place-items:center;border-radius:11px;color:#4f7fec;background:#edf3ff;font-size:24px}.tone-1{color:#35a98e;background:#e9f8f4}.tone-2{color:#7259de;background:#f0edff}.assignment-main h3,.score-list h3{margin:0;font-size:14px}.assignment-main p,.score-list p{color:#8795a8;font-size:11px}.assignment-actions{display:grid;justify-items:end;gap:13px}.status{padding:5px 9px;border-radius:99px;font-size:10px}.status--pending{color:#d38831;background:#fff4e6}.status--submitted{color:#347bd3;background:#eaf3ff}.status--reviewed{color:#2e9b73;background:#e8f7f1}.assignment-actions button{padding:7px 13px;border:1px solid #8aabef;border-radius:99px;color:#4b77db;background:#fff}.scores-section{min-height:390px}.score-list{margin-top:24px}.score-list article{grid-template-columns:minmax(0,1fr) auto}.score-values{display:flex;gap:28px}.score-values span{display:grid;gap:5px;color:#8997a8;font-size:10px;text-align:right}.score-values .group-score b{color:#1f2937;font-size:18px}.score-values .final-score b{color:#3e75d4;font-size:22px}.feedback,.empty-state{display:flex;min-height:230px;align-items:center;justify-content:center;flex-direction:column;gap:10px;color:#8493a6}.feedback--error{min-height:auto}.empty-state>.el-icon{font-size:34px}@media(max-width:760px){.course-hero{grid-template-columns:1fr}.course-tabs{gap:0;justify-content:space-between}.assignment-card{grid-template-columns:48px 1fr}.assignment-actions{grid-column:1/-1;display:flex;justify-content:space-between}.score-list article{grid-template-columns:1fr}.score-values{justify-content:space-between}.assignment-section{padding:22px 16px}}
</style>
