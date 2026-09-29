import Link from 'next/link';

export function MobileMenu() {
  return (
    <details className="mobile-menu">
      <summary aria-label="Mở menu">Menu</summary>
      <nav aria-label="Điều hướng trên điện thoại">
        <Link href="/search">Sản phẩm</Link>
        <Link href="/orders">Đơn hàng</Link>
        <Link href="/top-up">Nạp tiền</Link>
        <Link href="/login">Đăng nhập</Link>
      </nav>
    </details>
  );
}
