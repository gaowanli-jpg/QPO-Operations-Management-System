<template>
  <section class="erp-surface dashboard-surface dashboard-chart-card">
    <div class="dashboard-section-header">
      <div>
        <h2>{{ title }}</h2>
        <p v-if="description" class="dashboard-muted">{{ description }}</p>
      </div>
    </div>

    <div v-if="hasData" class="dashboard-chart-card-body">
      <div class="dashboard-chart-shell" :class="{ 'dashboard-chart-shell-revealing': isPieRevealing }">
        <div ref="chartRef" class="dashboard-chart-canvas" :aria-label="title" role="img" />
        <span v-if="isPieRevealing" ref="revealMaskRef" class="dashboard-chart-reveal-mask" aria-hidden="true" />
      </div>
    </div>
    <div v-else class="dashboard-chart-empty">
      {{ emptyText }}
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { formatDashboardNumber } from './dashboardNavigation'

echarts.use([PieChart, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    default: ''
  },
  data: {
    type: Array,
    default: () => []
  },
  emptyText: {
    type: String,
    default: '当前权限范围内暂无可展示数据'
  },
  palette: {
    type: Array,
    default: () => ['#2563EB', '#10B981', '#F59E0B', '#EF4444', '#06B6D4', '#8B5CF6']
  }
})

const emit = defineEmits(['select'])
const chartRef = ref()
const revealMaskRef = ref()
const chartInstance = ref()
const isPieRevealing = ref(false)
const pieRevealFrame = ref()
const pieStartAngle = 90
const cssRevealStartAngle = 90 - pieStartAngle
const pieRevealStartDelay = 80
const pieRevealDuration = 1200

const chartData = computed(() => props.data
  .map((item, index) => ({
    ...item,
    value: Number(item.value || 0),
    itemStyle: {
      color: props.palette[index % props.palette.length]
    }
  }))
  .filter((item) => item.value > 0))

const total = computed(() => chartData.value.reduce((sum, item) => sum + item.value, 0))
const hasData = computed(() => chartData.value.length > 0 && total.value > 0)

const prefersReducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches

const clearPieRevealFrame = () => {
  if (pieRevealFrame.value === undefined) return
  window.cancelAnimationFrame(pieRevealFrame.value)
  pieRevealFrame.value = undefined
}

const updatePieRevealMask = (angle) => {
  const revealMask = revealMaskRef.value
  if (!revealMask) return
  revealMask.style.setProperty('--dashboard-reveal-start-angle', `${cssRevealStartAngle}deg`)
  revealMask.style.setProperty('--dashboard-sweep-angle', `${angle}deg`)
}

const easeOutCubic = (progress) => 1 - Math.pow(1 - progress, 3)

const animatePieReveal = (startTime) => {
  const runFrame = (timestamp) => {
    const elapsed = Math.max(0, timestamp - startTime)
    const progress = Math.min(elapsed / pieRevealDuration, 1)
    updatePieRevealMask(1 + 359 * easeOutCubic(progress))
    if (progress < 1) {
      pieRevealFrame.value = window.requestAnimationFrame(runFrame)
      return
    }
    pieRevealFrame.value = undefined
    isPieRevealing.value = false
  }
  pieRevealFrame.value = window.requestAnimationFrame(runFrame)
}

const revealPieChart = async () => {
  clearPieRevealFrame()
  isPieRevealing.value = false
  if (prefersReducedMotion()) return
  await nextTick()
  if (!hasData.value || !chartInstance.value) return
  isPieRevealing.value = true
  await nextTick()
  updatePieRevealMask(1)
  animatePieReveal(window.performance.now() + pieRevealStartDelay)
}

