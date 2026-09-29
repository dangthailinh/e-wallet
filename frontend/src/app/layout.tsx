import type { Metadata } from 'next';
import Link from 'next/link';
import { GeistSans } from 'geist/font/sans';

import { BrandMark, Navbar } from '@/components/layout/navbar';

import './globals.css';

export const metadata: Metadata = {
  title: {
    default: 'Nexa Store — Sản phẩm số giao ngay',
    template: '%s — Nexa Store',
  },
  description: 'Mua sản phẩm số bằng số dư ví, nhận hàng ngay trong lịch sử đơn hàng.',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="vi" className={GeistSans.variable} data-scroll-behavior="smooth">
      <body>
        <a className="skip-link" href="#main-content">
          Đi tới nội dung chính
        </a>
        <Navbar />

        <main id="main-content">{children}</main>

        <footer className="site-footer">
          <div className="footer-shell">
            <div>
              <Link href="/" className="brand footer-brand">
                <BrandMark />
                <span>Nexa Store</span>
              </Link>
              <p>Sản phẩm số chính hãng, thanh toán bằng số dư ví và giao ngay sau khi mua.</p>
            </div>
            <nav aria-label="Liên kết cuối trang">
              <Link href="/help">Hỗ trợ</Link>
              <Link href="/terms">Điều khoản</Link>
              <Link href="/privacy">Bảo mật</Link>
            </nav>
          </div>
        </footer>
      </body>
    </html>
  );
}
