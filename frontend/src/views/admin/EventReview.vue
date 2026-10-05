<template>
  <div class="page-container">
    <el-card class="section-card">
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <span>活动复盘 · {{ data.event?.name }}</span>
          <el-button @click="$router.push(`/events/${eventId}/publicity`)">去发布居民公示 →</el-button>
        </div>
      </template>

      <el-row :gutter="14">
        <el-col :span="6"><div class="stat-card" style="background:#f56c6c;"><div class="num">¥{{ fmtMoney(s.totalDonation) }}</div><div class="label">公益捐赠总额（目标 ¥{{ fmtMoney(s.goalAmount) }}）</div></div></el-col>
        <el-col :span="6"><div class="stat-card" style="background:#e6a23c;"><div class="num">{{ s.visitorCount ?? '-' }}</div><div class="label">现场客流</div></div></el-col>
        <el-col :span="6"><div class="stat-card" style="background:#409eff;"><div class="num">{{ s.attendanceRate ?? '-' }}%</div><div class="label">摊主到场率（提前离场 {{ s.earlyLeft }}）</div></div></el-col>
        <el-col :span="6"><div class="stat-card" style="background:#909399;"><div class="num">{{ s.complaintCount }}/{{ s.incidentCount }}</div><div class="label">投诉 / 异常（未闭环 {{ s.incidentOpen }}）</div></div></el-col>
      </el-row>

      <el-row :gutter="14" style="margin-top:16px;">
        <el-col :span="12">
          <el-card shadow="never" header="通过摊位的品类结构">
            <div ref="pieEl" style="height:300px;"></div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" header="公益目标完成情况">
            <el-progress :percentage="goalPercent" :status="s.goalReached ? 'success' : ''"
                         :stroke-width="22" text-inside striped striped-flow
                         :format="() => `¥${fmtMoney(s.totalDonation)} / ¥${fmtMoney(s.goalAmount)}`" />
            <el-descriptions :column="1" border style="margin-top:16px;">
              <el-descriptions-item label="营业总额">¥{{ fmtMoney(s.totalSales) }}</el-descriptions-item>
              <el-descriptions-item label="承诺应捐合计">¥{{ fmtMoney(s.expectedDonation) }}</el-descriptions-item>
              <el-descriptions-item label="实际捐赠合计">¥{{ fmtMoney(s.totalDonation) }}</el-descriptions-item>
              <el-descriptions-item label="亲子摊位">{{ s.familyStalls }} 个</el-descriptions-item>
              <el-descriptions-item label="资质不完整食品摊">{{ s.foodIncomplete }} 个</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card v-if="data.powerCases && data.powerCases.length" class="section-card" header="用电超载处置与停电补偿（进入复盘）">
      <el-row :gutter="14" style="margin-bottom:10px;">
        <el-col :span="8">
          <el-statistic title="跳闸处置单数" :value="data.powerCases.length" />
        </el-col>
        <el-col :span="8">
          <el-statistic title="停电补偿合计(元)" :value="Number(data.powerCompensationTotal || 0)" :precision="2" />
        </el-col>
        <el-col :span="8">
          <el-statistic title="待电工复核(摊)" :value="data.powerCaseUnreviewed || 0" />
        </el-col>
      </el-row>
      <el-table :data="data.powerCases" border size="small">
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="boxCode" label="电箱" width="110" />
        <el-table-column prop="description" label="跳闸经过" min-width="180" show-overflow-tooltip />
        <el-table-column label="责任判定" width="180">
          <template #default="{ row }">
            <el-tag size="small" type="warning">{{ row.responsibilityLabel || '待判责' }}</el-tag>
            <div style="font-size:11px;color:#606266;">{{ row.responsibilityNote }}</div>
          </template>
        </el-table-column>
        <el-table-column label="处置措施" width="180">
          <template #default="{ row }">
            <el-tag v-for="m in row.measures" :key="m" size="small" style="margin:2px;">{{ measureLabel(m) }}</el-tag>
            <span v-if="!row.measures.length">—</span>
          </template>
        </el-table-column>
        <el-table-column label="补偿" width="90">
          <template #default="{ row }">
            <span v-if="row.compensations.length">¥{{ row.compensations.reduce((s, c) => s + Number(c.amount || 0), 0).toFixed(2) }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'REVIEWED' ? 'success' : row.status === 'DISPOSED' ? 'warning' : 'danger'">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-row :gutter="14">
      <el-col :span="12">
        <el-card class="section-card" header="摊主履约与捐赠榜（信用影响下次摊位位置）">          <el-table :data="data.vendorRanking" border size="small">
            <el-table-column type="index" label="名次" width="60" />
            <el-table-column prop="shopName" label="摊主" min-width="130" />
            <el-table-column label="信用" width="70">
              <template #default="{ row }">
                <el-tag size="small" :type="row.creditScore >= 100 ? 'success' : row.creditScore >= 80 ? 'warning' : 'danger'">
                  {{ row.creditScore }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="入场" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="dictType(CHECKIN_STATUS, row.checkInStatus)">
                  {{ dictLabel(CHECKIN_STATUS, row.checkInStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="donation" label="捐赠(元)" width="100" />
            <el-table-column label="投诉/异常" width="90">
              <template #default="{ row }">{{ row.complaintCount }}/{{ row.incidentCount }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="section-card" header="下次招商与现场管理建议">
          <el-timeline>
            <el-timeline-item v-for="(tip, i) in data.suggestions" :key="i" :timestamp="`建议 ${i + 1}`" placement="top"
                              :type="i === 0 ? 'primary' : ''">
              {{ tip }}
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { api } from '../../api/client'
import { fmtMoney } from '../../composables/useEvents'
import { CHECKIN_STATUS, dictLabel, dictType, powerMeasureLabel } from '../../dict'

const route = useRoute()
const eventId = Number(route.params.id)
const data = ref({})
const pieEl = ref(null)

const s = computed(() => data.value.stats || {})

function measureLabel(v) {
  return powerMeasureLabel(v)
}
const goalPercent = computed(() => {
  const goal = Number(s.value.goalAmount || 0)
  const got = Number(s.value.totalDonation || 0)
  if (!goal) {
    return 0
  }
  return Math.min(100, Math.round((got / goal) * 100))
})

const CATEGORY_LABEL = { HANDMADE: '手作', SECONDHAND: '二手物品', FOOD: '食品', CHARITY: '公益义卖', COMMERCIAL: '商业' }

onMounted(async () => {
  data.value = await api.get(`/events/${eventId}/review`)
  await nextTick()
  const chart = echarts.init(pieEl.value)
  const entries = Object.entries(s.value.byCategory || {})
  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '68%'],
      data: entries.map(([k, v]) => ({ name: CATEGORY_LABEL[k] || k, value: v })),
      label: { formatter: '{b}: {c}个' },
      color: ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399']
    }]
  })
  window.addEventListener('resize', () => chart.resize())
})
</script>
