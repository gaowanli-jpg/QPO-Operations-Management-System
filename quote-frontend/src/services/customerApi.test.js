import { beforeEach, describe, expect, test, vi } from 'vitest'
import { request } from '../api'
import * as customerApi from './customerApi'

vi.mock('../api', () => ({
  request: vi.fn()
}))

beforeEach(() => {
  request.mockReset()
})

describe('customerApi', () => {
  test('lists customer master data with paging filters', async () => {
    const params = { field: 'shortName', keyword: 'ABC', page: 2, pageSize: 20 }

    await customerApi.listCustomers(params)

    expect(request).toHaveBeenCalledWith('/api/system/customers', { params })
  })

  test('loads customer options for reusable selectors', async () => {
    const params = { field: 'name', keyword: 'Tech' }

    await customerApi.listCustomerOptions(params)

    expect(request).toHaveBeenCalledWith('/api/system/customers/options', { params })
  })

  test('creates updates and deletes customer master records', async () => {
    const payload = {
      customerCode: 'C001',
      customerShortName: 'ABC',
      customerName: 'ABC Technology'
    }

    await customerApi.createCustomer(payload)
    await customerApi.updateCustomer(9, payload)
    await customerApi.deleteCustomer(9)

    expect(request).toHaveBeenNthCalledWith(1, '/api/system/customers', {
      method: 'POST',
      data: payload
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/system/customers/9', {
      method: 'PUT',
      data: payload
    })
    expect(request).toHaveBeenNthCalledWith(3, '/api/system/customers/9', {
      method: 'DELETE'
    })
  })
})
