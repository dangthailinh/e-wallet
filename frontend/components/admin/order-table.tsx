import type { AdminOrder, AdminOrderStatus } from '@/lib/api/admin-operations';

const money = new Intl.NumberFormat('vi-VN');
const timestamp = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' });

const statusLabels: Record<AdminOrderStatus, string> = {
  COMPLETED: 'Hoàn tất',
  FAILED: 'Thất bại',
  REFUNDED: 'Đã hoàn tiền',
};

export function OrderTable({ orders }: { orders: AdminOrder[] }) {
  if (orders.length === 0) {
    return <p className="admin-empty" role="status">Không có đơn hàng phù hợp bộ lọc.</p>;
  }

  return (
    <div className="admin-table-scroll">
      <table className="admin-table operations-table">
        <caption className="sr-only">Danh sách đơn hàng quản trị</caption>
        <thead>
          <tr>
            <th scope="col">Đơn hàng</th>
            <th scope="col">Khách hàng</th>
            <th scope="col">Tổng tiền</th>
            <th scope="col">Trạng thái</th>
            <th scope="col">Tạo lúc</th>
            <th scope="col">Cập nhật</th>
          </tr>
        </thead>
        <tbody>
          {orders.map((order) => (
            <tr key={order.orderId}>
              <td>
                <strong>{order.orderId.slice(0, 8).toUpperCase()}</strong>
                <small>Mã UUID nội bộ</small>
              </td>
              <td>Người dùng #{order.userId}</td>
              <td>{money.format(order.totalVnd)} ₫</td>
              <td><span className={`admin-status operations-status-${order.status.toLowerCase()}`}>{statusLabels[order.status]}</span></td>
              <td><time dateTime={order.createdAt}>{timestamp.format(new Date(order.createdAt))}</time></td>
              <td><time dateTime={order.updatedAt}>{timestamp.format(new Date(order.updatedAt))}</time></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
