import { request } from '../api'

export const listCustomers = (params = {}) => request('/api/system/customers', { params })

export const listCustomerOptions = (params = {}) => request('/api/system/customers/options', { params })

export const createCustomer = (payload) => request('/api/system/customers', {
  method: 'POST',
  data: payload
})

export const updateCustomer = (id, payload) => request(`/api/system/customers/${id}`, {
  method: 'PUT',
  data: payload
})

export const deleteCustomer = (id) => request(`/api/system/customers/${id}`, {
  method: 'DELETE'
})
