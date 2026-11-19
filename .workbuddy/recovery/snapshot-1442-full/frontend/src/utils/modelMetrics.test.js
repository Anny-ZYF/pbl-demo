import { describe, expect, it } from 'vitest'
import {
  meetsModelAdmissionGate,
  parseJsonObject,
  readModelMetrics
} from './modelMetrics'

describe('model metric admission', () => {
  it('reads top-level, nested test, and preferred holdout metrics', () => {
    expect(readModelMetrics('{"rmse":0.9,"mae":0.7,"r2":0.995}')).toEqual({
      rmse: 0.9,
      mae: 0.7,
      r2: 0.995
    })
    expect(readModelMetrics({ test: { rmse: 0.8, mae: 0.6, r2: 0.999 } })).toEqual({
      rmse: 0.8,
      mae: 0.6,
      r2: 0.999
    })
    expect(readModelMetrics({
      holdout: { rmse: 0.4, mae: 0.3, r2: 0.9997 },
      test: { rmse: 1.4, mae: 0.9, r2: 0.98 }
    })).toEqual({ rmse: 0.4, mae: 0.3, r2: 0.9997 })
  })

  it('applies the same gate as the backend', () => {
    expect(meetsModelAdmissionGate({ test: { rmse: 1, mae: 0.8, r2: 0.99 } })).toBe(true)
    expect(meetsModelAdmissionGate({ test: { rmse: 1.01, mae: 0.8, r2: 0.99 } })).toBe(false)
    expect(meetsModelAdmissionGate({
      status: 'REJECTED',
      holdout: { rmse: 0.5, mae: 0.4, r2: 0.999 }
    })).toBe(false)
  })

  it('rejects malformed or incomplete JSON', () => {
    expect(parseJsonObject('{bad json')).toBeNull()
    expect(readModelMetrics('{"rmse":0.5}')).toBeNull()
    expect(meetsModelAdmissionGate(null)).toBe(false)
  })
})
