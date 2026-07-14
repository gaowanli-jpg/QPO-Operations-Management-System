import { ElMessageBox } from 'element-plus'

export const confirmAction = async (message, options = {}) => {
  const { title = '\u786e\u8ba4\u64cd\u4f5c', ...confirmOptions } = options
  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: '\u786e\u8ba4',
      cancelButtonText: '\u53d6\u6d88',
      ...confirmOptions
    })
    return true
  } catch {
    return false
  }
}
