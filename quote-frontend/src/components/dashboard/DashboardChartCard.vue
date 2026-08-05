<template>
  <section class="erp-surface dashboard-surface dashboard-chart-card">
    <div class="dashboard-section-header">
      <div>
        <h2>{{ title }}</h2>
        <p v-if="description" class="dashboard-muted">{{ description }}</p>
      </div>
    </div>

    <div v-if="hasData" class="dashboard-chart-card-body">
      <div ref="chartRef" class="dashboard-chart-canvas" :aria-label="title" role="img" />
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
const chartInstance = ref()

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
      radius: '76%',
      center: ['33%', '50%'],
      startAngle: 90,
      animationType: 'expansion',
      animationDuration: 900,
      animationEasing: 'cubicOut',
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
        borderColor: '#ffffff',
        borderWidth: 2
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
            radius: '68%'
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

.dashboard-chart-canvas {
  width: 100%;
  height: 286px;
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

  .dashboard-chart-canvas {
    height: 250px;
  }
}
</style>
