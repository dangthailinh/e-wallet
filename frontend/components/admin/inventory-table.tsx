'use client';

import type { AdminInventoryItem, InventoryStatus } from '@/lib/api/admin-inventory';

type InventoryTableProps = {
  items: AdminInventoryItem[];
  pendingItemId?: string;
  onStatusChange: (item: AdminInventoryItem, status: 'AVAILABLE' | 'DISABLED') => void;
};

const timestamp = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' });

export function InventoryTable({ items, pendingItemId, onStatusChange }: InventoryTableProps) {
  if (items.length === 0) {
    return <div className="catalog-state" role="status"><strong>Kho chưa có item</strong><p>Nhập dữ liệu ở biểu mẫu phía trên.</p></div>;
  }

  return (
    <div className="inventory-table-wrap">
      <table className="inventory-table">
        <caption className="sr-only">Metadata tồn kho đã mã hóa</caption>
        <thead><tr><th>Checksum</th><th>Trạng thái</th><th>Khóa</th><th>Cập nhật</th><th>Thao tác</th></tr></thead>
        <tbody>
          {items.map((item) => {
            const canToggle = item.status === 'AVAILABLE' || item.status === 'DISABLED';
            const target = item.status === 'AVAILABLE' ? 'DISABLED' : 'AVAILABLE';
            return (
              <tr key={item.id}>
                <td><code>••••{item.checksumSuffix}</code></td>
                <td><span className={`inventory-status status-${item.status.toLowerCase()}`}>{statusLabel(item.status)}</span></td>
                <td>Khóa v{item.keyVersion}</td>
                <td>{timestamp.format(new Date(item.updatedAt))}</td>
                <td>
                  {canToggle ? (
                    <button
                      className="button button-secondary inventory-action"
                      type="button"
                      disabled={pendingItemId === item.id}
                      onClick={() => onStatusChange(item, target)}
                    >
                      {target === 'DISABLED' ? 'Vô hiệu hóa' : 'Kích hoạt'}
                    </button>
                  ) : <span className="admin-muted">Đã khóa</span>}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function statusLabel(status: InventoryStatus): string {
  return { AVAILABLE: 'Khả dụng', RESERVED: 'Đã giữ', SOLD: 'Đã bán', DISABLED: 'Vô hiệu' }[status];
}
