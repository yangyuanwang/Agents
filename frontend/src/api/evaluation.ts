import { request } from './http'
import type { EvaluationCaseResult } from '@/types/domain'
import type { VectorSearchResult } from '@/types/vector'

/** POST /api/evaluations —— 待开发：Faithfulness / Answer Relevancy 需要 LLM 判定接口。 */
export function runEvaluation(payload: unknown): Promise<EvaluationCaseResult[]> {
  return request<EvaluationCaseResult[]>({ method: 'post', url: '/evaluations', data: payload, silent: true })
}

/** GET /api/evaluations —— 待开发：评估历史。 */
export function listEvaluations(): Promise<unknown[]> {
  return request<unknown[]>({ method: 'get', url: '/evaluations', silent: true })
}

export interface HitJudgement {
  hit: boolean
  rank: number | null
}

/**
 * 命中率判定：在真实检索结果里找期望片段。
 * 期望值既可以是片段 id，也可以是内容关键词（大小写不敏感）。
 * 这是纯计算，输入来自 POST /api/vector/search 的真实返回。
 */
export function judgeHit(results: VectorSearchResult[], expect: string): HitJudgement {
  const needle = expect.trim().toLowerCase()
  if (!needle) {
    return { hit: false, rank: null }
  }

  for (let index = 0; index < results.length; index += 1) {
    const item = results[index]
    const idMatched = item.id.toLowerCase() === needle
    const contentMatched = (item.content ?? '').toLowerCase().includes(needle)
    if (idMatched || contentMatched) {
      return { hit: true, rank: index + 1 }
    }
  }

  return { hit: false, rank: null }
}

/** 汇总命中率与平均倒数排名（MRR），两者都只由上面的真实判定得出。 */
export function summarize(items: EvaluationCaseResult[]): {
  total: number
  hits: number
  hitRate: number | null
  mrr: number | null
  avgLatencyMs: number | null
  failed: number
} {
  const usable = items.filter((item) => !item.error)
  const hits = usable.filter((item) => item.hit).length
  const reciprocal = usable.map((item) => (item.rank && item.rank > 0 ? 1 / item.rank : 0))

  return {
    total: items.length,
    hits,
    hitRate: usable.length === 0 ? null : hits / usable.length,
    mrr: usable.length === 0 ? null : reciprocal.reduce((sum, value) => sum + value, 0) / usable.length,
    avgLatencyMs:
      usable.length === 0
        ? null
        : usable.reduce((sum, item) => sum + item.latencyMs, 0) / usable.length,
    failed: items.length - usable.length
  }
}
