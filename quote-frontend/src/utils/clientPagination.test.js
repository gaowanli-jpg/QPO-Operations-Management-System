import { describe, expect, test } from 'vitest'
import { clampPage, paginateRows } from './clientPagination'

describe('client pagination helpers', () => {
  test('returns only rows for the current page', () => {
    const rows = Array.from({ length: 45 }, (_, index) => ({ id: index + 1 }))

    expect(paginateRows(rows, { page: 2, pageSize: 20 }).map(row => row.id)).toEqual(
      Array.from({ length: 20 }, (_, index) => index + 21)
    )
    expect(paginateRows(rows, { page: 3, pageSize: 20 }).map(row => row.id)).toEqual([41, 42, 43, 44, 45])
  })

  test('clamps the active page when list size shrinks', () => {
    expect(clampPage({ page: 9, pageSize: 20 }, 45)).toBe(3)
    expect(clampPage({ page: 0, pageSize: 20 }, 45)).toBe(1)
    expect(clampPage({ page: 3, pageSize: 20 }, 0)).toBe(1)
  })
})
