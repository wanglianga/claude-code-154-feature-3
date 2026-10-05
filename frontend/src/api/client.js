// 简单封装 fetch，自动携带 token、解包 ApiResult
const BASE = '/api'

export function getToken() {
  return localStorage.getItem('cm_token') || ''
}

export function setSession(token, user) {
  localStorage.setItem('cm_token', token)
  localStorage.setItem('cm_user', JSON.stringify(user))
}

export function clearSession() {
  localStorage.removeItem('cm_token')
  localStorage.removeItem('cm_user')
}

export function currentUser() {
  try {
    return JSON.parse(localStorage.getItem('cm_user') || 'null')
  } catch {
    return null
  }
}

async function request(method, path, body) {
  const headers = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) {
    headers['Authorization'] = 'Bearer ' + token
  }
  const resp = await fetch(BASE + path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined
  })
  return await unwrap(resp)
}

/** multipart/form-data 上传（现场设备照片），不要手动设置 Content-Type */
export async function upload(path, formData) {
  const headers = {}
  const token = getToken()
  if (token) {
    headers['Authorization'] = 'Bearer ' + token
  }
  const resp = await fetch(BASE + path, { method: 'POST', headers, body: formData })
  return await unwrap(resp)
}

async function unwrap(resp) {
  if (resp.status === 401) {
    clearSession()
    if (!location.hash.startsWith('#/login') && !location.hash.startsWith('#/public')) {
      location.hash = '#/login'
    }
    throw new Error('未登录或登录已失效')
  }
  const json = await resp.json()
  if (json.code !== 0) {
    throw new Error(json.message || '请求失败')
  }
  return json.data
}

export const api = {
  get: (p) => request('GET', p),
  post: (p, b) => request('POST', p, b ?? {}),
  put: (p, b) => request('PUT', p, b ?? {})
}
