<template>
  <div v-if="pageCount > 1" class="pager" :class="{ 'pager--split': !!summary }" role="group" aria-label="分页">
    <span v-if="summary" class="pager__summary">{{ summary }}</span>
    <div class="pager__pages">
      <button type="button" class="pager__arrow" :disabled="page <= 1" aria-label="上一页" @click="go(page - 1)">‹</button>
      <span class="pager__text">第</span>
      <input
        v-model="draft"
        class="pager__input"
        type="text"
        inputmode="numeric"
        :title="`输入 1-${pageCount} 之间的页码，回车跳转`"
        :aria-label="`当前第 ${page} 页，共 ${pageCount} 页，输入页码后回车跳转`"
        @focus="$event.target.select()"
        @keydown.enter.prevent="commit"
        @blur="commit"
      />
      <span class="pager__text">/ {{ pageCount }} 页</span>
      <button type="button" class="pager__arrow" :disabled="page >= pageCount" aria-label="下一页" @click="go(page + 1)">›</button>
    </div>
  </div>
</template>
<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  page: { type: Number, required: true },
  pageCount: { type: Number, required: true },
  // 可选的范围说明，如「1–10 / 共 12 组」；传了会靠左，让页码靠右，和表格分页一致。
  summary: { type: String, default: '' }
})
const emit = defineEmits(['update:page'])
// 输入框常显当前页，聚焦即全选，直接键入页码后回车或失焦即可跳转。
const draft = ref(String(props.page))
watch(() => props.page, (value) => { draft.value = String(value) })

const go = (target) => {
  const next = Math.min(Math.max(Math.trunc(Number(target)) || 1, 1), props.pageCount)
  if (next !== props.page) emit('update:page', next)
  draft.value = String(next)
}
const commit = () => {
  const raw = String(draft.value).trim()
  if (!/^\d+$/.test(raw)) { draft.value = String(props.page); return }
  go(raw)
}
</script>
<style scoped>
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 20px 0 0;
  color: #8795a6;
  font-size: 13px;
}
.pager--split { justify-content: space-between; gap: 14px; }
.pager__pages {
  display: flex;
  align-items: center;
  gap: 6px;
}
.pager__summary { white-space: nowrap; }
.pager__arrow {
  display: grid;
  width: 30px;
  height: 30px;
  padding: 0 0 2px;
  place-items: center;
  border: 1px solid #e3eaf3;
  border-radius: 9px;
  color: #52657a;
  background: #fff;
  font-size: 17px;
  line-height: 1;
  cursor: pointer;
  transition: border-color .15s, color .15s, background .15s;
}
.pager__arrow:hover:not(:disabled) { border-color: #b9cfea; color: #356798; background: #f6faff; }
.pager__arrow:disabled { color: #c3ccd8; background: #f8fafc; cursor: not-allowed; }
.pager__input {
  box-sizing: border-box;
  width: 46px;
  height: 30px;
  padding: 0;
  border: 1px solid #e3eaf3;
  border-radius: 9px;
  outline: 0;
  color: #334155;
  background: #fff;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  text-align: center;
  cursor: text;
  transition: border-color .15s, box-shadow .15s;
}
.pager__input:hover { border-color: #c8d8ea; }
.pager__input:focus { border-color: #6b9ed8; box-shadow: 0 0 0 3px rgba(107, 158, 216, .12); }
.pager__text { white-space: nowrap; }
</style>
