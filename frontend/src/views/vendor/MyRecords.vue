<template>
  <div class="page-container">
    <el-row :gutter="14">
      <el-col :span="15">
        <el-card header="我的报名记录">
          <el-table :data="list" border>
            <el-table-column prop="eventId" label="活动ID" width="80" />
            <el-table-column prop="shopName" label="摊位" min-width="130" />
            <el-table-column label="品类" width="90">
              <template #default="{ row }">
                <el-tag size="small">{{ row.categoryLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="审核" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="dictType(APP_STATUS, row.status)">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="摊位/入场" min-width="150">
              <template #default="{ row }">
                <div>{{ row.stall ? row.stall.zone + ' ' + row.stall.code : '待分配' }}</div>
                <el-tag size="small" :type="dictType(CHECKIN_STATUS, row.checkInStatus)">{{ row.checkInStatusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button size="small" type="primary" :disabled="row.status !== 'APPROVED'" @click="showEntry(row)">
                  入场码
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card class="section-card" header="我的收款与捐赠">
          <el-table :data="list" border size="small">
            <el-table-column prop="shopName" label="摊位" min-width="120" />
            <el-table-column label="承诺比例" width="90">
              <template #default="{ row }">{{ row.donationRate }}%</template>
            </el-table-column>
            <el-table-column label="流水合计" width="100">
              <template #default="{ row }">¥{{ fmtMoney(row.salesAmount) }}</template>
            </el-table-column>
            <el-table-column label="应捐" width="90">
              <template #default="{ row }">¥{{ fmtMoney(row.donation?.expectedAmount) }}</template>
            </el-table-column>
            <el-table-column label="实捐" width="90">
              <template #default="{ row }">¥{{ fmtMoney(row.donation?.actualAmount) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.donation" size="small" :type="dictType(DONATION_STATUS, row.donation.status)">
                  {{ dictLabel(DONATION_STATUS, row.donation.status) }}
                </el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="9">
        <el-card v-if="current" header="摊主入场二维码与物资清单">
          <div class="qr-center">
            <QrCode :text="current.checkinCode" :size="200" />
            <div style="font-size:18px; font-weight:700; letter-spacing:2px;">{{ current.checkinCode }}</div>
            <el-tag size="large" :type="dictType(CHECKIN_STATUS, current.checkInStatus)">
              {{ current.checkInStatusLabel }}
            </el-tag>
            <el-text type="info" size="small">活动当天向工作人员出示此码核销入场</el-text>
          </div>
          <el-divider>物资领取清单</el-divider>
          <el-table :data="materials" border size="small">
            <el-table-column prop="name" label="物资" />
            <el-table-column prop="qty" label="数量" width="70" />
            <el-table-column prop="unit" label="说明" width="110" />
          </el-table>
          <el-tag v-if="current.materialsCollected" type="success" style="margin-top:10px; width:100%; text-align:center;">
            物资已于 {{ current.materialsCollectedAt }} 领取
          </el-tag>
          <el-alert v-if="current.allocationReason" type="info" :closable="false" style="margin-top:10px;">
            摊位分配说明：{{ current.allocationReason }}
          </el-alert>
          <el-alert v-if="current.powerLocked" type="error" :closable="false" show-icon style="margin-top:10px;">
            <template #title>大功率设备暂停使用</template>
            用电整改尚未经电工复核线路负载，复核通过前本摊不得继续使用 500W 以上加热/大功率设备。
            <div style="font-size:12px;">{{ current.powerLockReason }}</div>
          </el-alert>
          <div v-if="current.powerCases?.length || current.powerCompensations?.length" style="margin-top:12px;">
            <el-divider content-position="left">用电跳闸处置与停电补偿</el-divider>
            <el-timeline v-if="current.powerCases?.length">
              <el-timeline-item v-for="(p, i) in current.powerCases" :key="i" :timestamp="p.statusLabel"
                                placement="top" :type="p.status === 'REVIEWED' ? 'success' : 'warning'">
                {{ p.title }}（电箱 {{ p.boxCode || '—' }}）
                <el-tag size="small" type="danger" style="margin:0 4px;">{{ p.liabilityLabel }}</el-tag>
                <span v-if="p.party?.liable" style="color:#f56c6c; font-size:12px;">本摊承担责任</span>
                <span v-else-if="p.party?.affectedByOutage" style="color:#e6a23c; font-size:12px;">受停电波及</span>
              </el-timeline-item>
            </el-timeline>
            <el-table v-if="current.powerCompensations?.length" :data="current.powerCompensations" border size="small">
              <el-table-column prop="amount" label="补偿(元)" width="90" />
              <el-table-column prop="outageMinutes" label="停电(分)" width="80" />
              <el-table-column prop="reason" label="说明" min-width="120" show-overflow-tooltip />
              <el-table-column label="状态" width="80">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 'PAID' ? 'success' : 'warning'">
                    {{ row.status === 'PAID' ? '已发放' : '待发放' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
        <el-empty v-else description="选择一条已通过审核的报名查看入场码" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/client'
import { fmtMoney } from '../../composables/useEvents'
import { APP_STATUS, CHECKIN_STATUS, DONATION_STATUS, dictLabel, dictType } from '../../dict'
import QrCode from '../../components/QrCode.vue'

const list = ref([])
const current = ref(null)
const materials = ref([])

async function showEntry(row) {
  const r = await api.get(`/my/applications/${row.id}/entry`)
  current.value = r.application
  materials.value = r.materials
}
onMounted(async () => {
  list.value = await api.get('/my/applications')
  const approved = list.value.find((a) => a.status === 'APPROVED')
  if (approved) {
    showEntry(approved)
  }
})
</script>
