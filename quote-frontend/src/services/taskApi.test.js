import { beforeEach, describe, expect, test, vi } from 'vitest'
import { request } from '../api'
import * as taskApi from './taskApi'

vi.mock('../api', () => ({
  request: vi.fn()
}))

beforeEach(() => {
  request.mockReset()
})

describe('taskApi', () => {
  test('refreshEngineerQuotes posts refresh task by rfq id', async () => {
    await taskApi.refreshEngineerQuotes(12)

    expect(request).toHaveBeenCalledWith('/api/tasks/engineer/refresh-quotes/12', {
      method: 'POST'
    })
  })

  test('getTask loads task by id', async () => {
    await taskApi.getTask(98)

    expect(request).toHaveBeenCalledWith('/api/tasks/98')
  })
})
