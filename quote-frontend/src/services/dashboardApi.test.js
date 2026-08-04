import { beforeEach, describe, expect, test, vi } from 'vitest'
import { request } from '../api'
import { getDashboardOverview } from './dashboardApi'

vi.mock('../api', () => ({
  request: vi.fn()
}))

beforeEach(() => {
  request.mockReset()
})

describe('dashboardApi', () => {
  test('loads dashboard overview from the read-only aggregate endpoint', async () => {
    await getDashboardOverview()

    expect(request).toHaveBeenCalledWith('/api/dashboard/overview')
  })
})
