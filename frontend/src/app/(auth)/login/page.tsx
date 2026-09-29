import type { Metadata } from 'next';

import { AuthForm } from '@/components/auth/auth-form';

export const metadata: Metadata = { title: 'Đăng nhập' };

export default function LoginPage() {
  return (
    <AuthPage
      eyebrow="Phiên mua hàng an toàn"
      title="Chào mừng anh quay lại."
      description="Đăng nhập để kiểm tra số dư, thanh toán và nhận sản phẩm trong lịch sử đơn hàng."
    >
      <AuthForm mode="login" />
    </AuthPage>
  );
}

function AuthPage({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow: string;
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <div className="auth-page">
      <section className="auth-context" aria-labelledby="auth-title">
        <p className="eyebrow">{eyebrow}</p>
        <h1 id="auth-title">{title}</h1>
        <p>{description}</p>
        <ul className="auth-trust-list">
          <li>Token không lưu trong trình duyệt</li>
          <li>Sản phẩm bí mật chỉ hiện trong tài khoản</li>
          <li>Nạp số dư và xác nhận tự động qua MoMo</li>
        </ul>
      </section>
      <section className="auth-card" aria-label="Biểu mẫu đăng nhập">
        <div className="auth-card-heading">
          <p className="eyebrow">Nexa ID</p>
          <h2>Đăng nhập</h2>
          <p>Dùng tài khoản đã đăng ký với Nexa Store.</p>
        </div>
        {children}
      </section>
    </div>
  );
}
