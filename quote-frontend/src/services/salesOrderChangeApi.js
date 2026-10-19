import { request } from '../api'

const BASE = '/api/change/sales-order-changes'

export const listSalesOrderChangeNotices = (params = {}) => request(BASE, { params })
export const getSalesOrderChangeNotice = (noticeId) => request(`${BASE}/${noticeId}`)
export const listSalesOrderChangeCustomers = (params = {}) => request(`${BASE}/customers`, { params })
export const getSalesOrderChangeDispatchSetting = () => request(`${BASE}/settings`)
export const updateSalesOrderChangeDispatchMode = (dispatchMode) => request(`${BASE}/settings/mode`, {
  method: 'PUT',
  data: { dispatchMode }
})
export const sendSalesOrderChangeNotices = (noticeIds = []) => request(`${BASE}/batch-send`, {
  method: 'POST',
  data: { noticeIds }
})
export const hideSalesOrderChangeNotices = (noticeIds = [], reason) => request(`${BASE}/batch-hide`, {
  method: 'POST',
  data: { noticeIds, reason }
})
export const restoreSalesOrderChangeNotices = (noticeIds = [], reason = '') => request(`${BASE}/batch-restore`, {
  method: 'POST',
  data: { noticeIds, reason }
})
