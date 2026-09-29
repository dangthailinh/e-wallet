import type { Metadata } from 'next';

import { TopUpFlow } from '@/components/payment/top-up-flow';

export const metadata: Metadata = { title: 'Nạp tiền', robots: { index: false, follow: false } };

export default function TopUpPage() {
  return (
    <main className="top-up-page" aria-labelledby="top-up-title">
      <section className="top-up-intro">
        <p className="eyebrow">Ví cửa hàng</p>
        <h1 id="top-up-title">Nạp tiền an toàn, xác nhận tự động.</h1>
        <p>
          Thanh toán qua MoMo. Hệ thống chỉ cộng số dư sau khi nhận và xác minh thông báo thanh toán từ MoMo.
        </p>
        <dl className="top-up-assurances">
          <div>
            <dt>Không cộng tiền giả</dt>
            <dd>Backend xác minh webhook trước khi cập nhật ví.</dd>
          </div>
          <div>
            <dt>Mỗi yêu cầu một mã riêng</dt>
            <dd>Dễ đối soát và tránh cộng nhầm giao dịch.</dd>
          </div>
        </dl>
      </section>
      <div className="top-up-card">
        <div className="top-up-card-heading">
          <p className="eyebrow">Tạo yêu cầu</p>
          <h2>Nạp tiền vào ví</h2>
        </div>
        <TopUpFlow />
      </div>
    </main>
  );
}
