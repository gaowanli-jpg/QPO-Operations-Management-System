export const CLIENT_PAGE_SIZES = [20, 50, 100]
export const DEFAULT_CLIENT_PAGE_SIZE = 20
export const DEFAULT_PAGER_COUNT = 7

export const clampPage = (pagination, total) => {
  const pageSize = Math.max(1, Number(pagination?.pageSize) || DEFAULT_CLIENT_PAGE_SIZE)
  const page = Math.max(1, Number(pagination?.page) || 1)
  const maxPage = Math.max(1, Math.ceil((Number(total) || 0) / pageSize))
  return Math.min(page, maxPage)
}

export const paginateRows = (rows, pagination) => {
  const list = Array.isArray(rows) ? rows : []
  const pageSize = Math.max(1, Number(pagination?.pageSize) || DEFAULT_CLIENT_PAGE_SIZE)
  const page = clampPage(pagination, list.length)
  const start = (page - 1) * pageSize
  return list.slice(start, start + pageSize)
}
