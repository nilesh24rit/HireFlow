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

/** HireFlow Frontend API Client documentation is provided above. */

/**
 --- Candidate Service ---
 */

export const candidates = {
  /** GET /api/candidates/by-user?userId */
  getByUser: (userId: string) => fetchApi<Candidate>(`${BASE_URL}/candidates/by-user?userId=${userId}`),

  /** GET /api/candidates?search&status&employmentType */
  search: (search = '', status?: string, employmentType?: string) =>
    fetchApi<Candidate[]>(`${BASE_URL}/candidates`, {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' },
    }),

  /** PUT /api/candidates/update */
  update: (id: string, body: CandidatePayload) => fetchApi<void>(`${BASE_URL}/candidates/update`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }),

  /** POST /api/candidates/process-enrichment (multipart) */
  processEnrichment: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return fetchApi<ProcessingResult>(`${BASE_URL}/candidates/process-enrichment`, {
      method: 'POST',
      body: formData,
    })
  },

  /** POST /api/candidates/confirm-enrichment */
  confirmEnrichment: (processingId: string, body: CandidatePayload) =>
    fetchApi<void>(`${BASE_URL}/candidates/confirm-enrichment?processingId=${processingId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }),
}

/** Candidate data types (kept in sync with backend DTOs) */
export type Candidate = {
  id: string
  userId: string
  headline?: string
  summary?: string
  location?: string
  yearsOfExperience?: number
  currentCompany?: string
  currentJobTitle?: string
  resumeUrl?: string
  linkedinUrl?: string
  githubUrl?: string
  skills?: string[]
  role?: Role
  createdAt?: string
  updatedAt?: string
}

export type CandidatePayload = {
  headline?: string
  summary?: string
  location?: string
  yearsOfExperience?: number
  currentCompany?: string
  currentJobTitle?: string
  resumeUrl?: string
  linkedinUrl?: string
  githubUrl?: string
  skills?: string[]
}

/** Processing result from resume enrichment */
export type ProcessingResult = {
  processingId: string
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED'
  proposedData?: CandidatePayload
  generatedAt?: string
}

/** Role enum */
export type Role = 'CANDIDATE' | 'RECRUITER' | 'ADMIN'

/**
 --- Job Service ---
 */

export const jobs = {
  /** GET /api/jobs/{id} */
  get: (jobId: string) => fetchApi<Job>(`${BASE_URL}/jobs/${jobId}`),

  /** GET /api/jobs/by-recruiter?recruiterId */
  byRecruiter: (recruiterId: string) => fetchApi<Job[]>(`${BASE_URL}/jobs/by-recruiter?recruiterId=${recruiterId}`),

  /** POST /api/jobs/create */
  create: (body: CreateJobPayload) =>
    fetchApi<void>(`${BASE_URL}/jobs/create`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }),

  /** PUT /api/jobs/{id} */
  update: (jobId: string, body: CreateJobPayload) =>
    fetchApi<void>(`${BASE_URL}/jobs/${jobId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }),

  /** GET /api/jobs?search&status&employmentType */
  search: (search = '', status?: string, employmentType?: string) =>
    fetchApi<Job[]>(`${BASE_URL}/jobs`, {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' },
    }),
}

export type Job = {
  id: string
  recruiterId: string
  title: string
  description: string
  location?: string
  employmentType?: 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'INTERNSHIP'
  experienceMin?: number
  experienceMax?: number
  salaryMin?: number
  salaryMax?: number
  status?: 'DRAFT' | 'OPEN' | 'CLOSED'
  skills?: string[]
  createdAt?: string
  updatedAt?: string
}

export type CreateJobPayload = {
  recruiterId: string
  title: string
  description: string
  location?: string
  employmentType?: 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'INTERNSHIP'
  experienceMin?: number
  experienceMax?: number
  salaryMin?: number
  salaryMax?: number
  status?: 'DRAFT' | 'OPEN' | 'CLOSED'
  skills?: string[]
}

/**
 --- Application Service ---
 */

export const applications = {
  /** POST /api/applications/create */
  create: (body: ApplicationCreatePayload) =>
    fetchApi<ApplicationResponse>(`${BASE_URL}/applications/create`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }),

  /** GET /api/applications/by-candidate?candidateId */
  byCandidate: (candidateId: string) => fetchApi<Application[]>(`${BASE_URL}/applications/by-candidate?candidateId=${candidateId}`),

  /** GET /api/applications/by-job?jobId */
  byJob: (jobId: string) => fetchApi<Application[]>(`${BASE_URL}/applications/by-job?jobId=${jobId}`),

  /** PUT /api/applications/{id}/status */
  updateStatus: (id: string, status: ApplicationStatus) =>
    fetchApi<void>(`${BASE_URL}/applications/${id}/status`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status }),
    }),
}

