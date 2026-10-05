// 业务字典
export const STALL_TYPES = [
  { value: 'HANDMADE', label: '手作' },
  { value: 'SECONDHAND', label: '二手物品' },
  { value: 'FOOD', label: '食品' },
  { value: 'CHARITY', label: '公益义卖' },
  { value: 'COMMERCIAL', label: '商业' }
]

export const FOOD_QUAL = [
  { value: 'NONE', label: '无需食品资质' },
  { value: 'COMPLETE', label: '资质齐全' },
  { value: 'INCOMPLETE', label: '资质不完整' }
]

export const PAY_METHODS = [
  { value: 'CASH', label: '现金' },
  { value: 'WECHAT', label: '微信' },
  { value: 'ALIPAY', label: '支付宝' },
  { value: 'MIXED', label: '混合收款' }
]

export const APP_STATUS = [
  { value: 'SUBMITTED', label: '待审核', type: 'warning' },
  { value: 'APPROVED', label: '审核通过', type: 'success' },
  { value: 'REJECTED', label: '审核驳回', type: 'danger' }
]

export const CHECKIN_STATUS = [
  { value: 'NOT_CHECKED', label: '未入场', type: 'info' },
  { value: 'CHECKED_IN', label: '已入场', type: 'success' },
  { value: 'LEFT_EARLY', label: '提前离场', type: 'danger' }
]

export const INCIDENT_TYPES = [
  { value: 'FOOD_QUALIFICATION', label: '食品资质不完整' },
  { value: 'MISMATCH', label: '现场售卖与报名不符' },
  { value: 'CATEGORY_CONFLICT', label: '同类摊位冲突' },
  { value: 'POWER_OVERLOAD', label: '用电超载' },
  { value: 'EARLY_LEAVE', label: '摊主提前离场' },
  { value: 'DONATION_MISMATCH', label: '捐赠金额与流水不一致' },
  { value: 'PUBLIC_REQUEST', label: '公益组织要求公示明细' },
  { value: 'OTHER', label: '其他' }
]

export const EVENT_STATUS = [
  { value: 'RECRUITING', label: '招募中', type: 'success' },
  { value: 'REVIEWING', label: '审核分配中', type: 'warning' },
  { value: 'ONGOING', label: '进行中', type: 'primary' },
  { value: 'FINISHED', label: '已结束', type: 'info' },
  { value: 'PUBLISHED', label: '已公示', type: '' }
]

export const DONATION_STATUS = [
  { value: 'PENDING', label: '待核对', type: 'info' },
  { value: 'CONFIRMED', label: '已核对', type: 'success' },
  { value: 'DISPUTED', label: '流水不符', type: 'danger' }
]

export const COMPLAINT_STATUS = [
  { value: 'OPEN', label: '待处理', type: 'danger' },
  { value: 'PROCESSING', label: '处理中', type: 'warning' },
  { value: 'RESOLVED', label: '已处理', type: 'success' }
]

export const POWER_CASE_STATUS = [
  { value: 'REPORTED', label: '已登记待处置', type: 'danger' },
  { value: 'HANDLING', label: '处置中', type: 'warning' },
  { value: 'PENDING_REVIEW', label: '整改完成待复核', type: 'primary' },
  { value: 'REVIEWED', label: '复核通过已闭环', type: 'success' }
]

export const POWER_LIABILITY = [
  { value: 'VENDOR_EXCESS', label: '摊主超额用电' },
  { value: 'JOINT_OVERLOAD', label: '多摊叠加共同超载' },
  { value: 'CAPACITY_INSUFFICIENT', label: '电箱容量配置不足' },
  { value: 'MIXED', label: '摊主与场地混合责任' }
]

export const POWER_ACTION_TYPES = [
  { value: 'DISABLE_DEVICE', label: '停用设备', type: 'danger' },
  { value: 'RELOCATE', label: '调整摊位', type: 'warning' },
  { value: 'ADD_BOX', label: '增加电箱/分路', type: 'success' }
]

export function dictLabel(list, value) {
  return list.find((x) => x.value === value)?.label || value
}

export function dictType(list, value) {
  return list.find((x) => x.value === value)?.type || 'info'
}
