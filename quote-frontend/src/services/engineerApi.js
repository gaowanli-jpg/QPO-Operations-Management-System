import { request } from '../api'

export const saveOrderConfig = (orderId, config) => request(`/api/engineer/order-config/${orderId}`, {
  method: 'POST',
  data: config
})

export const applyRepeatQuotes = (orderId) => request(`/api/engineer/apply-repeat-quotes/${orderId}`, {
  method: 'POST'
})

export const getRepeatAnalysis = (rfqId) => request(`/api/engineer/repeat-analysis/${rfqId}`)