const buildChartOption = () => ({
  color: props.palette,
  tooltip: {
    trigger: 'item',
    formatter: (params) => {
      const percent = Number.isFinite(params.percent) ? `${params.percent}%` : '0%'
      return `${params.name}<br/>数量：${formatDashboardNumber(params.value)}<br/>占比：${percent}`
    }
  },
  legend: {
    type: 'scroll',
    orient: 'vertical',
    right: 6,
    top: 'middle',
    itemWidth: 10,
    itemHeight: 10,
    textStyle: {
      color: '#667789',
      fontSize: 12
    }
  },
  series: [
    {
      name: props.title,
      type: 'pie',
      radius: ['42%', '76%'],
      center: ['33%', '50%'],
      startAngle: pieStartAngle,
      animation: false,
      avoidLabelOverlap: true,
      label: {
        show: false
      },
      labelLine: {
        show: false
      },
      emphasis: {
        scale: true,
        scaleSize: 5,
        itemStyle: {
          shadowBlur: 14,
          shadowColor: 'rgba(15, 23, 42, 0.18)'
        }
      },
      itemStyle: {
        borderWidth: 0
      },
      data: chartData.value
    }
  ],
  media: [
    {
      query: {
        maxWidth: 520
      },
      option: {
        legend: {
          orient: 'horizontal',
          left: 'center',
          right: 'auto',
          bottom: 0,
          top: 'auto'
        },
        series: [
          {
            center: ['50%', '40%'],
            radius: ['38%', '68%']
          }
        ]
      }
    }
  ]
})

const handleSelect = (params) => {
  const selected = props.data.find((item) => item.name === params.name) || params.data
  emit('select', selected)
}

const ensureChart = () => {
  if (!chartRef.value || chartInstance.value) return
  chartInstance.value = echarts.init(chartRef.value)
  chartInstance.value.on('click', handleSelect)
}

const disposeChart = () => {
  clearPieRevealFrame()
  isPieRevealing.value = false
  chartInstance.value?.dispose()
  chartInstance.value = undefined
}

const resizeChart = () => {
  chartInstance.value?.resize()
}

const renderChart = async () => {
  await nextTick()
  if (!hasData.value) {
    disposeChart()
    return
  }
  ensureChart()
  chartInstance.value?.setOption(buildChartOption(), true)
  chartInstance.value?.resize()
  revealPieChart()
}

onMounted(() => {
  renderChart()
  window.addEventListener('resize', resizeChart)
})

watch(() => [props.data, props.palette, props.title], renderChart, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  disposeChart()
})
</script>

<style scoped>
.dashboard-chart-card {
  min-width: 0;
  overflow: hidden;
}

.dashboard-section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--erp-space-3);
  padding: var(--erp-space-4);
  border-bottom: 1px solid var(--erp-border);
  background: var(--erp-surface-soft);
}

.dashboard-section-header h2 {
  margin: 0 0 var(--erp-space-1);
  color: var(--erp-text);
  font-size: var(--erp-font-size-lg);
}

.dashboard-section-header p {
  margin: 0;
  font-size: var(--erp-font-size-sm);
  line-height: var(--erp-line-height-base);
}

.dashboard-chart-card-body {
  min-height: 318px;
  padding: var(--erp-space-4);
}

.dashboard-chart-shell {
  position: relative;
  width: 100%;
  height: 286px;
  overflow: hidden;
}

.dashboard-chart-canvas {
  width: 100%;
  height: 100%;
}

.dashboard-chart-reveal-mask {
  --dashboard-reveal-start-angle: 0deg;
  --dashboard-sweep-angle: 1deg;
  position: absolute;
  top: 50%;
  left: 33%;
  z-index: 1;
  width: 220px;
  height: 220px;
  border-radius: 50%;
  background: conic-gradient(from var(--dashboard-reveal-start-angle), transparent 0deg var(--dashboard-sweep-angle), var(--erp-surface) var(--dashboard-sweep-angle) 360deg);
  pointer-events: none;
  transform: translate(-50%, -50%);
}

.dashboard-chart-empty {
  display: grid;
  min-height: 240px;
  place-items: center;
  margin: var(--erp-space-4);
  padding: var(--erp-space-6);
  color: var(--erp-text-secondary);
  background: var(--erp-surface-muted);
  border: 1px dashed var(--erp-border-strong);
  border-radius: var(--erp-radius-lg);
  text-align: center;
}

.dashboard-muted {
  color: var(--erp-text-secondary);
}

.dashboard-surface {
  border-color: var(--erp-border);
  background: var(--erp-surface);
}

@media (max-width: 760px) {
  .dashboard-section-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .dashboard-chart-card-body {
    min-height: 280px;
    padding: var(--erp-space-3);
  }

  .dashboard-chart-shell {
    height: 250px;
  }

  .dashboard-chart-reveal-mask {
    top: 40%;
    left: 50%;
    width: 170px;
    height: 170px;
  }
}

</style>
