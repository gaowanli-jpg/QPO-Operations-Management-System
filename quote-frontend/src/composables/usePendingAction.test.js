import { describe, expect, test } from 'vitest'
import { usePendingAction } from './usePendingAction'
import { readActionError } from '../utils/actionError'

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((nextResolve, nextReject) => {
    resolve = nextResolve
    reject = nextReject
  })
  return { promise, resolve, reject }
}

describe('usePendingAction', () => {
  test('blocks duplicate calls for the same pending key', async () => {
    const { isPending, runAction } = usePendingAction()
    const gate = deferred()
    let calls = 0

    const first = runAction('save:item:1', async () => {
      calls += 1
      await gate.promise
      return 'saved'
    })
    const duplicate = runAction('save:item:1', async () => {
      calls += 1
      return 'duplicate'
    })

    expect(isPending('save:item:1')).toBe(true)
    expect(await duplicate).toBeUndefined()
    expect(calls).toBe(1)

    gate.resolve()
    await expect(first).resolves.toBe('saved')
    expect(isPending('save:item:1')).toBe(false)
  })

  test('restores pending state after an action fails', async () => {
    const { isPending, runAction } = usePendingAction()

    await expect(runAction('save:item:2', async () => {
      throw new Error('network failed')
    })).rejects.toThrow('network failed')

    expect(isPending('save:item:2')).toBe(false)
  })

  test('allows different keys to run concurrently', async () => {
    const { isPending, runAction } = usePendingAction()
    const firstGate = deferred()
    const secondGate = deferred()
    let calls = 0

    const first = runAction('mes:ticket:1', async () => {
      calls += 1
      await firstGate.promise
      return 'mes'
    })
    const second = runAction('wms:ticket:1', async () => {
      calls += 1
      await secondGate.promise
      return 'wms'
    })

    expect(isPending('mes:ticket:1')).toBe(true)
    expect(isPending('wms:ticket:1')).toBe(true)
    expect(calls).toBe(2)

    firstGate.resolve()
    secondGate.resolve()
    await expect(Promise.all([first, second])).resolves.toEqual(['mes', 'wms'])
    expect(isPending('mes:ticket:1')).toBe(false)
    expect(isPending('wms:ticket:1')).toBe(false)
  })

  test('supports manually setting pending state', () => {
    const { isPending, setPending } = usePendingAction()

    setPending('export:quote:1', true)
    expect(isPending('export:quote:1')).toBe(true)

    setPending('export:quote:1', false)
    expect(isPending('export:quote:1')).toBe(false)
  })
})

describe('readActionError', () => {
  test('prefers backend response message over generic error message', () => {
    const error = {
      message: 'Request failed',
      response: {
        data: {
          message: '订单状态已变更，请刷新后重试'
        }
      }
    }

    expect(readActionError(error, '操作失败')).toBe('订单状态已变更，请刷新后重试')
  })

  test('falls back when no useful error message exists', () => {
    expect(readActionError({}, '保存失败，请稍后重试')).toBe('保存失败，请稍后重试')
  })
})
