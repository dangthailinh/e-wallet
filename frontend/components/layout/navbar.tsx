import Link from 'next/link';

import { SessionNavigation } from '@/components/auth/session-navigation';
import { MobileMenu } from '@/components/layout/mobile-menu';

export function BrandMark() {
  return (
    <svg aria-hidden="true" viewBox="0 0 32 32" className="brand-mark">
      <path d="M7 23V9l9-5 9 5v14l-9 5-9-5Z" fill="currentColor" opacity=".18" />
      <path d="m11 20 5-9 5 9m-8.5-3h7" fill="none" stroke="currentColor" strokeWidth="2" />
    </svg>
  );
}

function SearchIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className="search-icon">
      <circle cx="9" cy="9" r="5.5" fill="none" stroke="currentColor" strokeWidth="1.6" />
      <path d="m13 13 4 4" fill="none" stroke="currentColor" strokeWidth="1.6" />
    </svg>
  );
}

export function Navbar() {
  return (
    <header className="site-header">
      <nav className="nav-shell" aria-label="Điều hướng chính">
        <Link href="/" className="brand" aria-label="Nexa Store — Trang chủ">
          <BrandMark />
          <span>Nexa Store</span>
        </Link>

        <form className="nav-search" action="/search" role="search">
          <SearchIcon />
          <label className="sr-only" htmlFor="site-search">
            Tìm sản phẩm số
          </label>
          <input id="site-search" name="q" type="search" placeholder="Tìm API, bản quyền, tài nguyên…" />
          <kbd aria-hidden="true">/</kbd>
        </form>

        <div className="nav-actions">
          <Link href="/orders" className="nav-link">
            Đơn hàng
          </Link>
          <Link href="/top-up" className="button button-secondary">
            Nạp tiền
          </Link>
          <SessionNavigation />
          <MobileMenu />
        </div>
      </nav>
    </header>
  );
}
