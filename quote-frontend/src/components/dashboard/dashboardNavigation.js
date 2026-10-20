export const canShowDashboardAction = (item = {}, authStore) => {
  if (!item.permission) return true
  if (!authStore || typeof authStore.hasPermission !== 'function') return false
  return authStore.hasPermission(item.permission)
}

export const goDashboardTarget = (router, item = {}) => {
  const path = item.path || item.targetPath || item.target?.path
  const query = item.query || item.target?.query || {}

  if (!path || !router || typeof router.push !== 'function') {
    return Promise.resolve(false)
  }

  return router.push({ path, query })
}

export const formatDashboardNumber = (value) => {
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue)) return '0'
  return numericValue.toLocaleString('zh-CN')
}
