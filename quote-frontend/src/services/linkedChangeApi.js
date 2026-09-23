import { request } from '../api'

const BASE = '/api/change/linked-changes'

export const listLinkedChanges = (params = {}) => request(BASE, { params })
export const getLinkedChange = (id) => request(`${BASE}/${id}`)
export const listLinkedChangeCustomers = (params = {}) => request(`${BASE}/customers`, { params })
export const handleLinkedChange = (id, payload = {}) => request(`${BASE}/${id}/handle`, {
  method: 'POST',
  data: payload
})
export const reopenLinkedChange = (id, payload = {}) => request(`${BASE}/${id}/reopen`, {
  method: 'POST',
  data: payload
})
