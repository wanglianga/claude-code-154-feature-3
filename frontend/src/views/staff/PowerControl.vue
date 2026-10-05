<template>
  <div class="page-container">
    <el-alert
        title="用电超载/跳闸处置：依据「摊主申报用电 + 现场设备照片 + 电工处理记录」判定责任；可停用设备、调整摊位、增加电箱；停电补偿进入复盘；电工复核线路负载通过前，责任摊位大功率设备一律封锁。"
        type="warning" :closable="false" show-icon style="margin-bottom:12px;" />

    <el-row :gutter="14">
      <!-- 左：工单列表 -->
      <el-col :span="8">
        <el-card>
          <template #header>
            <div style="display:flex; justify-content:space-between; align-items:center;">
              <span>跳闸处置工单（{{ cases.length }}）</span>
              <el-button type="danger" size="small" @click="openCreate">登记跳闸</el-button>
            </div>
          </template>
          <el-table :data="cases" border size="small" highlight-current-row
                    @row-click="openDetail" :row-class-name="rowClass" style="cursor:pointer;">
            <el-table-column prop="id" label="#" width="42" />
            <el-table-column prop="title" label="工单" min-width="150" show-overflow-tooltip />
            <el-table-column label="状态" width="108">
              <template #default="{ row }">
                <el-tag size="small" :type="dictType(POWER_CASE_STATUS, row.status)">
                  {{ dictLabel(POWER_CASE_STATUS, row.status) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右：工单详情 -->
      <el-col :span="16">
        <el-card v-if="detail">
          <template #header>
            <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:8px;">
              <span>工单 #{{ c.id }} · {{ c.title }}
                <el-tag size="small" :type="dictType(POWER_CASE_STATUS, c.status)" style="margin-left:6px;">
                  {{ dictLabel(POWER_CASE_STATUS, c.status) }}
                </el-tag>
              </span>
              <span style="font-size:12px; color:#909399;">登记 {{ c.raisedAt }}｜电工：{{ c.electricianName || '未到场' }}</span>
            </div>
          </template>

          <!-- 基本信息 -->
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="临时电箱">{{ c.boxCode || '—' }}</el-descriptions-item>
            <el-descriptions-item label="电箱容量">{{ c.boxCapacityWatt ?? '—' }} W</el-descriptions-item>
            <el-descriptions-item label="申报/实测合计">
              {{ c.totalDeclaredWatt ?? '—' }} / <b :class="overTotal ? 'danger' : ''">{{ c.totalActualWatt ?? '—' }}</b> W
            </el-descriptions-item>
            <el-descriptions-item label="责任判定" :span="3">
              <el-tag v-if="c.liability" size="small" type="danger">{{ liabilityLabel(c.liability) }}</el-tag>
              <span v-else style="color:#909399;">待判定</span>
              <span v-if="c.liabilityNote" style="margin-left:8px;">{{ c.liabilityNote }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="经过" :span="3">{{ c.description || '—' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 设备照片 -->
          <div class="sec-title">现场设备照片（责任判定依据）</div>
          <div style="display:flex; gap:8px; flex-wrap:wrap; align-items:center;">
            <el-image v-for="(u, i) in photoList" :key="i" :src="u"
                      :preview-src-list="photoList" preview-teleported fit="cover"
                      style="width:86px; height:86px; border-radius:6px; border:1px solid #ebeef5;" />
            <el-upload :show-file-list="false" :http-request="uploadPhoto" accept="image/*">
              <el-button size="small">补传照片</el-button>
            </el-upload>
            <span v-if="!photoList.length" style="color:#909399; font-size:12px;">暂无照片</span>
          </div>

          <!-- 涉及摊位 -->
          <div class="sec-title">涉及摊位（申报功率 vs 现场实测，勾选责任/受影响后保存）</div>
          <el-table :data="detail.parties" border size="small">
            <el-table-column prop="shopName" label="摊位" min-width="130" show-overflow-tooltip />
            <el-table-column label="申报W" width="100">
              <template #default="{ row }">
                <el-input-number v-model="row.declaredWatt" :min="0" :step="100" size="small" controls-position="right" style="width:90px;" />
              </template>
            </el-table-column>
            <el-table-column label="实测W" width="100">
              <template #default="{ row }">
                <el-input-number v-model="row.actualWatt" :min="0" :step="100" size="small" controls-position="right" style="width:90px;" />
              </template>
            </el-table-column>
            <el-table-column label="责任方" width="68">
              <template #default="{ row }"><el-checkbox v-model="row.liable" /></template>
            </el-table-column>
            <el-table-column label="受停电波及" width="92">
              <template #default="{ row }"><el-checkbox v-model="row.affectedByOutage" /></template>
            </el-table-column>
            <el-table-column label="操作" width="72">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="saveParty(row)">保存</el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 操作按钮组 -->
          <div class="sec-title">处置流程</div>
          <el-space wrap>
            <el-button type="danger" plain size="small" @click="openLiability">① 判定责任</el-button>
            <el-button type="warning" plain size="small" @click="openElectrician">② 电工处理记录</el-button>
            <el-button type="primary" plain size="small" @click="openAction">③ 停用/调摊/增箱</el-button>
            <el-button size="small" @click="openCompensation">④ 受影响摊主补偿</el-button>
            <el-button type="warning" size="small" :disabled="c.status === 'PENDING_REVIEW' || c.status === 'REVIEWED'"
                       @click="submitRect">⑤ 整改完成提交复核</el-button>
            <el-button type="success" size="small" :disabled="c.status !== 'PENDING_REVIEW'" @click="openReview">
              ⑥ 电工复核线路负载
            </el-button>
          </el-space>

          <!-- 措施 -->
          <div class="sec-title">处置措施（{{ detail.actions.length }}）</div>
          <el-timeline v-if="detail.actions.length">
            <el-timeline-item v-for="a in detail.actions" :key="a.id" :timestamp="a.createdAt"
                              :type="actionType(a.type)">
              <el-tag size="small" :type="actionType(a.type)">{{ actionLabel(a.type) }}</el-tag>
              <b style="margin:0 6px;">{{ a.shopName || '全场供电' }}</b>{{ a.detail }}
              <span v-if="a.addedCapacityWatt" style="color:#67c23a;">（新增容量 {{ a.addedCapacityWatt }}W）</span>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="尚未下达措施" :image-size="50" />

          <!-- 补偿 -->
          <div class="sec-title">停电波及摊主补偿（合计 ¥{{ detail.compensationTotal }}）</div>
          <el-table v-if="detail.compensations.length" :data="detail.compensations" border size="small">
            <el-table-column prop="shopName" label="受影响摊位" min-width="130" />
            <el-table-column prop="amount" label="金额(元)" width="90" />
            <el-table-column prop="outageMinutes" label="停电(分钟)" width="90" />
            <el-table-column prop="reason" label="补偿说明" min-width="140" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'PAID' ? 'success' : 'warning'">
                  {{ row.status === 'PAID' ? '已发放' : '待发放' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'PAID'" size="small" link type="primary" @click="payComp(row)">发放</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="无补偿记录" :image-size="50" />

          <!-- 复核结论 -->
          <div class="sec-title">电工复核结论</div>
          <el-descriptions v-if="c.status === 'REVIEWED'" :column="3" border size="small">
            <el-descriptions-item label="实测负载">{{ c.reviewLoadWatt }} W</el-descriptions-item>
            <el-descriptions-item label="负载率">{{ c.reviewLoadRatio }}%</el-descriptions-item>
            <el-descriptions-item label="复核时间">{{ c.reviewedAt }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="3">{{ c.reviewComment || '—' }}</el-descriptions-item>
          </el-descriptions>
          <el-text v-else type="info">整改后由电工实测线路负载，复核通过自动解除摊位大功率设备封锁并闭环异常档案。</el-text>
        </el-card>
        <el-empty v-else description="选择左侧工单查看详情，或登记新的跳闸事件" />
      </el-col>
    </el-row>

    <!-- 登记工单 -->
    <el-dialog v-model="createDlg" title="登记临时电箱跳闸" width="720px" top="5vh">
      <el-form :model="cf" label-width="120px">
        <el-form-item label="工单标题" required><el-input v-model="cf.title" placeholder="如：食品区1号临时电箱跳闸，多摊加热设备同时使用" /></el-form-item>
        <el-form-item label="电箱编号"><el-input v-model="cf.boxCode" placeholder="如 PDU-FOOD-01" /></el-form-item>
        <el-row :gutter="10">
          <el-col :span="8">
            <el-form-item label="电箱容量(W)"><el-input-number v-model="cf.boxCapacityWatt" :min="0" :step="500" style="width:100%;" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="申报合计(W)"><el-input-number v-model="cf.totalDeclaredWatt" :min="0" :step="100" style="width:100%;" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="实测合计(W)"><el-input-number v-model="cf.totalActualWatt" :min="0" :step="100" style="width:100%;" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="跳闸经过"><el-input v-model="cf.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="现场设备照片">
          <el-upload :show-file-list="false" :http-request="uploadCreatePhoto" accept="image/*" multiple>
            <el-button size="small">上传设备照片</el-button>
          </el-upload>
          <div style="display:flex; gap:6px; margin-top:6px; flex-wrap:wrap;">
            <el-image v-for="(u, i) in cf.photos" :key="i" :src="u" :preview-src-list="cf.photos" preview-teleported
                      fit="cover" style="width:64px;height:64px;border-radius:4px;" />
          </div>
        </el-form-item>
        <el-form-item label="涉及摊位">
          <el-select v-model="cf.partyIds" multiple filterable placeholder="选择同箱/受影响摊位" style="width:100%;">
            <el-option v-for="a in apps" :key="a.id"
                       :label="`${a.shopName}（申报${a.powerWanted || 0}W）`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-table v-if="cf.partyIds.length" :data="createParties" border size="small">
          <el-table-column label="摊位" min-width="130">
            <template #default="{ row }">{{ row.shopName }}</template>
          </el-table-column>
          <el-table-column label="申报W" width="110">
            <template #default="{ row }"><el-input-number v-model="row.declaredWatt" :min="0" :step="100" size="small" controls-position="right" style="width:100px;" /></template>
          </el-table-column>
          <el-table-column label="实测W" width="110">
            <template #default="{ row }"><el-input-number v-model="row.actualWatt" :min="0" :step="100" size="small" controls-position="right" style="width:100px;" /></template>
          </el-table-column>
          <el-table-column label="责任" width="64">
            <template #default="{ row }"><el-checkbox v-model="row.liable" /></template>
          </el-table-column>
          <el-table-column label="受波及" width="74">
            <template #default="{ row }"><el-checkbox v-model="row.affectedByOutage" /></template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="createDlg = false">取消</el-button>
        <el-button type="danger" @click="submitCreate">登记并同步异常档案</el-button>
      </template>
    </el-dialog>

    <!-- 责任判定 -->
    <el-dialog v-model="liabDlg" title="责任判定（申报/照片/电工记录三方比对）" width="520px">
      <el-form label-width="92px">
        <el-form-item label="责任结论">
          <el-select v-model="liab.liability" style="width:100%;">
            <el-option v-for="x in POWER_LIABILITY" :key="x.value" :label="x.label" :value="x.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任说明"><el-input v-model="liab.liabilityNote" type="textarea" :rows="3"
          placeholder="说明各摊加热设备、申报功率与电箱容量比对情况；责任摊主将扣信用分并封锁大功率设备" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="liabDlg = false">取消</el-button>
        <el-button type="danger" @click="submitLiability">确认判定</el-button>
      </template>
    </el-dialog>

    <!-- 电工处理 -->
    <el-dialog v-model="elecDlg" title="电工到场处理记录" width="520px">
      <el-form label-width="92px">
        <el-form-item label="电工姓名"><el-input v-model="elec.electricianName" /></el-form-item>
        <el-form-item label="处理记录">
          <el-input v-model="elec.electricianRecord" type="textarea" :rows="3"
                    placeholder="如：断开过载回路、排查加热设备、临时分路、合闸恢复供电" />
        </el-form-item>
        <el-form-item label="实测合计W"><el-input-number v-model="elec.totalActualWatt" :min="0" :step="100" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="elecDlg = false">取消</el-button>
        <el-button type="warning" @click="submitElectrician">保存记录</el-button>
      </template>
    </el-dialog>

    <!-- 处置措施 -->
    <el-dialog v-model="actDlg" title="下达处置措施" width="560px">
      <el-form label-width="100px">
        <el-form-item label="措施类型">
          <el-radio-group v-model="act.type">
            <el-radio-button v-for="x in POWER_ACTION_TYPES" :key="x.value" :value="x.value">{{ x.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="针对摊位" :required="act.type !== 'ADD_BOX'">
          <el-select v-model="act.applicationId" filterable :disabled="act.type === 'ADD_BOX'" style="width:100%;">
            <el-option v-for="a in apps" :key="a.id" :label="a.shopName" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="act.type === 'DISABLE_DEVICE'" label="停用设备">
          <el-input v-model="act.detail" placeholder="如：停用商用电磁炉/炸炉 1 台（2500W），仅保留保温设备" />
        </el-form-item>
        <el-form-item v-if="act.type === 'RELOCATE'" label="目标摊位" required>
          <el-select v-model="act.targetStallId" filterable placeholder="选择空闲摊位" style="width:100%;">
            <el-option v-for="s in freeStalls" :key="s.id"
                       :label="`${s.zone} ${s.code}（容量${s.powerCapacity}W）`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="act.type === 'ADD_BOX'" label="新增容量W" required>
          <el-input-number v-model="act.addedCapacityWatt" :min="100" :step="500" />
        </el-form-item>
        <el-form-item v-if="act.type !== 'DISABLE_DEVICE'" label="补充说明">
          <el-input v-model="act.detail" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actDlg = false">取消</el-button>
        <el-button type="primary" @click="submitAction">下达并执行</el-button>
      </template>
    </el-dialog>

    <!-- 补偿 -->
    <el-dialog v-model="compDlg" title="登记停电波及补偿" width="520px">
      <el-form label-width="100px">
        <el-form-item label="受影响摊位">
          <el-select v-model="comp.applicationId" filterable style="width:100%;">
            <el-option v-for="a in apps" :key="a.id" :label="a.shopName" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="补偿金额(元)"><el-input-number v-model="comp.amount" :min="0" :precision="2" :step="20" /></el-form-item>
        <el-form-item label="停电时长(分)"><el-input-number v-model="comp.outageMinutes" :min="0" :step="10" /></el-form-item>
        <el-form-item label="补偿说明"><el-input v-model="comp.reason" type="textarea" :rows="2"
          placeholder="如：断电 40 分钟导致饮品变质/无法营业，按核损补偿" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="compDlg = false">取消</el-button>
        <el-button @click="submitCompensation">登记（进入活动复盘）</el-button>
      </template>
    </el-dialog>

    <!-- 电工复核 -->
    <el-dialog v-model="reviewDlg" title="电工复核线路负载" width="520px">
      <el-alert type="info" :closable="false" style="margin-bottom:10px;"
                :title="`当前电箱容量 ${c?.boxCapacityWatt ?? 0}W；实测负载超过容量将不予通过，需继续整改。`" />
      <el-form label-width="110px">
        <el-form-item label="实测负载(W)"><el-input-number v-model="rev.reviewLoadWatt" :min="0" :step="100" /></el-form-item>
        <el-form-item label="复核备注"><el-input v-model="rev.reviewComment" type="textarea" :rows="3"
          placeholder="如：各分路电流正常、漏电保护有效、负载率约 70%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDlg = false">取消</el-button>
        <el-button type="success" @click="submitReview">复核通过并闭环</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, upload } from '../../api/client'
import { POWER_CASE_STATUS, POWER_LIABILITY, POWER_ACTION_TYPES, dictLabel, dictType } from '../../dict'

const eventId = Number(localStorage.getItem('cm_event_id'))
const apps = ref([])
const stalls = ref([])
const cases = ref([])
const currentId = ref(null)
const detail = ref(null)

const c = computed(() => detail.value?.powerCase || {})
const photoList = computed(() => (c.value.devicePhotoUrls || '').split(',').filter(Boolean))
const overTotal = computed(() => c.value.boxCapacityWatt && c.value.totalActualWatt
    && c.value.totalActualWatt > c.value.boxCapacityWatt)
const freeStalls = computed(() => stalls.value.filter((s) => s.status === 'FREE'))

const createDlg = ref(false)
const cf = reactive({ title: '', boxCode: '', description: '', boxCapacityWatt: 1000,
  totalDeclaredWatt: null, totalActualWatt: null, photos: [], partyIds: [] })
const createParties = computed(() => cf.partyIds.map((id) => {
  const a = apps.value.find((x) => x.id === id)
  return partyRows[id] || (partyRows[id] = {
    applicationId: id, shopName: a?.shopName, declaredWatt: a?.powerWanted || 0,
    actualWatt: null, liable: false, affectedByOutage: true, note: ''
  })
}))
const partyRows = reactive({})

const liabDlg = ref(false)
const liab = reactive({ liability: 'JOINT_OVERLOAD', liabilityNote: '' })
const elecDlg = ref(false)
const elec = reactive({ electricianName: '', electricianRecord: '', totalActualWatt: null })
const actDlg = ref(false)
const act = reactive({ type: 'DISABLE_DEVICE', applicationId: null, detail: '', targetStallId: null, addedCapacityWatt: 1000 })
const compDlg = ref(false)
const comp = reactive({ applicationId: null, amount: 50, outageMinutes: 30, reason: '' })
const reviewDlg = ref(false)
const rev = reactive({ reviewLoadWatt: null, reviewComment: '' })

function liabilityLabel(v) {
  return POWER_LIABILITY.find((x) => x.value === v)?.label || v
}
function actionLabel(v) {
  return POWER_ACTION_TYPES.find((x) => x.value === v)?.label || v
}
function actionType(v) {
  return POWER_ACTION_TYPES.find((x) => x.value === v)?.type || 'info'
}
function rowClass({ row }) {
  return row.id === currentId.value ? 'current-row-bold' : ''
}

async function load() {
  apps.value = await api.get(`/events/${eventId}/applications`)
  stalls.value = await api.get(`/stalls?eventId=${eventId}`)
  cases.value = await api.get(`/onsite/power/events/${eventId}/cases`)
  if (currentId.value) {
    detail.value = await api.get(`/onsite/power/cases/${currentId.value}`)
  }
}
async function openDetail(row) {
  currentId.value = row.id
  detail.value = await api.get(`/onsite/power/cases/${row.id}`)
}
function openCreate() {
  Object.assign(cf, { title: '', boxCode: '', description: '', boxCapacityWatt: 1000,
    totalDeclaredWatt: null, totalActualWatt: null, photos: [], partyIds: [] })
  Object.keys(partyRows).forEach((k) => delete partyRows[k])
  createDlg.value = true
}
async function uploadCreatePhoto(opt) {
  const fd = new FormData()
  fd.append('file', opt.file)
  const r = await upload('/onsite/power/photos', fd)
  cf.photos.push(r.url)
}
async function uploadPhoto(opt) {
  const fd = new FormData()
  fd.append('file', opt.file)
  const r = await upload('/onsite/power/photos', fd)
  await api.post(`/onsite/power/cases/${currentId.value}/photos`, { urls: [r.url] })
  ElMessage.success('照片已补充')
  await load()
}
async function submitCreate() {
  if (!cf.title) {
    ElMessage.warning('请填写工单标题')
    return
  }
  const body = {
    eventId, title: cf.title, boxCode: cf.boxCode, description: cf.description,
    devicePhotoUrls: cf.photos.join(','), boxCapacityWatt: cf.boxCapacityWatt,
    totalDeclaredWatt: cf.totalDeclaredWatt, totalActualWatt: cf.totalActualWatt,
    triggeredByApplicationId: createParties.value.find((p) => p.liable)?.applicationId || null,
    parties: createParties.value.map(({ shopName, ...p }) => p)
  }
  const created = await api.post('/onsite/power/cases', body)
  ElMessage.success('工单已登记，同步生成用电超载异常；责任摊位大功率设备已封锁')
  createDlg.value = false
  await load()
  await openDetail({ id: created.id })
}
async function saveParty(row) {
  await api.post(`/onsite/power/parties/${row.id}`, {
    applicationId: row.applicationId, declaredWatt: row.declaredWatt, actualWatt: row.actualWatt,
    liable: row.liable, affectedByOutage: row.affectedByOutage, note: row.note
  })
  ElMessage.success('涉及摊位信息已保存')
  await load()
}
function openLiability() {
  Object.assign(liab, { liability: c.value.liability || 'JOINT_OVERLOAD', liabilityNote: c.value.liabilityNote || '' })
  liabDlg.value = true
}
async function submitLiability() {
  await api.post(`/onsite/power/cases/${currentId.value}/liability`, liab)
  ElMessage.success('责任已判定：责任摊主扣信用分并维持大功率设备封锁')
  liabDlg.value = false
  await load()
}
function openElectrician() {
  Object.assign(elec, {
    electricianName: c.value.electricianName || '',
    electricianRecord: c.value.electricianRecord || '',
    totalActualWatt: c.value.totalActualWatt
  })
  elecDlg.value = true
}
async function submitElectrician() {
  if (!elec.electricianName || !elec.electricianRecord) {
    ElMessage.warning('电工姓名与处理记录必填')
    return
  }
  await api.post(`/onsite/power/cases/${currentId.value}/electrician`, elec)
  ElMessage.success('电工处理记录已保存')
  elecDlg.value = false
  await load()
}
function openAction() {
  Object.assign(act, { type: 'DISABLE_DEVICE', applicationId: null, detail: '', targetStallId: null, addedCapacityWatt: 1000 })
  actDlg.value = true
}
async function submitAction() {
  if (act.type !== 'ADD_BOX' && !act.applicationId) {
    ElMessage.warning('请选择针对摊位')
    return
  }
  if (act.type === 'RELOCATE' && !act.targetStallId) {
    ElMessage.warning('请选择调整目标摊位')
    return
  }
  await api.post(`/onsite/power/cases/${currentId.value}/actions`, act)
  ElMessage.success(act.type === 'RELOCATE' ? '已调整摊位并记录措施' : '措施已下达执行')
  actDlg.value = false
  await load()
}
function openCompensation() {
  Object.assign(comp, { applicationId: null, amount: 50, outageMinutes: 30, reason: '' })
  compDlg.value = true
}
async function submitCompensation() {
  if (!comp.applicationId) {
    ElMessage.warning('请选择受影响摊位')
    return
  }
  await api.post(`/onsite/power/cases/${currentId.value}/compensations`, comp)
  ElMessage.success('补偿已登记，金额与责任说明进入活动复盘')
  compDlg.value = false
  await load()
}
async function payComp(row) {
  await api.post(`/onsite/power/compensations/${row.id}/pay`)
  ElMessage.success('补偿已发放')
  await load()
}
async function submitRect() {
  await api.post(`/onsite/power/cases/${currentId.value}/submit-rectification`)
  ElMessage.success('已提交，等待电工复核线路负载')
  await load()
}
function openReview() {
  Object.assign(rev, { reviewLoadWatt: c.value.totalActualWatt || c.value.boxCapacityWatt || 0, reviewComment: '' })
  reviewDlg.value = true
}
async function submitReview() {
  try {
    await api.post(`/onsite/power/cases/${currentId.value}/review`, rev)
    ElMessage.success('复核通过：工单闭环、异常归档关闭、摊位大功率设备封锁解除')
    reviewDlg.value = false
    await load()
  } catch (e) {
    ElMessageBox.alert(e.message, '复核未通过', { type: 'warning' })
  }
}
onMounted(load)
</script>

<style scoped>
.sec-title {
  font-weight: 600;
  margin: 16px 0 8px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
}
.danger { color: #f56c6c; }
:deep(.current-row-bold) { background-color: #ecf5ff !important; }
</style>
