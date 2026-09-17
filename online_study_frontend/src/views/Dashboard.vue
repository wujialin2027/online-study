<template>
  <div class="dashboard">
    <div class="page-head">
      <h2>{{ data.title || '数据概览' }}</h2>
      <p class="subtitle">{{ data.subtitle }}</p>
    </div>

    <!-- 数字卡片：由服务端按角色返回，前端只负责渲染 -->
    <el-row :gutter="16">
      <el-col :xs="12" :sm="6" v-for="card in data.cards" :key="card.label">
        <!--
          卡片可以带跳转：跳转地址由服务端给出（card.link），前端只负责 push。
          后端既然知道这个数字指的是什么，也就知道该跳到哪 —— 前端不用硬编码一套对应关系。
        -->
        <div
          class="stat-card"
          :class="{ 'is-link': card.link }"
          @click="goCard(card)"
        >
          <div class="label">
            {{ card.label }}
            <span v-if="card.link" class="arrow">›</span>
          </div>
          <div class="value">
            {{ card.value }}<span class="unit">{{ card.unit }}</span>
          </div>
          <div class="tip">{{ card.tip || '&nbsp;' }}</div>
        </div>
      </el-col>
    </el-row>

    <!--
      报名进度列表。
      为什么不画成柱状图：柱状图回答的是「谁比谁多」，而且它的刻度是相对的
      （最大值占满全格），于是「报名 3 人」看着并不小，读者无法判断这门课是热是冷。
      进度条的刻度是绝对的（满格 = 名额上限），一眼就能看出谁快满、谁冷清。
    -->
    <el-row :gutter="16" v-if="data.progressList && data.progressList.length">
      <el-col :span="24">
        <div class="chart-card">
          <div class="chart-title">
            {{ data.progressTitle }}
            <span class="chart-hint">越靠前越接近满员</span>
          </div>
          <div class="progress-list">
            <div class="progress-row" v-for="item in data.progressList" :key="item.label">
              <div class="progress-name" :title="item.label">{{ item.label }}</div>
              <el-progress
                class="progress-bar"
                :percentage="item.percent"
                :color="progressColor(item.status)"
                :show-text="false"
                :stroke-width="10"
              />
              <div class="progress-tip" :class="'is-' + item.status">{{ item.tip }}</div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表：类型 / 标题 / 数据全部来自服务端 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="12" v-for="(chart, index) in data.charts" :key="index">
        <div class="chart-card">
          <div class="chart-title">{{ chart.title }}</div>
          <!-- 没有数据的图表显示占位，避免出现一个空坐标系 -->
          <div v-if="!emptyCharts[index]" :ref="el => setChartRef(el, index)" class="chart-body"></div>
          <div v-else class="chart-body chart-empty">暂无数据</div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import request from '../utils/request'

/**
 * 首页数据概览
 *
 * 改造点：
 * 1. 数据全部来自后端 /dashboard/overview —— 服务端按角色返回不同内容，
 *    前端不再拉 4 张全表在浏览器里统计（原来数据量一大就卡，而且密码字段也会跟着接口出去）。
 * 2. 一套渲染逻辑吃三种角色的数据：后端返回 cards + charts + progressList，
 *    前端只做渲染，不做计算。
 * 3. 修掉原实现的三个问题：写死的「管理员 1 人」、resize 监听未解绑、ECharts 实例未销毁。
 */
const data = reactive({
  role: '',
  title: '',
  subtitle: '',
  cards: [],
  charts: [],
  progressTitle: '',
  progressList: [],
})

const chartRefs = ref([])
// 记录哪些图表没有数据（用于显示占位文案，而不是画一个空坐标系）
const emptyCharts = ref({})
// 用普通数组保存 ECharts 实例，卸载时逐个 dispose，避免内存泄漏
let chartInstances = []

const setChartRef = (el, index) => {
  if (el) {
    chartRefs.value[index] = el
  }
}

const router = useRouter()

/** 点数字卡片跳到对应的列表页（跳转地址由服务端给出，空表示不可点击） */
const goCard = (card) => {
  if (card.link) {
    router.push(card.link)
  }
}

/**
 * 类目超过这个数量时，纵向柱状图改用横向。
 *
 * 原因：课程名、作业名都是 8~10 个汉字，纵向柱状图的 X 轴标签只能斜排，
 * 类目一多就互相重叠、完全看不清（之前 21 门课挤成一团就是这个原因）。
 * 横过来之后标签在 Y 轴竖向排列，天然有充足空间，也不用旋转。
 */
const HORIZONTAL_BAR_THRESHOLD = 6

