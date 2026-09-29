import type { Metadata } from 'next';

import { AuthForm } from '@/components/auth/auth-form';

export const metadata: Metadata = { title: 'Tạo tài khoản' };

export default function SignupPage() {
  return (
    <div className="auth-page">
      <section className="auth-context" aria-labelledby="signup-title">
        <p className="eyebrow">Bắt đầu trong một phút</p>
        <h1 id="signup-title">Một tài khoản cho ví và đơn hàng.</h1>
        <p>Tạo tài khoản cá nhân để nạp tiền thật, mua sản phẩm và xem lại mã đã nhận một cách an toàn.</p>
        <ul className="auth-trust-list">
          <li>Không thu phí mở tài khoản</li>
          <li>Mọi biến động ví có lịch sử đối soát</li>
          <li>Không gửi mã sản phẩm qua email</li>
        </ul>
      </section>
      <section className="auth-card" aria-label="Biểu mẫu tạo tài khoản">
        <div className="auth-card-heading">
          <p className="eyebrow">Nexa ID</p>
          <h2>Tạo tài khoản</h2>
          <p>Thông tin này dùng để xác minh quyền sở hữu đơn hàng.</p>
        </div>
        <AuthForm mode="signup" />
      </section>
    </div>
  );
}
