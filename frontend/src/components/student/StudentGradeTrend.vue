<template><section class="trend"><h2>成绩趋势</h2><canvas ref="canvas"></canvas></section></template>
<script setup>
import {onMounted,onBeforeUnmount,ref} from 'vue';import {Chart,LineController,LineElement,PointElement,LinearScale,CategoryScale} from 'chart.js';Chart.register(LineController,LineElement,PointElement,LinearScale,CategoryScale);const props=defineProps({scores:{type:Array,default:()=>[]}});const canvas=ref();let chart;onMounted(()=>{const rows=props.scores.filter(item=>item.finalScore!=null);chart=new Chart(canvas.value,{type:'line',data:{labels:rows.map(item=>item.assignmentTitle),datasets:[{label:'成绩',data:rows.map(item=>Number(item.finalScore)),borderColor:'#356798',tension:.3}]},options:{responsive:true,scales:{y:{min:0,max:100}}}})});onBeforeUnmount(()=>chart?.destroy())
</script>
<style scoped>.trend{height:280px;margin-bottom:22px;padding:20px;border-radius:9px;background:#fff}.trend h2{margin:0 0 12px;color:#263b53}</style>
