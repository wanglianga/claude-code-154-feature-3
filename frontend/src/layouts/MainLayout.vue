<template>
  <el-container style="height: 100vh">
    <el-aside width="230px" style="background: #1f2d3d; color: #fff; overflow-y: auto;">
      <div style="padding: 18px 16px; font-size: 16px; font-weight: 700; line-height: 1.4;">
        🤝 社区公益集市平台
      </div>
      <el-menu :default-active="$route.path" router background-color="#1f2d3d" text-color="#c0ccda"
               active-text-color="#ffd04b">
        <el-menu-item index="/dashboard"><el-icon><Odometer /></el-icon><span>工作台</span></el-menu-item>

        <el-sub-menu v-if="auth.isAdmin" index="mgmt">
          <template #title><el-icon><OfficeBuilding /></el-icon><span>社区管理</span></template>
          <el-menu-item index="/events">活动与公益目标</el-menu-item>
          <el-menu-item index="/users">账号管理</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="auth.isAdmin || auth.isStaff" index="work">
          <template #title><el-icon><Bell /></el-icon><span>现场工作</span></template>
          <el-menu-item index="/onsite/checkin">核销入场 / 物资</el-menu-item>
          <el-menu-item index="/onsite/patrol">巡检 / 临时换品</el-menu-item>
          <el-menu-item index="/onsite/power">用电超载处置</el-menu-item>
          <el-menu-item index="/onsite/complaints">投诉与异常</el-menu-item>
          <el-menu-item index="/onsite/finance">收款与捐赠核对</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="auth.isVendor" index="v">
          <template #title><el-icon><Shop /></el-icon><span>摊主中心</span></template>
          <el-menu-item index="/vendor/apply">我要报名</el-menu-item>
          <el-menu-item index="/vendor/records">我的报名 / 入场码</el-menu-item>
          <el-menu-item index="/vendor/credit">我的信用</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/public"><el-icon><View /></el-icon><span>居民公示端</span></el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header style="background: #fff; display: flex; align-items: center; justify-content: space-between;
                        box-shadow: 0 1px 4px rgba(0,21,41,.08);">
        <div style="font-weight: 600; color: #303133;">{{ title }}</div>
        <div>
          <el-tag size="small" :type="roleTag.type" effect="plain" style="margin-right: 10px;">
            {{ roleTag.label }}
          </el-tag>
          <span style="margin-right: 12px; color: #606266;">{{ auth.user?.realName || auth.user?.username }}</span>
          <el-button text type="primary" @click="logout">退出登录</el-button>
        </div>
      </el-header>
      <el-main style="background: #f5f7fa; overflow-y: auto;">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const titles = {
  '/dashboard': '工作台',
  '/events': '活动与公益目标管理',
  '/users': '账号管理',
  '/onsite/checkin': '扫码核销入场与物资发放',
  '/onsite/patrol': '现场巡检与临时换品',
  '/onsite/power': '用电超载处置（跳闸判责 / 停用 / 调摊 / 加电箱 / 电工复核）',
  '/onsite/complaints': '投诉与现场异常',
  '/onsite/finance': '收款统计与捐赠核对',
  '/vendor/apply': '摊主报名',
  '/vendor/records': '我的报名与入场二维码',
  '/vendor/credit': '我的信用记录'
}
const title = computed(() => {
  if (route.path.includes('/stalls')) return '摊位平面图'
  if (route.path.includes('/applications')) return '摊主报名审核'
  if (route.path.includes('/allocate')) return '智能摊位分配'
  if (route.path.includes('/archive')) return '活动统一档案'
  if (route.path.includes('/review')) return '活动复盘与招商建议'
  if (route.path.includes('/publicity')) return '居民公示发布'
  return titles[route.path] || '社区公益集市平台'
})

const roleTag = computed(() => {
  if (auth.isAdmin) return { label: '社区管理员', type: 'danger' }
  if (auth.isStaff) return { label: '现场工作人员', type: 'warning' }
  return { label: '摊主', type: 'success' }
})

function logout() {
  auth.logout()
  router.push('/login')
}
</script>
