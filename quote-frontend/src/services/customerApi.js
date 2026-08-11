import { request } from '../api'

export const listCustomers = (params = {}) => request('/api/system/customers', { params })

export const listCustomerOptions = (params = {}) => request('/api/system/customers/options', { params })

export const syncCustomers = () => request('/api/system/customers/sync', {
  method: 'POST'
})
