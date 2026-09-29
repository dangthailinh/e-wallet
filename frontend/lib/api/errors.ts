export type ProblemDetails = {
  code?: string;
  detail?: string;
  errors?: Record<string, string[]>;
};

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly fieldErrors?: Record<string, string[]>;

  constructor(status: number, problem: ProblemDetails = {}) {
    super(problem.detail || 'Request failed');
    this.name = 'ApiError';
    this.status = status;
    this.code = problem.code;
    this.fieldErrors = problem.errors;
  }
}

export async function readProblem(response: Response): Promise<ProblemDetails> {
  if (!response.headers.get('content-type')?.includes('json')) return {};
  try {
    const value: unknown = await response.json();
    if (!value || typeof value !== 'object') return {};
    const problem = value as Record<string, unknown>;
    return {
      code: typeof problem.code === 'string' ? problem.code : undefined,
      detail: typeof problem.detail === 'string' ? problem.detail : undefined,
      errors: isFieldErrors(problem.errors) ? problem.errors : undefined,
    };
  } catch {
    return {};
  }
}

function isFieldErrors(value: unknown): value is Record<string, string[]> {
  return (
    !!value &&
    typeof value === 'object' &&
    Object.values(value).every(
      (messages) => Array.isArray(messages) && messages.every((message) => typeof message === 'string')
    )
  );
}
