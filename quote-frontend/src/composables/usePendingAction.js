import { ref } from 'vue'

export const usePendingAction = () => {
  const pendingKeys = ref(new Set())

  const normalizeKey = (key) => String(key)

  const isPending = (key) => pendingKeys.value.has(normalizeKey(key))

  const setPending = (key, value) => {
    const next = new Set(pendingKeys.value)
    const normalizedKey = normalizeKey(key)

    if (value) {
      next.add(normalizedKey)
    } else {
      next.delete(normalizedKey)
    }

    pendingKeys.value = next
  }

  const runAction = async (key, action) => {
    if (isPending(key)) return undefined

    setPending(key, true)
    try {
      return await action()
    } finally {
      setPending(key, false)
    }
  }

  return {
    isPending,
    runAction,
    setPending
  }
}
