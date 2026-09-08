export const isPdfFile = (file) => {
  return Boolean(file?.name && file.name.toLowerCase().endsWith('.pdf'))
}

const hasFilename = (file) => Boolean(file?.name)

export const collectFilesFromFileList = (fileList) => {
  return Array.from(fileList || []).filter(hasFilename)
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

const mergeUniqueFiles = (currentFiles = [], incomingFiles = []) => {
  const seen = new Set()
  return [...currentFiles, ...incomingFiles].filter(file => {
    const key = `${file.name || ''}|${file.size ?? ''}|${file.lastModified ?? ''}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

export const mergeFiles = (currentFiles = [], incomingFiles = []) => {
  return mergeUniqueFiles(currentFiles, collectFilesFromFileList(incomingFiles))
}

export const mergePdfFiles = (currentFiles = [], incomingFiles = []) => {
  return mergeUniqueFiles(currentFiles, collectPdfFilesFromFileList(incomingFiles))
}

const readEntryFile = (entry, acceptFile) => new Promise((resolve) => {
  entry.file(file => resolve(acceptFile(file) ? file : null), () => resolve(null))
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

const collectFilesFromEntry = async (entry, acceptFile) => {
  if (!entry) return []
  if (entry.isFile) {
    const file = await readEntryFile(entry, acceptFile)
    return file ? [file] : []
  }
  if (entry.isDirectory) {
    const entries = await readDirectoryEntries(entry)
    const nestedFiles = await Promise.all(entries.map(nestedEntry => collectFilesFromEntry(nestedEntry, acceptFile)))
    return nestedFiles.flat()
  }
  return []
}

const collectAcceptedFilesFromDataTransfer = async (dataTransfer, acceptFile, fallbackCollector) => {
  const items = Array.from(dataTransfer?.items || [])
  const entries = items
    .map(item => (typeof item.webkitGetAsEntry === 'function' ? item.webkitGetAsEntry() : null))
    .filter(Boolean)

  if (entries.length > 0) {
    const files = await Promise.all(entries.map(entry => collectFilesFromEntry(entry, acceptFile)))
    return files.flat()
  }

  return fallbackCollector(dataTransfer?.files)
}

export const collectFilesFromDataTransfer = async (dataTransfer) => {
  return collectAcceptedFilesFromDataTransfer(dataTransfer, hasFilename, collectFilesFromFileList)
}

export const collectPdfFilesFromDataTransfer = async (dataTransfer) => {
  return collectAcceptedFilesFromDataTransfer(dataTransfer, isPdfFile, collectPdfFilesFromFileList)
}
