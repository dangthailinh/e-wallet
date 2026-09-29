import type { CatalogProductDetail } from '@/lib/contracts/catalog';

export function ProductDescription({ product }: { product: CatalogProductDetail }) {
  return (
    <div className="product-description">
      <section aria-labelledby="description-title">
        <p className="eyebrow">Thông tin sản phẩm</p>
        <h2 id="description-title">Mô tả</h2>
        <p>{product.description}</p>
      </section>

      <section aria-labelledby="delivery-title">
        <p className="eyebrow">Bàn giao an toàn</p>
        <h2 id="delivery-title">Nhận hàng trong tài khoản</h2>
        <ul>
          <li>Mã hoặc thông tin tài khoản được mở trong lịch sử đơn hàng.</li>
          <li>Dữ liệu bí mật được mã hóa trong kho và không gửi qua email.</li>
          <li>Mỗi sản phẩm tồn kho chỉ được giao cho một đơn hàng thành công.</li>
        </ul>
      </section>

      <section aria-labelledby="warranty-title">
        <p className="eyebrow">Sau mua hàng</p>
        <h2 id="warranty-title">Bảo hành và hỗ trợ</h2>
        <p>
          Thời hạn và điều kiện hỗ trợ được ghi rõ trong đơn hàng. Nếu dữ liệu bàn giao không hoạt động,
          khách hàng có thể gửi yêu cầu kèm mã đơn để được kiểm tra.
        </p>
      </section>
    </div>
  );
}
