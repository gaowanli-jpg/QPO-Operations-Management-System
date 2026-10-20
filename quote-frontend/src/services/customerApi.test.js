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

  test('triggers manual U9 customer cache sync without exposing local write APIs', async () => {
    await customerApi.syncCustomers()

    expect(request).toHaveBeenCalledWith('/api/system/customers/sync', {
      method: 'POST'
    })
    expect(customerApi.createCustomer).toBeUndefined()
    expect(customerApi.updateCustomer).toBeUndefined()
    expect(customerApi.deleteCustomer).toBeUndefined()
  })

  test('loads and saves U9 customer timed sync schedule strategy', async () => {
    const payload = { enabled: true, startupDelayMs: 30_000, fixedDelayMs: 1_800_000 }

    await customerApi.getCustomerSyncSchedule()
    await customerApi.saveCustomerSyncSchedule(payload)

    expect(request).toHaveBeenNthCalledWith(1, '/api/system/customers/sync-schedule')
    expect(request).toHaveBeenNthCalledWith(2, '/api/system/customers/sync-schedule', {
      method: 'PUT',
      data: payload
    })
  })
})
