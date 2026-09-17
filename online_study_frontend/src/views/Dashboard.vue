<template>
  <div class="dashboard">
    <div class="page-head">
      <h2>{{ data.title || '数据概览' }}</h2>
      <p class="subtitle">{{ data.subtitle }}</p>
    </div>

    <!-- 数字卡片：由服务端按角色返回，前端只负责渲染 -->
    <el-row :gutter="16">
      <el-col :xs="12" :sm="6" v-for="card in data.cards" :key="card.label">
        <div class="stat-card">
          <div class="label">{{ card.label }}</div>
          <div class="value">
            {{ card.value }}<span class="unit">{{ card.unit }}</span>
          </div>
          <div class="tip">{{ card.tip || '&nbsp;' }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表：类型 / 标题 / 数据全部来自服务端 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="12" v-for="(chart, index) in data.charts" :key="index">
        <div class="chart-card">
          <div class="chart-title">{{ chart.title }}</div>
          <div :ref="el => setChartRef(el, index)" class="chart-body"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import request from '../utils/request'

/**
 * 首页数据概览
 *
 * 改造点：
 * 1. 数据全部来自后端 /dashboard/overview —— 服务端按角色返回不同内容，
 *    前端不再拉 4 张全表在浏览器里统计（原来数据量一大就卡，而且密码字段也会跟着接口出去）。
 * 2. 一套渲染逻辑吃三种角色的数据：后端返回 cards + charts，前端只做渲染，不做计算。
 * 3. 修掉原实现的三个问题：写死的「管理员 1 人」、resize 监听未解绑、ECharts 实例未销毁。
 */
const data = reactive({
  role: '',
  title: '',
  subtitle: '',
  cards: [],
  charts: [],
})

const chartRefs = ref([])
// 用普通数组保存 ECharts 实例，卸载时逐个 dispose，避免内存泄漏
let chartInstances = []

const setChartRef = (el, index) => {
  if (el) {
    chartRefs.value[index] = el
  }
}

/** 把后端的图表数据翻译成 ECharts 的 option */
const buildOption = (chart) => {
  const series = chart.series || []
  const categories = chart.categories || []

  // 饼图：categories 作扇区名称，第一个系列的 data 作数值
  if (chart.type === 'pie') {
    const first = series[0] || { data: [] }
    const values = first.data || []
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, icon: 'circle' },
      series: [
        {
          type: 'pie',
          radius: ['42%', '66%'],
          center: ['50%', '45%'],
          avoidLabelOverlap: true,
          data: categories.map((name, i) => ({ name, value: values[i] ?? 0 })),
          label: { formatter: '{b}: {c}' },
        },
      ],
    }
  }

  const isLine = chart.type === 'line'
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 10, right: 20, top: 34, bottom: 10, containLabel: true },
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: {
        interval: 0,
        rotate: categories.length > 5 ? 25 : 0,   // 课程名较长时斜排，避免重叠
      },
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: series.map((item) => ({
      name: item.name,
      type: chart.type,
      data: item.data || [],
      barMaxWidth: 42,
      smooth: isLine,
      label: { show: true, position: 'top' },
      areaStyle: isLine ? { opacity: 0.15 } : undefined,
    })),
  }
}

const renderCharts = () => {
  // 重建前先销毁旧实例（角色切换 / 重复进入页面时避免叠加）
  chartInstances.forEach((instance) => instance && instance.dispose())
  chartInstances = []

  data.charts.forEach((chart, index) => {
    const el = chartRefs.value[index]
    if (!el) return
    const instance = echarts.init(el)
    instance.setOption(buildOption(chart))
    chartInstances.push(instance)
  })
}

const handleResize = () => {
  chartInstances.forEach((instance) => instance && instance.resize())
}

onMounted(async () => {
  try {
    const res = await request.get('/dashboard/overview')
    Object.assign(data, res)
    await nextTick()          // 等 v-for 渲染出图表容器再初始化
    renderCharts()
    window.addEventListener('resize', handleResize)
  } catch (e) {
    console.error(e)
  }
})

onUnmounted(() => {
  // 原实现只加监听不解绑：反复进入首页会累积监听器，且实例泄漏
  window.removeEventListener('resize', handleResize)
  chartInstances.forEach((instance) => instance && instance.dispose())
  chartInstances = []
})
</script>

<style scoped>
.dashboard {
  padding: 4px;
}

.page-head h2 {
  margin: 0 0 4px;
  font-size: 20px;
  color: var(--el-text-color-primary);
}

.subtitle {
  margin: 0 0 16px;
  font-size: 13px;
  color: #909399;
}

.stat-card {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px 18px;
  margin-bottom: 16px;
  transition: box-shadow 0.2s;
}

.stat-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.stat-card .label {
  font-size: 13px;
  color: #909399;
}

.stat-card .value {
  margin: 6px 0 4px;
  font-size: 26px;
  font-weight: 600;
  color: var(--el-color-primary);
  line-height: 1.2;
}

.stat-card .unit {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 400;
  color: #909399;
}

.stat-card .tip {
  min-height: 16px;
  font-size: 12px;
  color: #b1b3b8;
}

.chart-card {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 16px;
}

.chart-title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.chart-body {
  height: 280px;
}
</style>
