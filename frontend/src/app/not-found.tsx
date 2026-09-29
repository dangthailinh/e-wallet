import Link from "next/link";

export default function NotFound() {
  return (
    <section className="state-page" aria-labelledby="not-found-title">
      <div className="state-code" aria-hidden="true">404</div>
      <p className="eyebrow">Không tìm thấy trang</p>
      <h1 id="not-found-title">Liên kết này không còn tồn tại.</h1>
      <p>Trang có thể đã được chuyển hoặc địa chỉ chưa chính xác. Bạn có thể quay lại danh mục để tiếp tục mua sắm.</p>
      <div className="state-actions">
        <Link className="button button-primary" href="/">Về trang chủ</Link>
        <Link className="button button-secondary" href="/#products">Xem sản phẩm</Link>
      </div>
    </section>
  );
}
