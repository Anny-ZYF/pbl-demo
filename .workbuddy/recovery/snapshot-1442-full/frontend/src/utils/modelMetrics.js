export const MODEL_ADMISSION_GATE = Object.freeze({
  rmse: 1,
  mae: 0.8,
  r2: 0.99
})

export function parseJsonObject(value) {
  if (value && typeof value === 'object' && !Array.isArray(value)) return value
  if (typeof value !== 'string' || !value.trim()) return null

  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null
  } catch {
    return null
  }
}

export function readModelMetrics(value) {
  const parsed = parseJsonObject(value)
  if (!parsed) return null

  const source = parsed.holdout && typeof parsed.holdout === 'object'
    ? parsed.holdout
    : parsed.test && typeof parsed.test === 'object' ? parsed.test : parsed
  const metrics = {
    rmse: Number(source.rmse),
    mae: Number(source.mae),
    r2: Number(source.r2)
  }

  return Object.values(metrics).every(Number.isFinite) ? metrics : null
}

export function meetsModelAdmissionGate(value) {
  const parsed = parseJsonObject(value)
  if (!parsed) return false
  if (Object.prototype.hasOwnProperty.call(parsed, 'status') && parsed.status !== 'ELIGIBLE') return false
  if (parsed.eligibility && parsed.eligibility.passed === false) return false

  const metrics = readModelMetrics(value)
  return Boolean(metrics
    && metrics.rmse >= 0
    && metrics.mae >= 0
    && metrics.r2 <= 1
    && metrics.rmse <= MODEL_ADMISSION_GATE.rmse
    && metrics.mae <= MODEL_ADMISSION_GATE.mae
    && metrics.r2 >= MODEL_ADMISSION_GATE.r2)
}
