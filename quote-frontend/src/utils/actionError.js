const firstText = (...values) => values
  .map(value => (typeof value === 'string' ? value.trim() : ''))
  .find(Boolean)

export const readActionError = (error, fallback = '') => firstText(
  error?.response?.data?.message,
  error?.payload?.message,
  error?.message,
  fallback
) || fallback
