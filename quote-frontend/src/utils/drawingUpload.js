export const isPdfFile = (file) => {
  return Boolean(file?.name && file.name.toLowerCase().endsWith('.pdf'))
}

export const collectPdfFilesFromFileList = (fileList) => {
  return Array.from(fileList || []).filter(isPdfFile)
}

export const extractDrawingFolderName = (fileList) => {
  const files = Array.from(fileList || [])
  const firstPath = files
    .map(file => String(file?.webkitRelativePath || '').replace(/\\/g, '/'))
    .find(path => path.includes('/'))
  return firstPath ? firstPath.split('/').filter(Boolean)[0] || '' : ''
}

export const mergePdfFiles = (currentFiles = [], incomingFiles = []) => {
  const seen = new Set()
  return [...currentFiles, ...collectPdfFilesFromFileList(incomingFiles)].filter(file => {
    const key = `${file.name || ''}|${file.size ?? ''}|${file.lastModified ?? ''}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

const readEntryFile = (entry) => new Promise((resolve) => {
  entry.file(file => resolve(isPdfFile(file) ? file : null), () => resolve(null))
})

const readDirectoryEntries = async (directoryEntry) => {
  const reader = directoryEntry.createReader()
  const entries = []

  while (true) {
    const batch = await new Promise((resolve) => {
      reader.readEntries(resolve, () => resolve([]))
    })
    if (!batch.length) break
    entries.push(...batch)
  }

  return entries
}

const collectPdfFilesFromEntry = async (entry) => {
  if (!entry) return []
  if (entry.isFile) {
    const file = await readEntryFile(entry)
    return file ? [file] : []
  }
  if (entry.isDirectory) {
    const entries = await readDirectoryEntries(entry)
    const nestedFiles = await Promise.all(entries.map(collectPdfFilesFromEntry))
    return nestedFiles.flat()
  }
  return []
}

export const collectPdfFilesFromDataTransfer = async (dataTransfer) => {
  const items = Array.from(dataTransfer?.items || [])
  const entries = items
    .map(item => (typeof item.webkitGetAsEntry === 'function' ? item.webkitGetAsEntry() : null))
    .filter(Boolean)

  if (entries.length > 0) {
    const files = await Promise.all(entries.map(collectPdfFilesFromEntry))
    return files.flat()
  }

  return collectPdfFilesFromFileList(dataTransfer?.files)
}
