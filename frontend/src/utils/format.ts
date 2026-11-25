function pad(value: number): string {
  return value < 10 ? `0${value}` : String(value)
}

export function formatDateTime(value?: string | number | Date | null): string {
  if (value === undefined || value === null || value === '') {
    return '-'
  }
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function formatClock(value: string | number | Date): string {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function formatBytes(size?: number): string {
  if (size === undefined || size === null || Number.isNaN(size)) {
    return '-'
  }
  if (size < 1024) {
    return `${size} B`
  }
  const units = ['KB', 'MB', 'GB']
  let value = size / 1024
  let index = 0
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024
    index += 1
  }
  return `${value.toFixed(1)} ${units[index]}`
}

/** 相似度分数：后端 VectorSearchResult.score 是 0..1 的 Cosine 相关度。 */
export function formatScore(score: number): string {
  if (!Number.isFinite(score)) {
    return '-'
  }
  return score.toFixed(4)
}

export function formatPercent(value?: number | null): string {
  if (value === undefined || value === null || !Number.isFinite(value)) {
    return '-'
  }
  return `${(value * 100).toFixed(1)}%`
}

export function truncate(text: string, max = 120): string {
  if (text.length <= max) {
    return text
  }
  return `${text.slice(0, max)}…`
}

/** metadata 里除内部字段外的可读键值，用于切片来源展示。 */
export function readableMetadata(metadata?: Record<string, unknown>): Array<[string, string]> {
  if (!metadata) {
    return []
  }
  const hidden = new Set(['index_flag', 'distance', '_id_is_generated'])
  return Object.entries(metadata)
    .filter(([key]) => !key.startsWith('{') && !hidden.has(key))
    .map(([key, value]) => [key, typeof value === 'object' ? JSON.stringify(value) : String(value)] as [string, string])
}

export function metadataValue(metadata: Record<string, unknown> | undefined, key: string): string {
  const value = metadata?.[key]
  return value === undefined || value === null ? '-' : String(value)
}

export function newId(prefix: string): string {
  const cryptoRef = globalThis.crypto
  if (cryptoRef && 'randomUUID' in cryptoRef) {
    return `${prefix}_${cryptoRef.randomUUID().slice(0, 8)}`
  }
  return `${prefix}_${Math.random().toString(36).slice(2, 10)}`
}