/**
 * 类目名的平均字数超过这个值，也改用横向柱状图。
 *
 * 只看数量不够：5 门课的课程名可能都是 10 个汉字
 * （如「MySQL 索引原理与 SQL 优化」），纵向柱状图的 X 轴标签只能斜排，
 * 既互相重叠、又容易被卡片边缘裁掉 —— 曾出现「ECharts 数据可视化实战」
 * 被裁成「rts 数据可视化实战」。横过来之后名字沿 Y 轴竖排，天然放得下。
 */
const LONG_NAME_THRESHOLD = 8

/** 主色与"非重点"色：只高亮第一名，让视线有落点 */
const BAR_PRIMARY = '#409eff'
const BAR_SECONDARY = '#a0cfff'

/** 进度状态 → 颜色。状态由服务端判定，前端只做颜色映射 */
const progressColor = (status) => {
  const map = {
    normal: BAR_PRIMARY,
    warning: '#e6a23c',
    full: '#f56c6c',
    // 不限名额用灰色：条是满的，但灰色表示"开放"而不是"已满"，避免误读
    unlimited: '#c0c4cc',
  }
  return map[status] || BAR_PRIMARY
}

/** 把后端的图表数据翻译成 ECharts 的 option */
const buildOption = (chart) => {
  const series = chart.series || []
  const categories = chart.categories || []

  // ---------- 饼图：categories 作扇区名称，第一个系列的 data 作数值 ----------
  if (chart.type === 'pie') {
    const first = series[0] || { data: [] }
    const values = first.data || []
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8 },
      series: [
        {
          type: 'pie',
          radius: ['42%', '66%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          itemStyle: { borderColor: '#fff', borderWidth: 2 },
          data: categories.map((name, i) => ({ name, value: values[i] ?? 0 })),
          label: { formatter: '{b}: {c}' },
        },
      ],
    }
  }

  const isLine = chart.type === 'line'

  // ---------- 柱状图统一按数值降序重排 ----------
  // 柱状图回答的就是"谁比谁多"，顺序本就该由大到小，而不是让读者自己去图里找最大值。
  // 后端通常已经排好序，这里再保证一次：以后新增图表时忘了排序也不会退化成乱序。
  // ⚠️ 折线图的横轴是时间，绝不能排序。
  const pairs = categories.map((name, i) => ({ name, value: Number((series[0]?.data || [])[i]) || 0 }))
  if (!isLine) {
    pairs.sort((a, b) => b.value - a.value)
  }
  const orderedNames = pairs.map((p) => p.name)
  const orderedValues = pairs.map((p) => p.value)

  // ---------- 参考线（及格线 / 平均线）----------
  // 只有柱子时读者无法判断"这个值算高还是算低"，一条参考线就补上了参照系。
  const markLine = chart.markLineValue == null
    ? undefined
    : {
        silent: true,
        symbol: 'none',
        lineStyle: { type: 'dashed', color: '#e6a23c', width: 1 },
        label: {
          formatter: chart.markLineLabel || '',
          color: '#e6a23c',
          fontSize: 12,
          // 必须用 insideEndTop（绘图区内）：默认的 end 会把标签画在线的末端之外，
          // 右侧空间不够时整段文字被卡片裁掉 —— 曾只显示出一个「及」字
          position: 'insideEndTop',
        },
      }

  // 类目名的平均字数（长中文名在纵向柱状图里放不下）
  const avgNameLength = orderedNames.length
    ? orderedNames.reduce((sum, name) => sum + String(name).length, 0) / orderedNames.length
    : 0

  // ---------- 横向柱状图（类目多，或类目名长）----------
  if (!isLine && (orderedNames.length > HORIZONTAL_BAR_THRESHOLD
      || (orderedNames.length >= 4 && avgNameLength > LONG_NAME_THRESHOLD))) {
    // ECharts 的类目轴自下而上绘制，所以反转数组，让"数值最大的"落在最上面
    const reversedNames = [...orderedNames].reverse()
    const reversedValues = [...orderedValues].reverse()
    const topIndex = reversedValues.length - 1

    return {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      // top 留 24：参考线标签显示在线的顶端内侧，需要一点空间
      grid: { left: 8, right: 46, top: 24, bottom: 6, containLabel: true },
      xAxis: {
        type: 'value',
        minInterval: 1,
        splitLine: { lineStyle: { type: 'dashed', color: '#ebeef5' } },
        axisLabel: { color: '#909399' },
      },
      yAxis: {
        type: 'category',
        data: reversedNames,
        axisLabel: { fontSize: 12, color: '#606266' },
        axisTick: { show: false },
        axisLine: { lineStyle: { color: '#e4e7ed' } },
      },
      series: series.map((item, sIndex) => ({
        name: item.name,
        type: 'bar',
        // 逐项着色：第一名主色、其余浅色（数据项的 itemStyle 会与 series 的合并，
        // 所以下面的圆角设置依然生效）
        data: (sIndex === 0
          ? reversedValues
          : [...(item.data || [])].reverse().map((v) => Number(v) || 0)
        ).map((value, i) => ({
          value,
          itemStyle: { color: i === topIndex ? BAR_PRIMARY : BAR_SECONDARY },
        })),
        barMaxWidth: 16,
        itemStyle: { borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right', color: '#606266', fontSize: 12 },
        markLine: markLine ? { ...markLine, data: [{ xAxis: chart.markLineValue }] } : undefined,
      })),
    }
  }

  // ---------- 纵向柱状图 / 折线图 ----------
  const axisLabels = isLine ? categories : orderedNames
  return {
    tooltip: { trigger: 'axis' },
    // left 留 16：斜排的类目名会向左伸出，空间不足时会被裁掉首字
    grid: { left: 16, right: 20, top: 30, bottom: 6, containLabel: true },
    xAxis: {
      type: 'category',
      data: axisLabels,
      axisLabel: {
        interval: 0,
        rotate: axisLabels.length > 4 ? 20 : 0,
        color: '#606266',
      },
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e4e7ed' } },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { type: 'dashed', color: '#ebeef5' } },
      axisLabel: { color: '#909399' },
    },
    series: series.map((item) => ({
      name: item.name,
      type: chart.type,
      data: isLine
        ? (item.data || [])
        : orderedValues.map((value, i) => ({
            value,
            itemStyle: { color: i === 0 ? BAR_PRIMARY : BAR_SECONDARY },
          })),
      barMaxWidth: 42,
      smooth: isLine,
      label: { show: true, position: 'top', color: '#606266', fontSize: 12 },
      itemStyle: isLine ? undefined : { borderRadius: [4, 4, 0, 0] },
      areaStyle: isLine ? { opacity: 0.12 } : undefined,
      // 折线图不画参考线（趋势图加一条水平线没有意义）
      markLine: (!isLine && markLine) ? { ...markLine, data: [{ yAxis: chart.markLineValue }] } : undefined,
    })),
  }
}

