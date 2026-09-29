'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { FormEvent, useState } from 'react';

import { ApiError } from '@/lib/api/client';
import { login, signup } from '@/lib/api/auth';

type AuthMode = 'login' | 'signup';
type FieldName = 'firstName' | 'lastName' | 'username' | 'email' | 'password';
type FieldErrors = Partial<Record<FieldName, string>>;

export function AuthForm({ mode }: { mode: AuthMode }) {
  const router = useRouter();
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [requestError, setRequestError] = useState<string>();
  const [pending, setPending] = useState(false);
  const isLogin = mode === 'login';

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = readValues(new FormData(event.currentTarget));
    const errors = validate(values, mode);
    setFieldErrors(errors);
    setRequestError(undefined);
    const firstInvalidField = Object.keys(errors)[0] as FieldName | undefined;
    if (firstInvalidField) {
      const control = event.currentTarget.elements.namedItem(firstInvalidField);
      if (control instanceof HTMLElement) control.focus();
      return;
    }

    setPending(true);
    try {
      if (isLogin) {
        await login({ username: values.username.trim(), password: values.password });
        router.push('/');
        router.refresh();
      } else {
        await signup({
          firstName: values.firstName.trim(),
          lastName: values.lastName.trim(),
          username: values.username.trim(),
          email: values.email.trim(),
          password: values.password,
        });
        router.push('/login?registered=1');
      }
    } catch (error) {
      if (!isLogin && error instanceof ApiError && error.fieldErrors) {
        setFieldErrors(mapServerErrors(error.fieldErrors));
      }
      setRequestError(
        isLogin
          ? 'Không thể đăng nhập. Vui lòng kiểm tra thông tin và thử lại.'
          : 'Không thể tạo tài khoản lúc này. Vui lòng kiểm tra thông tin và thử lại.'
      );
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit} noValidate>
      {!isLogin && (
        <div className="auth-name-grid">
          <Field label="Họ" name="lastName" autoComplete="family-name" error={fieldErrors.lastName} />
          <Field label="Tên" name="firstName" autoComplete="given-name" error={fieldErrors.firstName} />
        </div>
      )}

      <Field label="Tên đăng nhập" name="username" autoComplete="username" error={fieldErrors.username} />
      {!isLogin && <Field label="Email" name="email" type="email" autoComplete="email" error={fieldErrors.email} />}
      <Field
        label="Mật khẩu"
        name="password"
        type="password"
        autoComplete={isLogin ? 'current-password' : 'new-password'}
        error={fieldErrors.password}
        hint={isLogin ? undefined : 'Từ 6 đến 100 ký tự.'}
      />

      {requestError && (
        <p className="auth-request-error" role="alert">
          {requestError}
        </p>
      )}

      <button className="button button-primary auth-submit" type="submit" disabled={pending}>
        {pending ? (isLogin ? 'Đang đăng nhập…' : 'Đang tạo tài khoản…') : isLogin ? 'Đăng nhập' : 'Tạo tài khoản'}
      </button>

      <p className="auth-switch">
        {isLogin ? 'Chưa có tài khoản?' : 'Đã có tài khoản?'}{' '}
        <Link href={isLogin ? '/signup' : '/login'}>{isLogin ? 'Đăng ký' : 'Đăng nhập'}</Link>
      </p>
    </form>
  );
}

type FieldProps = {
  label: string;
  name: FieldName;
  type?: 'text' | 'email' | 'password';
  autoComplete: string;
  error?: string;
  hint?: string;
};

function Field({ label, name, type = 'text', autoComplete, error, hint }: FieldProps) {
  const messageId = `${name}-message`;
  return (
    <div className="auth-field">
      <label htmlFor={name}>{label}</label>
      <input
        id={name}
        name={name}
        type={type}
        autoComplete={autoComplete}
        aria-invalid={error ? true : undefined}
        aria-describedby={error || hint ? messageId : undefined}
      />
      {(error || hint) && (
        <p id={messageId} className={error ? 'auth-field-error' : 'auth-field-hint'} role={error ? 'alert' : undefined}>
          {error || hint}
        </p>
      )}
    </div>
  );
}

function readValues(form: FormData): Record<FieldName, string> {
  return {
    firstName: String(form.get('firstName') ?? ''),
    lastName: String(form.get('lastName') ?? ''),
    username: String(form.get('username') ?? ''),
    email: String(form.get('email') ?? ''),
    password: String(form.get('password') ?? ''),
  };
}

function validate(values: Record<FieldName, string>, mode: AuthMode): FieldErrors {
  const errors: FieldErrors = {};
  if (mode === 'signup' && values.lastName.trim().length < 3) errors.lastName = 'Họ cần ít nhất 3 ký tự.';
  if (mode === 'signup' && values.firstName.trim().length < 3) errors.firstName = 'Tên cần ít nhất 3 ký tự.';
  if (values.username.trim().length < 3) errors.username = 'Tên đăng nhập cần ít nhất 3 ký tự.';
  if (mode === 'signup' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email.trim())) {
    errors.email = 'Email chưa đúng định dạng.';
  }
  if (values.password.length < 6) errors.password = 'Mật khẩu cần ít nhất 6 ký tự.';
  return errors;
}

function mapServerErrors(errors: Record<string, string[]>): FieldErrors {
  const fields: FieldErrors = {};
  for (const name of Object.keys(errors) as FieldName[]) {
    const message = errors[name]?.[0];
    if (message && ['firstName', 'lastName', 'username', 'email', 'password'].includes(name)) fields[name] = message;
  }
  return fields;
}
