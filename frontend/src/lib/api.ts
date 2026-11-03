/**
 * HireFlow Frontend API Client
 *
 * This module provides typed access to the backend services through the API gateway.
 * All endpoints are routed through /api/... paths which the gateway forwards to the
 * appropriate micro-service (auth on 8081, candidate on 8082, job on 8083, etc.).
 *
 * API base path: /api
 *   /auth/**       -> auth-service (localhost:8081)
 *   /candidates/** -> candidate-service (localhost:8082)
 *   /jobs/**       -> job-service (localhost:8083)
 *   /applications/** -> application-service (localhost:8084)
 *   /ai/**         -> ai-service (localhost:8085)
 *   /notifications/** -> notification-service (localhost:8086)
 *   /analytics/**  -> analytics-service (localhost:8087)
 */

type ApiErrorResponse = {
  status: string
  message: string
  error: string
}

/**
 * Generic fetch wrapper that handles error response parsing and consistent error shapes.
 */
async function fetchApi<T>(input: RequestInfo, init: RequestInit = {}): Promise<T> {
  const response = await fetch(input, init)
  if (!response.ok) {
    const errorData: ApiErrorResponse = await response.json().catch(() => ({
      status: String(response.status),
      message: response.statusText,
      error: 'API request failed',
    }))
    const err = new Error(errorData.message)
    ;(err as any).status = errorData.status
    ;(err as any).statusText = errorData.error
    throw err
  }
  return response.json() as Promise<T>
}

/** Base URL for all API calls (relative to the gateway) */
const BASE_URL = '/api'

/** API error type for use in components */
export type { ApiErrorResponse }

/** Root API types exported for convenience */
export type {
  Candidate,
  CandidatePayload,
  Job,
  CreateJobPayload,
  Application,
  ApplicationStatus,
  ApplicationCreatePayload,
  ApplicationResponse,
  MatchResult,
  Notification,
  UserResponse,
  Role,
  EmploymentType,
  LoginResponse,
}

/** Aggregated API client for frontend imports.
 * 
 * This object aggregates all API service modules (candidates, jobs, applications,
 * ai, notifications, users, analytics) providing a single import point for the
 * frontend. Each service module provides typed methods for backend operations
 * routed through the API gateway.
 */
export const api = {
  candidates,
  jobs,
  applications,
  ai,
  notifications,
  users,
  analytics,
}

/** API base path used by the gateway to route to backend services */
export const BASE_URL = '/api'