const renderCharts = async () => {
  // 重建前先销毁旧实例（角色切换 / 重复进入页面时避免叠加）
  chartInstances.forEach((instance) => instance && instance.dispose())
  chartInstances = []

  // 先判断哪些图表真的没有数据，避免画出空坐标系
  const empty = {}
  data.charts.forEach((chart, index) => {
    const total = (chart.series || []).reduce(
      (sum, item) => sum + (item.data || []).reduce((acc, value) => acc + (Number(value) || 0), 0),
      0
    )
    if (!chart.categories?.length || total <= 0) {
      empty[index] = true
    }
  })
  emptyCharts.value = empty

  // 等 v-if 渲染完，非空图表的容器才存在
  await nextTick()

  data.charts.forEach((chart, index) => {
    if (empty[index]) return
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
    await renderCharts()
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
  transition: box-shadow 0.2s, transform 0.2s, border-color 0.2s;
}

.stat-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

/* 带跳转的卡片：手型光标 + 更明显的悬浮反馈，暗示"这里可以点" */
.stat-card.is-link {
  cursor: pointer;
}

.stat-card.is-link:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.15);
  transform: translateY(-2px);
}

.stat-card .arrow {
  margin-left: 4px;
  font-size: 14px;
  color: #c0c4cc;
}

.stat-card.is-link:hover .arrow {
  color: var(--el-color-primary);
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

.chart-hint {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: #b1b3b8;
}

.chart-body {
  height: 280px;
}

/* 无数据占位：与图表同高，避免卡片忽高忽低 */
.chart-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #b1b3b8;
  background: #fafafa;
  border-radius: var(--app-radius-sm);
}

/* ==================== 报名进度列表 ==================== */
.progress-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  /* 教师可能有几十门课，限高滚动，避免把整页拉得很长 */
  max-height: 280px;
  overflow-y: auto;
  padding-right: 6px;
}

.progress-row {
  display: grid;
  grid-template-columns: 200px 1fr 200px;
  align-items: center;
  gap: 14px;
}

.progress-name {
  font-size: 13px;
  color: var(--el-text-color-regular);
  /* 课程名可能很长（如「Java 基础语法与面向对象编程」），截断并保留悬停查看 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-tip {
  font-size: 12px;
  color: #909399;
  text-align: right;
}

.progress-tip.is-warning {
  color: #e6a23c;
}

.progress-tip.is-full {
  color: #f56c6c;
}

.progress-tip.is-unlimited {
  color: #b1b3b8;
}

@media (max-width: 768px) {
  .progress-row {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .progress-tip {
    text-align: left;
  }
}
</style>
