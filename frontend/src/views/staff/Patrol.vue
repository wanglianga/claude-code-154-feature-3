<template>
  <div class="page-container">
    <el-row :gutter="14">
      <el-col :span="10">
        <el-card header="现场巡检登记">
          <el-form :model="form" label-width="110px">
            <el-form-item label="选择摊位">
              <el-select v-model="form.applicationId" filterable placeholder="选择摊主" style="width:100%">
                <el-option v-for="a in apps" :key="a.id"
                           :label="`${a.shopName}（${a.stall ? a.stall.code : '未分配'}）`" :value="a.id" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="selected" label="报名内容">
              <el-text type="info">{{ selected.goodsDescription }}</el-text>
            </el-form-item>
            <el-form-item v-if="selected?.powerLocked" label=" ">
              <el-alert type="error" :closable="false" show-icon style="width:100%;"
                        title="该摊大功率设备封锁中（电工未复核）"
                        :description="selected.powerLockReason || '整改复核通过前不得继续使用 500W 以上设备'" />
            </el-form-item>
            <el-form-item label="实际售卖内容">
              <el-input v-model="form.actualGoods" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="与报名相符">
              <el-switch v-model="form.matched" active-text="相符" inactive-text="不符" />
            </el-form-item>
            <el-form-item label="临时换品">
              <el-switch v-model="form.goodsChanged" />
            </el-form-item>
            <el-form-item v-if="form.goodsChanged" label="换品说明">
              <el-input v-model="form.changeDescription" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item v-if="selected?.stall?.powerAvailable" label="实测用电(W)">
              <el-input-number v-model="form.powerUsedWatt" :min="0" :step="100" />
              <el-text v-if="selected && form.powerUsedWatt > selected.stall.powerCapacity" type="danger" style="margin-left:8px;">
                超核定 {{ selected.stall.powerCapacity }}W，将登记超载异常
              </el-text>
            </el-form-item>
            <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
            <el-button type="primary" style="width:100%" @click="submit">提交巡检</el-button>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card header="巡检 / 临时换品记录">
          <el-table :data="inspections" border size="small">
            <el-table-column prop="createdAt" label="时间" width="170" />
            <el-table-column prop="applicationId" label="报名ID" width="80" />
            <el-table-column prop="actualGoods" label="实际售卖" min-width="150" show-overflow-tooltip />
            <el-table-column label="相符" width="70">
              <template #default="{ row }">
                <el-tag size="small" :type="row.matched ? 'success' : 'danger'">{{ row.matched ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="换品" width="70">
              <template #default="{ row }">
                <el-tag v-if="row.goodsChanged" size="small" type="warning">是</el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column prop="changeDescription" label="换品说明" min-width="140" show-overflow-tooltip />
            <el-table-column prop="powerUsedWatt" label="用电W" width="80" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/client'

const eventId = Number(localStorage.getItem('cm_event_id'))
const apps = ref([])
const inspections = ref([])
const form = reactive({
  applicationId: null, actualGoods: '', matched: true, goodsChanged: false,
  changeDescription: '', powerUsedWatt: null, remark: ''
})

const selected = computed(() => apps.value.find((a) => a.id === form.applicationId))

async function submit() {
  if (!form.applicationId || !form.actualGoods) {
    ElMessage.warning('请选择摊位并填写实际售卖内容')
    return
  }
  await api.post('/onsite/inspections', { ...form })
  ElMessage.success('巡检已提交（不符/超载将自动进入异常档案并影响信用）')
  Object.assign(form, { actualGoods: '', matched: true, goodsChanged: false, changeDescription: '', powerUsedWatt: null, remark: '' })
  load()
}
async function load() {
  apps.value = await api.get(`/events/${eventId}/applications`)
  inspections.value = await api.get(`/onsite/events/${eventId}/inspections`)
}
onMounted(load)
</script>
