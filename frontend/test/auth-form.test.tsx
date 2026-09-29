import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthForm } from '@/components/auth/auth-form';
import { ApiError } from '@/lib/api/client';

const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  signup: vi.fn(),
  push: vi.fn(),
  refresh: vi.fn(),
}));

vi.mock('@/lib/api/auth', () => ({
  login: mocks.login,
  signup: mocks.signup,
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mocks.push, refresh: mocks.refresh }),
}));

describe('AuthForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('has visible labels and exposes a pending login state', async () => {
    let completeLogin: (() => void) | undefined;
    mocks.login.mockReturnValueOnce(new Promise<void>((resolve) => (completeLogin = resolve)));
    render(<AuthForm mode="login" />);

    fireEvent.change(screen.getByLabelText('Tên đăng nhập'), { target: { value: 'buyer' } });
    fireEvent.change(screen.getByLabelText('Mật khẩu'), { target: { value: 'secure-password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));

    expect(await screen.findByRole('button', { name: 'Đang đăng nhập…' })).toBeDisabled();
    expect(mocks.login).toHaveBeenCalledWith({ username: 'buyer', password: 'secure-password' });

    completeLogin?.();
    await waitFor(() => expect(mocks.push).toHaveBeenCalledWith('/'));
  });

  it('shows the same safe login failure regardless of backend detail', async () => {
    mocks.login.mockRejectedValueOnce(new Error('buyer exists but password hash did not match'));
    render(<AuthForm mode="login" />);

    fireEvent.change(screen.getByLabelText('Tên đăng nhập'), { target: { value: 'buyer' } });
    fireEvent.change(screen.getByLabelText('Mật khẩu'), { target: { value: 'wrong-password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Không thể đăng nhập. Vui lòng kiểm tra thông tin và thử lại.');
    expect(alert).not.toHaveTextContent('password hash');
  });

  it('validates signup fields next to their controls before sending', async () => {
    render(<AuthForm mode="signup" />);

    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));

    expect(await screen.findByText('Tên cần ít nhất 3 ký tự.')).toBeVisible();
    expect(screen.getByText('Họ cần ít nhất 3 ký tự.')).toBeVisible();
    expect(screen.getByText('Email chưa đúng định dạng.')).toBeVisible();
    expect(screen.getByLabelText('Họ')).toHaveFocus();
    expect(mocks.signup).not.toHaveBeenCalled();
  });

  it('maps backend validation errors to the matching signup field', async () => {
    mocks.signup.mockRejectedValueOnce(
      new ApiError(422, { errors: { username: ['Tên đăng nhập đã được sử dụng.'] } })
    );
    render(<AuthForm mode="signup" />);

    fireEvent.change(screen.getByLabelText('Họ'), { target: { value: 'Nguyen' } });
    fireEvent.change(screen.getByLabelText('Tên'), { target: { value: 'Anh' } });
    fireEvent.change(screen.getByLabelText('Tên đăng nhập'), { target: { value: 'buyer' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'buyer@example.com' } });
    fireEvent.change(screen.getByLabelText('Mật khẩu'), { target: { value: 'secure-password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));

    expect(await screen.findByText('Tên đăng nhập đã được sử dụng.')).toBeVisible();
  });
});
