import axios from 'axios'
import type { ApiErrorBody, UiError } from '@/types/system'

export const http = axios.create({
  baseURL: '/api',
  timeout: 5000,
  // Session login will reuse this client. CSRF is not relevant until mutating
  // endpoints exist; the dev server proxies /api so no CORS credentials mode is
  // needed for stage 0.
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  withXSRFToken: true,
  headers: {
    Accept: 'application/json',
  },
})

export class ApiContractError extends Error {
  constructor(message = '后端响应不符合阶段 0 契约') {
    super(message)
    this.name = 'ApiContractError'
  }
}

export function toUiError(cause: unknown): UiError {
  if (cause instanceof ApiContractError) {
    return { code: 'INVALID_RESPONSE', message: cause.message }
  }

  if (axios.isAxiosError(cause)) {
    const response = cause.response
    const body = response?.data
    const headerTraceId = readTraceIdHeader(response?.headers)
    const traceId = isApiErrorBody(body) ? body.traceId ?? headerTraceId : headerTraceId

    if (!response) {
      const timedOut = cause.code === 'ECONNABORTED' || cause.code === 'ETIMEDOUT'
      return {
        code: timedOut ? 'BACKEND_TIMEOUT' : 'BACKEND_UNREACHABLE',
        message: timedOut
          ? '后端响应超时，请确认 Spring Boot 已启动。'
          : '无法连接后端，请确认 Spring Boot 已在 8080 端口启动。',
      }
    }

    if (isApiErrorBody(body)) {
      return {
        code: body.code,
        message: body.message,
        traceId,
      }
    }

    return {
      code: `HTTP_${response.status}`,
      message: `后端返回 HTTP ${response.status}，但响应体不符合错误契约。`,
      traceId,
    }
  }

  return {
    code: 'FRONTEND_UNEXPECTED_ERROR',
    message: cause instanceof Error ? cause.message : '前端发生未知错误',
  }
}

function readTraceIdHeader(headers: unknown): string | undefined {
  if (!headers || typeof headers !== 'object') {
    return undefined
  }
  const value = (headers as Record<string, unknown>)['x-trace-id']
  return typeof value === 'string' && value.length > 0 ? value : undefined
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (!value || typeof value !== 'object') {
    return false
  }
  const candidate = value as Partial<ApiErrorBody>
  return typeof candidate.code === 'string' && typeof candidate.message === 'string'
}
