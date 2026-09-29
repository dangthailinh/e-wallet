import type { AdminDeposit, AdminDepositStatus } from '@/lib/api/admin-operations';

const money = new Intl.NumberFormat('vi-VN');
const timestamp = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' });

const statusLabels: Record<AdminDepositStatus, string> = {
  PENDING: 'Đang chờ',
  PAID: 'Đã nhận tiền',
  FAILED: 'Thất bại',
  EXPIRED: 'Hết hạn',
  REVIEW: 'Đang rà soát',
};

export function DepositTable({ deposits }: { deposits: AdminDeposit[] }) {
  if (deposits.length === 0) {
    return <p className="admin-empty" role="status">Không có giao dịch nạp tiền phù hợp bộ lọc.</p>;
  }

  return (
    <div className="admin-table-scroll">
      <table className="admin-table operations-table">
        <caption className="sr-only">Danh sách giao dịch nạp tiền quản trị</caption>
        <thead>
          <tr>
            <th scope="col">Giao dịch</th>
            <th scope="col">Khách hàng</th>
            <th scope="col">Kênh</th>
            <th scope="col">Số tiền</th>
            <th scope="col">Trạng thái</th>
            <th scope="col">Tạo lúc</th>
            <th scope="col">Đã thanh toán</th>
          </tr>
        </thead>
        <tbody>
          {deposits.map((deposit) => (
            <tr key={deposit.id}>
              <td>
                <strong>{deposit.id.slice(0, 8).toUpperCase()}</strong>
                <small>{deposit.providerCode} · {deposit.currency}</small>
              </td>
              <td>Người dùng #{deposit.userId}</td>
              <td>{deposit.channel}</td>
              <td>{money.format(deposit.amountVnd)} ₫</td>
              <td>
                <span className={`admin-status operations-status-${deposit.status.toLowerCase()}`}>
                  {statusLabels[deposit.status]}
                </span>
                {deposit.needsReconciliationReview && <small className="operations-review-note">Cần đối soát</small>}
              </td>
              <td><time dateTime={deposit.createdAt}>{timestamp.format(new Date(deposit.createdAt))}</time></td>
              <td>{deposit.paidAt ? <time dateTime={deposit.paidAt}>{timestamp.format(new Date(deposit.paidAt))}</time> : 'Chưa xác nhận'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
