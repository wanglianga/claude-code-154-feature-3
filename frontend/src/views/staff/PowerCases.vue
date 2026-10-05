<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <span>用电超载处置 · 临时电箱跳闸</span>
          <el-button type="danger" @click="openReport">登记电箱跳闸</el-button>
        </div>
      </template>

      <el-alert type="warning" :closable="false" style="margin-bottom:12px;">
        多个饮品/加热摊位同时使用大功率设备导致跳闸时：平台依据<b>摊主申报用电、现场设备照片、电工处理记录</b>判责；
        可停用设备 / 调整摊位 / 增加电箱；停电波及其他摊主的补偿进入活动复盘；
        <b>电工复核线路负载前，相关摊位不得继续使用大功率设备</b>。
      </el-alert>

      <el-table :data="cases" border size="small">
        <el-table-column prop="createdAt" label="登记时间" width="170" />
        <el-table-column label="电箱" width="120">
          <template #default="{ row }">
            {{ row.boxCode || '—' }}
            <div v-if="row.boxCapacityWatt" style="font-size:11px;color:#909399;">额定 {{ row.boxCapacityWatt }}W</div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="跳闸经过" min-width="200" show-overflow-tooltip />
        <el-table-column label="责任摊位" width="150">
          <template #default="{ row }">
            <el-tag v-for="s in row.involved" :key="s.applicationId" size="small"
                    :type="s.highPowerLocked ? 'danger' : 'info'" style="margin:2px;">
              {{ s.shopName }}
            </el-tag>
            <span v-if="!row.involved.length">—</span>
          </template>
        </el-table-column>
        <el-table-column label="判责" width="170">
          <template #default="{ row }">
            <el-tag v-if="row.responsibility" size="small" type="warning">{{ row.responsibilityLabel }}</el-tag>
            <span v-else style="color:#909399;">待判责</span>
          </template>
        </el-table-column>
        <el-table-column label="停电补偿" width="100">
          <template #default="{ row }">
            <span v-if="row.compensations.length">¥{{ compTotal(row) }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag size="small" :type="dictType(POWER_CASE_STATUS, row.status)">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openDetail(row)">处置</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 登记跳闸 -->
    <el-dialog v-model="reportDlg" title="登记临时电箱跳闸" width="640px">
      <el-form :model="rf" label-width="120px">
        <el-form-item label="电箱编号"><el-input v-model="rf.boxCode" placeholder="如 PX-食品区-02" /></el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="电箱额定(W)"><el-input-number v-model="rf.boxCapacityWatt" :min="0" :step="500" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="跳闸叠加(W)"><el-input-number v-model="rf.trippedLoadWatt" :min="0" :step="100" style="width:100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="跳闸经过">
          <el-input v-model="rf.description" type="textarea" :rows="2" placeholder="多个饮品摊同时启动加热设备、漏保跳闸等" />
        </el-form-item>
        <el-form-item label="责任嫌疑摊位">
          <el-select v-model="rf.involvedApplicationIds" multiple filterable style="width:100%"
                     placeholder="同时使用加热设备、可能导致超载的摊位">
            <el-option v-for="a in apps" :key="a.id" :label="optLabel(a)" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="停电波及摊位">
          <el-select v-model="rf.affectedApplicationIds" multiple filterable style="width:100%"
                     placeholder="受停电影响、可能需要补偿的其他摊主">
            <el-option v-for="a in apps" :key="a.id" :label="optLabel(a)" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="现场设备照片">
          <el-input v-model="rf.photoText" type="textarea" :rows="2" placeholder="每行一个照片 URL/编号（申报与现场设备比对依据）" />
        </el-form-item>
        <el-form-item label="到场电工"><el-input v-model="rf.electricianName" /></el-form-item>
        <el-form-item label="电工处理记录">
          <el-input v-model="rf.electricianRecord" type="textarea" :rows="2" placeholder="跳闸后电工排查、临时送电等记录" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportDlg = false">取消</el-button>
        <el-button type="danger" @click="submitReport">登记并进入异常档案</el-button>
      </template>
    </el-dialog>

    <!-- 处置抽屉 -->
    <el-drawer v-model="detailDlg" size="56%" :title="`用电处置单 #${cur?.id ?? ''}`">
      <div v-if="cur">
        <el-steps :active="stepActive" align-center finish-status="success" style="margin-bottom:18px;">
          <el-step title="跳闸建档" />
          <el-step title="平台判责" />
          <el-step title="处置措施" />
          <el-step title="电工复核" />
        </el-steps>

        <!-- 基础信息与证据 -->
        <el-card shadow="never" header="① 跳闸信息与判责证据" style="margin-bottom:12px;">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="电箱">{{ cur.boxCode || '—' }}</el-descriptions-item>
            <el-descriptions-item label="额定/跳闸负载">
              {{ cur.boxCapacityWatt ?? '—' }}W / {{ cur.trippedLoadWatt ?? '—' }}W
            </el-descriptions-item>
            <el-descriptions-item label="跳闸经过" :span="2">{{ cur.description }}</el-descriptions-item>
            <el-descriptions-item label="责任摊位" :span="2">
              <el-tag v-for="s in cur.involved" :key="s.applicationId" size="small" style="margin:2px;"
                      :type="s.highPowerLocked ? 'danger' : 'info'">
                {{ s.shopName }}（申报 {{ s.powerWanted }}W）
                <span v-if="s.highPowerLocked">🔒停用中</span>
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="波及摊位" :span="2">
              <el-tag v-for="s in cur.affected" :key="s.applicationId" size="small" type="warning" style="margin:2px;">
                {{ s.shopName }}
              </el-tag>
              <span v-if="!cur.affected.length">—</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-form label-width="110px" style="margin-top:10px;">
            <el-form-item label="设备照片">
              <el-input v-model="ev.photoText" type="textarea" :rows="2" placeholder="每行一个照片 URL/编号" />
            </el-form-item>
            <el-form-item label="责任摊位">
              <el-select v-model="ev.involvedApplicationIds" multiple filterable style="width:100%">
                <el-option v-for="a in apps" :key="a.id" :label="optLabel(a)" :value="a.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="波及摊位">
              <el-select v-model="ev.affectedApplicationIds" multiple filterable style="width:100%">
                <el-option v-for="a in apps" :key="a.id" :label="optLabel(a)" :value="a.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="电工姓名"><el-input v-model="ev.electricianName" /></el-form-item>
            <el-form-item label="电工记录">
              <el-input v-model="ev.electricianRecord" type="textarea" :rows="2" />
            </el-form-item>
            <el-button @click="saveEvidence">保存证据</el-button>
          </el-form>
        </el-card>

        <!-- 判责 -->
        <el-card shadow="never" header="② 平台判责（申报用电 + 设备照片 + 电工记录）" style="margin-bottom:12px;">
          <el-form label-width="110px">
            <el-form-item label="责任判定">
              <el-radio-group v-model="jf.responsibility">
                <el-radio v-for="r in POWER_RESPONSIBILITY" :key="r.value" :value="r.value" style="display:block;margin:6px 0;">
                  {{ r.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="责任认定说明">
              <el-input v-model="jf.responsibilityNote" type="textarea" :rows="2"
                        placeholder="对照申报功率、现场设备照片与电工记录说明判定依据" />
            </el-form-item>
            <el-button type="primary" :disabled="!!cur.responsibility" @click="submitJudge">
              {{ cur.responsibility ? '已判责：' + cur.responsibilityLabel : '提交判责（摊主责任将扣信用）' }}
            </el-button>
          </el-form>
        </el-card>

        <!-- 处置 -->
        <el-card shadow="never" header="③ 处置措施与停电补偿" style="margin-bottom:12px;">
          <el-form label-width="110px">
            <el-form-item label="处置措施">
              <el-checkbox-group v-model="df.measures">
                <el-checkbox value="DEVICE_STOP">停用部分设备</el-checkbox>
                <el-checkbox value="STALL_MOVE">调整摊位</el-checkbox>
                <el-checkbox value="BOX_ADD">增加电箱</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item v-if="df.measures.includes('STALL_MOVE')" label="迁往摊位">
              <el-select v-model="df.moveToStallId" filterable placeholder="选择空闲摊位" style="width:100%">
                <el-option v-for="s in freeStalls" :key="s.id"
                           :label="`${s.zone} ${s.code}（容量 ${s.powerCapacity}W）`" :value="s.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="措施说明">
              <el-input v-model="df.measureNote" type="textarea" :rows="2"
                        placeholder="停用哪些加热设备、迁往何处、新增电箱编号与容量" />
            </el-form-item>

            <el-form-item label="停电补偿">
              <div style="width:100%">
                <el-table :data="df.compensations" border size="small">
                  <el-table-column label="被波及摊主" min-width="160">
                    <template #default="{ row }">
                      <el-select v-model="row.applicationId" filterable size="small" style="width:100%">
                        <el-option v-for="a in apps" :key="a.id" :label="a.shopName" :value="a.id" />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column label="补偿金额(元)" width="140">
                    <template #default="{ row }">
                      <el-input-number v-model="row.amount" :min="0" :precision="2" size="small" />
                    </template>
                  </el-table-column>
                  <el-table-column label="说明" min-width="150">
                    <template #default="{ row }"><el-input v-model="row.reason" size="small" /></template>
                  </el-table-column>
                  <el-table-column width="60">
                    <template #default="{ $index }">
                      <el-button size="small" link type="danger" @click="df.compensations.splice($index, 1)">删</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-button size="small" style="margin-top:6px;" @click="df.compensations.push({ applicationId: null, amount: 0, reason: '停电影响经营补偿' })">
                  + 添加补偿
                </el-button>
              </div>
            </el-form-item>
            <el-button type="warning" :disabled="cur.status !== 'REPORTED' || !cur.responsibility" @click="submitDispose">
              下发处置（即刻锁定责任摊位大功率设备）
            </el-button>
            <el-text v-if="cur.status !== 'REPORTED'" type="success" style="margin-left:10px;">
              已处置：{{ cur.measures.map(powerMeasureLabel).join('、') }}
            </el-text>
          </el-form>
        </el-card>

        <!-- 电工复核 -->
        <el-card shadow="never" header="④ 电工复核线路负载（通过后解锁）">
          <el-form label-width="110px">
            <el-form-item label="复核实测(W)">
              <el-input-number v-model="rv.reviewedLoadWatt" :min="0" :step="100" />
            </el-form-item>
            <el-form-item label="复核结论">
              <el-input v-model="rv.reviewNote" type="textarea" :rows="2" placeholder="线路负载在安全范围、接地漏保正常等" />
            </el-form-item>
            <el-button type="success" :disabled="cur.status !== 'DISPOSED'" @click="submitReview">
              电工复核通过，恢复供电并解除大功率设备锁定
            </el-button>
            <el-text v-if="cur.status === 'REVIEWED'" type="success" style="margin-left:10px;">
              已于 {{ cur.reviewedAt }} 复核通过（实测 {{ cur.reviewedLoadWatt }}W），异常档案自动闭环
            </el-text>
          </el-form>
        </el-card>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../api/client'
import { POWER_CASE_STATUS, POWER_RESPONSIBILITY, powerMeasureLabel, dictType } from '../../dict'

const eventId = Number(localStorage.getItem('cm_event_id'))
const cases = ref([])
const apps = ref([])
const stalls = ref([])

const reportDlg = ref(false)
const detailDlg = ref(false)
const cur = ref(null)

const rf = reactive({ boxCode: '', boxCapacityWatt: 3000, trippedLoadWatt: null, description: '',
  involvedApplicationIds: [], affectedApplicationIds: [], photoText: '', electricianName: '', electricianRecord: '' })
const ev = reactive({ photoText: '', involvedApplicationIds: [], affectedApplicationIds: [], electricianName: '', electricianRecord: '' })
const jf = reactive({ responsibility: 'VENDOR_FAULT', responsibilityNote: '' })
const df = reactive({ measures: [], moveToStallId: null, measureNote: '', compensations: [] })
const rv = reactive({ reviewedLoadWatt: null, reviewNote: '' })

const freeStalls = computed(() => stalls.value.filter((s) => s.powerAvailable && s.status === 'FREE'))

const stepActive = computed(() => {
  if (!cur.value) return 0
  if (cur.value.status === 'REVIEWED') return 4
  if (cur.value.status === 'DISPOSED') return 3
  if (cur.value.responsibility) return 2
  return 1
})

function optLabel(a) {
  return `${a.shopName}（${a.stall ? a.stall.code : '未分配'}｜申报 ${a.powerWanted || 0}W）`
}
function compTotal(row) {
  return row.compensations.reduce((s, c) => s + Number(c.amount || 0), 0).toFixed(2)
}
function toLines(text) {
  return (text || '').split('\n').map((s) => s.trim()).filter(Boolean)
}

function openReport() {
  Object.assign(rf, { boxCode: '', boxCapacityWatt: 3000, trippedLoadWatt: null, description: '',
    involvedApplicationIds: [], affectedApplicationIds: [], photoText: '', electricianName: '', electricianRecord: '' })
  reportDlg.value = true
}
async function submitReport() {
  if (!rf.description) {
    ElMessage.warning('请填写跳闸经过')
    return
  }
  const created = await api.post('/onsite/power-cases', {
    eventId,
    boxCode: rf.boxCode, boxCapacityWatt: rf.boxCapacityWatt, trippedLoadWatt: rf.trippedLoadWatt,
    description: rf.description,
    involvedApplicationIds: rf.involvedApplicationIds, affectedApplicationIds: rf.affectedApplicationIds,
    devicePhotoUrls: toLines(rf.photoText),
    electricianName: rf.electricianName, electricianRecord: rf.electricianRecord
  })
  ElMessage.success('跳闸已登记，同步生成用电超载异常档案')
  reportDlg.value = false
  await load()
  openDetailById(created.id)
}

async function openDetail(row) {
  const d = await api.get(`/onsite/power-cases/${row.id}`)
  fillDetail(d)
  detailDlg.value = true
}
async function openDetailById(id) {
  const d = await api.get(`/onsite/power-cases/${id}`)
  fillDetail(d)
  detailDlg.value = true
}
function fillDetail(d) {
  cur.value = d
  ev.photoText = (d.devicePhotoUrls || []).join('\n')
  ev.involvedApplicationIds = d.involved.map((s) => s.applicationId)
  ev.affectedApplicationIds = d.affected.map((s) => s.applicationId)
  ev.electricianName = d.electricianName || ''
  ev.electricianRecord = d.electricianRecord || ''
  jf.responsibility = d.responsibility || 'VENDOR_FAULT'
  jf.responsibilityNote = d.responsibilityNote || ''
  df.measures = [...d.measures]
  df.moveToStallId = d.moveToStallId
  df.measureNote = d.measureNote || ''
  df.compensations = d.compensations.map((c) => ({ applicationId: c.applicationId, amount: Number(c.amount), reason: c.reason }))
  rv.reviewedLoadWatt = d.reviewedLoadWatt
  rv.reviewNote = d.reviewNote || ''
}

async function saveEvidence() {
  await api.put(`/onsite/power-cases/${cur.value.id}/evidence`, {
    devicePhotoUrls: toLines(ev.photoText),
    involvedApplicationIds: ev.involvedApplicationIds,
    affectedApplicationIds: ev.affectedApplicationIds,
    electricianName: ev.electricianName,
    electricianRecord: ev.electricianRecord
  })
  ElMessage.success('证据已更新')
  await refresh()
}

async function submitJudge() {
  await api.post(`/onsite/power-cases/${cur.value.id}/judge`, jf)
  ElMessage.success('已判责（摊主侧责任已扣减信用）')
  await refresh()
}

async function submitDispose() {
  if (!df.measures.length) {
    ElMessage.warning('请至少选择一项处置措施')
    return
  }
  await ElMessageBox.confirm('下发后责任摊位将被锁定、停用大功率设备，直至电工复核通过。确认下发？', '处置确认', { type: 'warning' })
  await api.post(`/onsite/power-cases/${cur.value.id}/dispose`, {
    measures: df.measures,
    moveToStallId: df.moveToStallId,
    measureNote: df.measureNote,
    compensations: df.compensations.filter((c) => c.applicationId)
  })
  ElMessage.success('处置已下发，相关摊位大功率设备锁定；补偿已计入复盘')
  await refresh()
}

async function submitReview() {
  if (rv.reviewedLoadWatt == null) {
    ElMessage.warning('请填写复核实测负载')
    return
  }
  await api.post(`/onsite/power-cases/${cur.value.id}/electrician-review`, rv)
  ElMessage.success('复核通过，已解除锁定并闭环异常档案')
  await refresh()
}

async function refresh() {
  await load()
  if (cur.value) {
    const d = await api.get(`/onsite/power-cases/${cur.value.id}`)
    fillDetail(d)
  }
}
async function load() {
  cases.value = await api.get(`/onsite/power-cases/events/${eventId}`)
  apps.value = await api.get(`/events/${eventId}/applications`)
  stalls.value = await api.get(`/stalls?eventId=${eventId}`)
}
onMounted(load)
</script>
