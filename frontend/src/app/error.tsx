"use client";

import { useEffect } from "react";
import Link from "next/link";

export default function ErrorState({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error("Storefront route failed", error.digest ?? "no-digest");
  }, [error]);

  return (
    <section className="state-page" role="alert" aria-labelledby="error-title">
      <div className="state-code" aria-hidden="true">!</div>
      <p className="eyebrow">Không thể tải trang</p>
      <h1 id="error-title">Đã có lỗi xảy ra, nhưng dữ liệu của bạn vẫn an toàn.</h1>
      <p>Hãy thử tải lại. Nếu lỗi còn tiếp diễn, dùng mã yêu cầu trong phản hồi hỗ trợ để chúng tôi kiểm tra.</p>
      <div className="state-actions">
        <button className="button button-primary" type="button" onClick={reset}>Thử lại</button>
        <Link className="button button-secondary" href="/">Về trang chủ</Link>
      </div>
    </section>
  );
}