export type Application = {
  id: string
  candidateId: string
  jobId: string
  status: ApplicationStatus
  appliedAt: string
}

export type ApplicationStatus =
  | 'APPLIED'
  | 'UNDER_REVIEW'
  | 'SHORTLISTED'
  | 'INTERVIEW'
  | 'REJECTED'
  | 'HIRED'

export type ApplicationCreatePayload = {
  candidateId: string
  jobId: string
  coverLetter?: string
}

export type ApplicationResponse = {
  id: string
  candidateId: string
  jobId: string
  status: ApplicationStatus
  appliedAt: string
}

/** AI / Matching Service */
export const ai = {
  /** POST /api/ai/candidate-match?jobId */
  candidateMatch: (jobId: string) => fetchApi<MatchResult>(`${BASE_URL}/ai/candidate-match?jobId=${jobId}`),

  /** POST /api/ai/applicant-match?jobId&candidateId */
  applicantMatch: (jobId: string, candidateId: string) =>
    fetchApi<MatchResult>(`${BASE_URL}/ai/applicant-match?jobId=${jobId}&candidateId=${candidateId}`),
}

export type MatchResult = {
  matchScore: number
  matchedRequiredSkills: string[]
  missingRequiredSkills: string[]
  matchedPreferredSkills: string[]
  explanation: string
  explanationStatus: 'AVAILABLE' | 'UNAVAILABLE'
  generatedAt: string
}

/**
 --- Notification Service ---
 */
export const notifications = {
  /** GET /api/notifications/ */
  list: () => fetchApi<Notification[]>(`${BASE_URL}/notifications/`),
}

export type Notification = {
  id: string
  subject: string
  message: string
  createdAt: string
  read: boolean
}

/**
 --- User / Auth Service ---
 */
export const users = {
  /** PUT /api/users/{id} */
  update: (id: string, values: { firstName: string; lastName: string }) =>
    fetchApi<void>(`${BASE_URL}/users/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(values),
    }),

  /** GET /api/users/{id} */
  get: (userId: string) => fetchApi<UserResponse>(`${BASE_URL}/users/${userId}`),

  /** PUT /api/users/{id}/role */
  updateRole: (id: string, role: Role) =>
    fetchApi<void>(`${BASE_URL}/users/${id}/role`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ role }),
    }),

  /** DELETE /api/users/{id} */
  remove: (userId: string) => fetchApi<void>(`${BASE_URL}/users/${userId}`, { method: 'DELETE' }),
}

export type UserResponse = {
  id: string
  firstName: string
  lastName: string
  email: string
  role: Role
  enabled: boolean
  createdAt: string
}

/** Login / Auth */
export const auth = {
  /** POST /api/auth/login */
  signIn: (email: string, password: string) =>
    fetchApi<LoginResponse>(`${BASE_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    }),

  /** POST /api/auth/oauth2/token */
  oauth2Token: (handoffCode: string) =>
    fetchApi<LoginResponse>(`${BASE_URL}/auth/oauth2/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ handoffCode }),
    }),
}

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

/** API base path used by the gateway to route to backend services */
export const BASE_URL = '/api'

/** Aggregated API client for frontend imports */
export const api = {
  candidates,
  jobs,
  applications,
  ai,
  notifications,
  users,
  analytics,
}

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

export type LoginResponse = {
  token: string
  role: Role
}

/**
 --- Analytics Service ---
 */
export const analytics = {
  /** GET /api/analytics/overview?admin=true&range=30 */
  overview: (admin = false, range = 30) =>
    fetchApi<AnalyticsOverview>(`${BASE_URL}/analytics/overview?admin=${admin}&range=${range}`),
}

export type AnalyticsOverview = {
  totalApplications: number
  submissionTrend: { date: string; count: number }[]
  dataFreshAt: string
  applicationsByStatus: Record<ApplicationStatus, number>
}

/** API error type exported for use in components */
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

/** API base path used by the gateway to route to backend services */
export const BASE_URL = '/api'

/** Aggregated API client for frontend imports */
export const api = {
  candidates,
  jobs,
  applications,
  ai,
  notifications,
  users,
  analytics,
}

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