# Đặc tả: Digital Storefront dùng ví nội bộ

**Trạng thái:** Đã duyệt — phạm vi thanh toán chỉ dùng MoMo

**Nhánh:** `feature/digital-storefront`

**Nền tảng gốc:** `yildizmy/e-wallet`

**Ngày:** 2026-09-17

## 1. Mục tiêu

Chuyển dự án `e-wallet` thành website kinh doanh sản phẩm số công khai trên Internet:

- Khách xem catalog dạng danh sách gọn, đăng ký/đăng nhập và nạp VND qua MoMo.
- Backend chỉ cộng số dư sau khi nhận và xác minh webhook/IPN thật từ nhà cung cấp.
- Admin nhập sẵn từng tài khoản, mã hoặc API key vào kho.
- Khi mua, hệ thống khóa đúng một item tồn kho, trừ ví và tạo đơn trong cùng một giao dịch database.
- Khách xem sản phẩm số đã mua trong lịch sử đơn; không gửi dữ liệu bí mật qua email.
- Hệ thống đủ nền tảng bảo mật, kiểm thử, quan sát và vận hành để triển khai production.

## 2. Phạm vi

### Trong phạm vi

- Catalog, danh mục, giá, trạng thái bán và số lượng tồn.
- Tài khoản khách/admin, JWT, phân quyền và quản lý phiên an toàn.
- Ví VND và sổ cái bất biến.
- Tạo yêu cầu nạp tiền qua MoMo.
- Webhook/IPN có xác thực chữ ký, chống phát lại và xử lý idempotent.
- Đơn hàng, khóa kho, giao sản phẩm số, lịch sử mua hàng.
- Admin quản lý sản phẩm, kho bí mật, đơn, giao dịch nạp và audit log.
- Docker Compose cho local/staging/production, HTTPS ở reverse proxy và quy trình sao lưu PostgreSQL.

### Ngoài phạm vi

- Hàng vật lý, vận chuyển, COD.
- Marketplace nhiều người bán.
- Tiền mã hóa.
- Gửi tài khoản/API key qua email.
- Tự động lấy giao dịch bằng đọc SMS, email hoặc scraping Internet Banking.
- Cơ chế nạp tiền giả lập trong production.
- Tích hợp phương thức thanh toán khác ngoài MoMo ở phiên bản đầu, gồm ngân hàng/VietQR.

## 3. Giả định

- Một đơn vị kinh doanh vận hành cửa hàng; tiền tệ duy nhất là VND.
- Sản phẩm được bán hợp pháp và người vận hành có quyền phân phối tài khoản/mã/API key đó.
- Tài khoản merchant MoMo đã sẵn sàng.
- Production dùng cùng origin qua reverse proxy: `/` cho frontend, `/api` cho backend.
- Phiên bản đầu phục vụ web responsive; chưa có ứng dụng mobile native.
- Tiếng Việt là ngôn ngữ mặc định.

## 4. Nền tảng kỹ thuật

### Backend

- Java 21 LTS.
- Spring Boot 3.5.x thay cho 3.0.4 để giảm rủi ro migration lớn sang Boot 4 nhưng vẫn dùng nhánh ổn định hiện hành.
- Spring Security, Spring Data JPA, Bean Validation, Flyway, Actuator và OpenAPI.
- PostgreSQL 17.
- Testcontainers dùng PostgreSQL thật cho integration test.
- JWT access token ngắn hạn; opaque refresh token xoay vòng, chỉ lưu hash và thu hồi được.

### Frontend

- Next.js 16.3.3 Active LTS, React 19, TypeScript và Tailwind CSS 4; bỏ Create React App đã deprecated.
- App Router, React Server Components và Server/Client Component boundary theo tài liệu Next.js.
- Server Components đọc catalog trực tiếp từ Spring Boot REST API; Client Components chỉ dùng cho search tương tác, dialog mua, wallet drawer, polling payment và reveal/copy.
- TanStack Query chỉ dùng cho server state cần polling/optimistic update ở client; React Hook Form và Zod cho form tương tác.
- Vitest + React Testing Library; Playwright cho E2E và axe cho accessibility.
- UI fork có chọn lọc từ Vercel Commerce commit `3761e52` (MIT), theo [design system](../design-system/digital-storefront/MASTER.md); Shopify provider được thay bằng adapter gọi Spring Boot.

### Hạ tầng

- Docker Compose tách cấu hình local và production.
- Reverse proxy kết thúc TLS, ép HTTPS/HSTS và chuyển `/api` về backend.
- PostgreSQL không public port trên production.
- Khóa mã hóa, JWT key và merchant credentials chỉ lấy từ environment/secret manager.

## 5. Kiến trúc

```text
Browser
  └── HTTPS reverse proxy
      ├── React storefront/admin
      └── Spring Boot /api/v1
          ├── Auth + session/token rotation
          ├── Catalog
          ├── Wallet ledger
          ├── Checkout + inventory allocation
          ├── MoMo payment adapter
          ├── Webhook inbox
          └── Audit/observability
                └── PostgreSQL
```

Backend là modular monolith. Mỗi module có `controller`, `application/service`, `domain` và `repository`; giao tiếp giữa module qua interface rõ ràng. Chưa tách microservice để giữ giao dịch trừ ví–bán kho nguyên tử và giảm độ phức tạp vận hành.

Next.js là lớp storefront/BFF, không được truy cập PostgreSQL trực tiếp. Mọi dữ liệu và mutation nghiệp vụ vẫn đi qua Spring Boot REST API. Server Components gọi backend trực tiếp; browser mutation đi qua same-origin `/api` reverse proxy hoặc Route Handler mỏng khi cần chuyển tiếp cookie/header. Authorization cuối cùng luôn nằm ở Spring Boot.

## 6. Mô hình dữ liệu

Tên chính xác có thể được tinh chỉnh trong migration, nhưng quan hệ và ràng buộc dưới đây là bắt buộc.

### Identity

- `users`: UUID public id, email/username chuẩn hóa, password hash, trạng thái, timestamps.
- `roles`, `user_roles`: `CUSTOMER`, `ADMIN`.
- `refresh_sessions`: hash refresh token, user, expiry, revoked time, rotation family, device metadata tối thiểu.

### Catalog và kho

- `categories`: tên, slug duy nhất, thứ tự hiển thị, trạng thái.
- `products`: category, tên, slug duy nhất, mô tả, giá VND, trạng thái, ảnh/logo, timestamps.
- `inventory_items`: product, ciphertext, IV/nonce, auth tag, key version, checksum không đảo ngược, trạng thái (`AVAILABLE`, `RESERVED`, `SOLD`, `DISABLED`), order item, timestamps.
- Không có plaintext secret trong bất kỳ cột, log hoặc audit payload nào.
- Tồn hiển thị được đếm từ item `AVAILABLE`; không cho admin nhập số tồn tách rời gây lệch dữ liệu.

### Ví và thanh toán

- `wallets`: một ví VND cho mỗi user, balance cache, version/timestamps.
- Signup tạo ví 0 đồng trong cùng transaction; migration cấp ví 0 đồng cho user cũ chưa có ví, không cho client gửi số dư khởi tạo.
- `wallet_entries`: sổ cái append-only gồm `CREDIT`/`DEBIT`, amount nguyên dương, loại nghiệp vụ, reference duy nhất, balance-after và timestamps.
- Khi chuyển dữ liệu cũ, ví có số dư dương nhận một `OPENING_BALANCE`; ví số dư 0 giữ ledger rỗng để không tạo bút toán amount 0 trái invariant. Mọi trường hợp một user đang có nhiều ví phải được audit để xử lý riêng, không tự động gộp hoặc xóa.
- `payment_deposits`: user, provider `MOMO`, merchant reference duy nhất, amount, status (`PENDING`, `PAID`, `FAILED`, `EXPIRED`, `REVIEW`), provider transaction id, expiry. Schema có thể giữ giá trị `BANK` cũ để tương thích migration nhưng API không tạo deposit mới bằng giá trị này.
- `webhook_events`: provider, provider event/transaction id, request hash, received/processed time, result và lỗi đã làm sạch; unique constraint chống xử lý trùng.
- Mọi số tiền dùng `BIGINT` số đồng VND ở API/domain hoặc `NUMERIC(19,0)` ở DB; tuyệt đối không dùng float/double.

### Đơn hàng

- `orders`: public UUID, user, trạng thái (`COMPLETED`, `FAILED`, `REFUNDED` khi hỗ trợ), total VND, idempotency key, timestamps.
- `order_items`: order, product snapshot (tên/giá), inventory item duy nhất.
- `audit_logs`: actor, action, resource type/id, request/correlation id, thời điểm, metadata đã loại bí mật.

### Ràng buộc quan trọng

- Unique `(provider, provider_transaction_id)` và `(provider, provider_event_id)`.
- Unique `(user_id, idempotency_key)` cho checkout.
- Một `inventory_item` chỉ liên kết tối đa một `order_item`.
- `amount > 0`, `balance >= 0`, currency cố định `VND`.
- Foreign key không cascade xóa lịch sử tài chính/đơn hàng.

## 7. Luồng nghiệp vụ

### 7.1 Nạp qua MoMo

1. Khách chọn số tiền và tạo `payment_deposit` trạng thái `PENDING`.
2. Backend gọi API MoMo bằng credential từ secret store, tạo `orderId/requestId` riêng và trả pay URL/QR phù hợp.
3. Redirect chỉ phục vụ UX; không được dùng để cộng tiền.
4. MoMo POST IPN vào endpoint public chuyên dụng.
5. Backend đọc raw body, xác minh HMAC/signature theo tài liệu MoMo, đối chiếu `partnerCode`, `orderId`, `amount`, `resultCode` và transaction id với deposit trong DB.
6. Trong một transaction: ghi inbox event, khóa deposit + wallet, chuyển deposit sang `PAID`, thêm đúng một ledger credit và cập nhật balance.
7. IPN trùng trả kết quả thành công/no-op, không cộng tiền lần hai. Phản hồi `204` trong giới hạn 15 giây của MoMo.

### 7.2 Mua sản phẩm

Client gửi `POST /api/v1/orders` với `productId` và header `Idempotency-Key`.

Trong một transaction database duy nhất, backend khóa theo thứ tự cố định để tránh deadlock:

1. Xác thực user và khóa ví bằng pessimistic write lock.
2. Chọn/khóa một `inventory_item AVAILABLE` của sản phẩm bằng row lock; xử lý tranh chấp bằng `SKIP LOCKED` hoặc retry có giới hạn.
3. Đọc giá hiện tại, kiểm tra sản phẩm còn bán và balance đủ.
4. Tạo order + order item với snapshot giá/tên.
5. Ghi ledger debit, cập nhật balance và chuyển item sang `SOLD`.
6. Commit tất cả hoặc rollback tất cả.

Kết quả retry cùng `Idempotency-Key` phải trả lại cùng đơn, không trừ tiền lần hai. API tạo đơn không trả plaintext secret; frontend gọi endpoint reveal sau khi order đã commit.

### 7.4 Hiển thị bí mật đã mua

- Chỉ owner của order hoặc admin có quyền được ủy quyền mới giải mã.
- Mặc định che nội dung; user bấm “Hiển thị” để lấy dữ liệu.
- Response dùng `Cache-Control: no-store`; frontend không lưu secret vào localStorage, query cache lâu dài hoặc analytics.
- Mỗi lần reveal được audit nhưng audit không chứa secret.
- Không render secret trong HTML server, email, notification hoặc log.

## 8. Xác thực và bảo mật phiên

- Giữ JWT nhưng bỏ lưu JWT trong `localStorage`.
- Access JWT sống ngắn, nằm trong cookie `HttpOnly; Secure; SameSite=Lax` với path hẹp; refresh token ngẫu nhiên/hoặc JWT riêng được xoay vòng, chỉ lưu hash phía server và có thể revoke.
- SPA dùng CSRF token theo cơ chế Spring Security; tất cả method thay đổi trạng thái phải kiểm tra CSRF, ngoại trừ webhook dùng filter chain riêng và signature provider.
- Cookie chỉ hoạt động qua HTTPS. CORS production chỉ cho origin cấu hình rõ ràng và cho credential khi cần.
- Password hash bằng encoder thích ứng được Spring Security hỗ trợ; có rate limit cho login, signup, refresh, reveal và webhook.
- Logout revoke refresh session, xóa cookies và trả `Clear-Site-Data` phù hợp.
- Admin endpoint bắt buộc `ROLE_ADMIN`; authorization kiểm tra ownership ở service, không tin `userId` do client gửi.

## 9. Mã hóa kho bí mật

- AES-256-GCM với nonce ngẫu nhiên duy nhất cho từng item và AAD liên kết `inventoryItemId`, `productId`, `keyVersion`.
- Master key không lưu trong DB/repo/image; production lấy từ secret manager hoặc biến môi trường được bảo vệ.
- Ciphertext lưu cùng nonce, tag và key version để hỗ trợ rotation.
- Import secret được trim/validate, mã hóa ngay trong service rồi xóa reference plaintext sớm nhất có thể.
- Tuyệt đối không ghi request body import/reveal vào access log, tracing, exception hoặc error response.
- Có job/quy trình rotation khóa theo version; thay khóa không yêu cầu giải mã toàn bộ cùng lúc.

## 10. API v1

Tất cả API JSON dùng UTC ISO-8601, pagination `page/size/sort`, và lỗi theo `application/problem+json` gồm `type`, `title`, `status`, `detail`, `instance`, `code`, `requestId`, `fieldErrors` khi có validation.

### Public/Auth

| Method | Endpoint | Mục đích |
|---|---|---|
| `GET` | `/api/v1/products` | Catalog/filter/search/page |
| `GET` | `/api/v1/products/{slug}` | Chi tiết và tồn khả dụng |
| `POST` | `/api/v1/auth/signup` | Đăng ký |
| `POST` | `/api/v1/auth/login` | Đăng nhập, set secure cookies |
| `POST` | `/api/v1/auth/refresh` | Rotate phiên |
| `POST` | `/api/v1/auth/logout` | Revoke phiên |
| `GET` | `/api/v1/auth/me` | User hiện tại |
| `GET` | `/api/v1/auth/csrf` | Cấp/refresh CSRF token cho SPA |

### Customer

| Method | Endpoint | Mục đích |
|---|---|---|
| `GET` | `/api/v1/wallet` | Balance và thông tin ví |
| `GET` | `/api/v1/wallet/entries` | Lịch sử sổ cái |
| `POST` | `/api/v1/deposits` | Tạo yêu cầu nạp MoMo |
| `GET` | `/api/v1/deposits/{id}` | Poll trạng thái nạp |
| `POST` | `/api/v1/orders` | Mua một sản phẩm; bắt buộc idempotency key |
| `GET` | `/api/v1/orders` | Lịch sử đơn của user |
| `GET` | `/api/v1/orders/{id}` | Chi tiết đơn thuộc user |
| `POST` | `/api/v1/orders/{id}/items/{itemId}/reveal` | Giải mã/giao bí mật |

Hai endpoint ví chỉ dùng user ID từ principal đã xác thực, không nhận user ID do client khai báo. `/wallet` trả `{walletId,balanceVnd,currency}`; `/wallet/entries?page=0&size=20` trả `{items,page,size,totalItems,totalPages}` với `size` tối đa 50. Response không chứa business reference, IBAN hay entity nội bộ và đặt `Cache-Control: no-store`.

### Webhook

| Method | Endpoint | Mục đích |
|---|---|---|
| `POST` | `/api/v1/webhooks/momo` | MoMo IPN đã ký |

Webhook không dùng user JWT/CSRF, nhưng bắt buộc signature/token/mTLS đúng theo hợp đồng provider, size limit, timeout và idempotency.

### Admin

| Method | Endpoint | Mục đích |
|---|---|---|
| CRUD | `/api/v1/admin/categories` | Quản lý danh mục |
| CRUD | `/api/v1/admin/products` | Quản lý sản phẩm/giá/trạng thái |
| `POST` | `/api/v1/admin/products/{id}/inventory-items` | Nhập batch secrets |
| `GET` | `/api/v1/admin/products/{id}/inventory-items` | Danh sách trạng thái, luôn mask secret |
| `PATCH` | `/api/v1/admin/inventory-items/{id}` | Disable/enable item chưa bán |
| `GET` | `/api/v1/admin/orders` | Tra cứu đơn |
| `GET` | `/api/v1/admin/deposits` | Tra cứu/reconcile nạp tiền |
| `GET` | `/api/v1/admin/audit-logs` | Audit bảo mật |

Không có API admin “set balance” thông thường. Điều chỉnh tài chính sau này phải là nghiệp vụ riêng, có lý do, ledger entry và audit.

## 11. Giao diện

### Storefront

- Giữ bố cục navbar ba vùng của Vercel Commerce: thương hiệu/menu bên trái, tìm kiếm ở giữa, wallet/account bên phải.
- Hero `ThreeItemGrid` được đổi thành “Sản phẩm nổi bật”; carousel giữ lại cho danh mục/sản phẩm bán chạy.
- Catalog dùng product tile/gallery của Vercel Commerce, bổ sung chế độ danh sách gọn giống ảnh mẫu để quét nhanh tên, giá và tồn.
- Danh mục Shopify Collection được ánh xạ sang category của Spring Boot; search/sort gọi `/api/v1/products`.
- Hàng có vùng bấm tối thiểu 44px, focus rõ, không dùng emoji làm icon.
- Trang sản phẩm giữ gallery, related products, metadata/JSON-LD; bỏ variant vật lý và thay bằng thông tin thời hạn/bảo hành/tồn kho.
- `Add to Cart` được thay bằng `Mua bằng số dư`; không có giỏ nhiều món để tránh giữ tồn kho dài và làm phức tạp giao sản phẩm số.
- Cart modal được đổi thành wallet drawer: số dư, nút nạp tiền và giao dịch gần đây.
- Wallet drawer chỉ đọc ví của phiên hiện tại qua cookie, tải giao dịch khi mở và xóa dữ liệu riêng khi logout/401. Trên mobile, header hiển thị số dư rút gọn nhưng tên truy cập vẫn có số dư đầy đủ.
- Frontend từ chối hiển thị số tiền vượt giới hạn `Number.MAX_SAFE_INTEGER` để tránh làm tròn âm thầm từ `BIGINT` của backend.
- Trước khi MoMo và IPN được xác minh hoạt động, `/top-up` chỉ hiển thị trạng thái “chưa mở”; không cộng tiền giả lập.
- Dialog xác nhận mua hiển thị tên, giá, số dư trước/sau và lỗi hết hàng/thiếu tiền.
- Desktop có grid 3 cột và list view; mobile một cột, không cuộn ngang.
- Loading skeleton, empty state và error/retry rõ ràng; không bao giờ để trang trắng.

### Ví và thanh toán

- Phương thức cố định là `MoMo`; khách nhập số tiền theo preset/custom có validation.
- Màn hình chờ hiển thị liên kết thanh toán, số tiền, hạn thanh toán và trạng thái realtime/polling.
- Redirect MoMo trở về chỉ hiển thị “đang xác minh”; frontend đợi trạng thái backend, không tự kết luận thành công.

### Đơn hàng

- Danh sách đơn và chi tiết; mỗi item có trạng thái giao hàng số.
- Secret che mặc định, reveal/copy có cảnh báo và tự ẩn lại ở UI.

### Admin

- Dashboard vận hành: doanh thu, nạp chờ/review, sản phẩm sắp hết, lỗi webhook.
- Bảng catalog/kho/đơn/deposit có filter, pagination, bulk import và kết quả từng dòng.
- UI không bao giờ trả danh sách plaintext secret; reveal admin là thao tác riêng và được audit.

### Design tokens

- Giữ neutral palette và blue interactive states của Vercel Commerce: `neutral-50/900`, black/white, `blue-600`; amber chỉ dùng cho số dư, giá và tồn kho.
- Typography: Geist Sans như nguồn Vercel Commerce; số tiền có tabular numerals.
- Bo góc, border, product tile, navbar, search, gallery, mobile sheet và toast bám sát source; branding/logo/content được thay bằng của dự án.
- Contrast WCAG 2.1 AA, hỗ trợ keyboard, screen reader và `prefers-reduced-motion`.
- Kiểm tra ở 375px, 768px, 1024px và 1440px.

### Thành phần lấy và sửa từ Vercel Commerce

| Source component | Dùng trong dự án | Thay đổi bắt buộc |
|---|---|---|
| `layout/navbar` | Navbar desktop/mobile | Menu từ category API; thêm wallet, orders, account |
| `layout/navbar/search` | Tìm sản phẩm | Tiếng Việt, URL search params, Spring API |
| `grid/tile`, `three-items` | Product card/featured grid | VND, tồn kho, digital badge, buy state |
| `carousel` | Sản phẩm bán chạy/liên quan | Dữ liệu Spring Boot, empty/error state |
| `product/gallery` | Chi tiết sản phẩm | Ảnh/logo digital product, accessible alt |
| `product-description` | Giá/mô tả/mua | Bỏ Shopify variants; thêm stock/warranty/buy wallet |
| `cart/modal` | Wallet drawer | Bỏ shipping/tax/quantity; thêm balance/top-up/ledger |
| `error.tsx`, loading UI | Error/loading boundary | Thông báo tiếng Việt, retry, correlation id |
| Shopify provider | Không dùng | Thay hoàn toàn bằng typed Spring REST client |

Nếu sao chép substantial portions, repo phát hành phải giữ `license.md` của dự án và thêm copyright Vercel/MIT vào `THIRD_PARTY_NOTICES.md`.

## 12. Quy ước code

### Java

- Package theo feature: `auth`, `catalog`, `inventory`, `wallet`, `payment`, `order`, `audit`.
- Controller chỉ parse/validate/authorize đầu vào; business transaction nằm trong application service.
- DTO là immutable records khi phù hợp; không trả JPA entity ra API.
- Tất cả public operation có typed result và domain exception; không dùng `Optional.get()` hoặc catch rỗng.
- Lock acquisition luôn theo một thứ tự được tài liệu hóa.

```java
@Transactional
public OrderResult placeOrder(UUID userId, UUID productId, String idempotencyKey) {
    var wallet = walletRepository.lockByUserId(userId).orElseThrow(WalletNotFound::new);
    var stock = inventoryRepository.lockNextAvailable(productId).orElseThrow(OutOfStock::new);
    return orderWriter.complete(wallet, stock, idempotencyKey);
}
```

### TypeScript/React

- Component trình bày tách khỏi query/mutation hook.
- Server state dùng TanStack Query; filter/page nằm trong URL; không tạo global store nếu chưa cần.
- Không dùng `any`, index làm React key, inline raw colors hoặc component trên 200 dòng.
- Mọi form có label thật, lỗi cạnh field, trạng thái pending và chặn submit lặp.

```tsx
export function ProductRow({ product, onBuy }: ProductRowProps) {
  return (
    <article aria-labelledby={`product-${product.id}`}>
      <h3 id={`product-${product.id}`}>{product.name}</h3>
      <Money value={product.priceVnd} currency="VND" />
      <Button disabled={product.availableCount === 0} onClick={() => onBuy(product.id)}>
        Mua ngay
      </Button>
    </article>
  );
}
```

## 13. Kiểm thử

### Backend

- Unit test cho amount/reference validation, signature canonicalization, encryption/decryption và state machine.
- Integration test với Testcontainers/PostgreSQL cho Flyway, repository locks và transactions.
- Concurrency test: nhiều request mua item cuối chỉ một request thành công; balance không âm.
- Webhook contract test: signature đúng/sai, amount/reference mismatch, retry, duplicate event và out-of-order event.
- Security test: ownership/role, CSRF, cookie flags, rate limit, input limits và không rò secret qua error/log.

### Frontend

- Component tests cho catalog, checkout dialog, QR/payment state, order reveal và mọi empty/error/loading state.
- E2E: đăng ký → đăng nhập → tạo deposit sandbox → webhook fixture đã ký → mua → reveal.
- E2E đồng thời cho double click/retry; accessibility scan bằng axe.
- Browser smoke tests không có console error/network request sai origin.

### Chất lượng và dependency

- Backend: `./mvnw verify`, dependency scan và secret scan.
- Frontend: lint, typecheck, unit, build, Playwright và `npm audit`/scanner tương đương.
- Không dùng `npm audit fix --force`; từng advisory phải được triage và test migration.

## 14. Quan sát và vận hành

- Structured logs với `requestId`, user id nội bộ, order/deposit id; redact token, cookie, signature và secret.
- Metrics: login failure, webhook received/invalid/duplicate, deposit latency, checkout success/failure, out-of-stock, DB transaction retry.
- Health/readiness không lộ config; alert khi webhook invalid tăng, deposit treo hoặc tồn xuống ngưỡng.
- Backup PostgreSQL tự động, mã hóa, retention rõ ràng và diễn tập restore.
- Reconciliation job so sánh deposit `PAID`/ledger với dữ liệu provider; mismatch bật cờ `needs_reconciliation_review` một chiều trên deposit đã kết thúc, giữ nguyên trạng thái thanh toán và đưa vào hàng đợi xử lý thủ công. Thanh toán được xác nhận sau `EXPIRED` cũng bật cờ này, không tự động credit. `REVIEW` là trạng thái của deposit chưa hoàn tất trước khi credit; không chuyển `PAID` quay lại `REVIEW`.

## 15. Threat model tối thiểu

| Nhóm | Rủi ro | Kiểm soát bắt buộc |
|---|---|---|
| Spoofing | Webhook giả, chiếm phiên | Signature/mTLS theo provider, secure cookies, token rotation, rate limit |
| Tampering | Sửa amount/reference/order | Server-side price, DB constraints, signature validation, immutable ledger |
| Repudiation | Chối giao dịch/reveal | Audit log, correlation id, timestamps, provider transaction id |
| Information disclosure | Lộ JWT/secret hàng | HttpOnly cookie, AES-GCM, no-store, redaction, ownership checks |
| Denial of service | Spam login/webhook/checkout | Rate/size limits, timeouts, indexes, bounded retry |
| Elevation of privilege | Customer gọi admin/reveal đơn khác | Method security, service ownership check, negative tests |

## 16. Commands mục tiêu

Các lệnh này là hợp đồng developer experience sau migration:

```powershell
# Local full stack
docker compose --env-file .env.local -f docker-compose.yml -f docker-compose.dev.yml up --build

# Backend
cd backend
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run

# Frontend
cd frontend
npm ci
npm run dev
npm run lint
npm run typecheck
npm test
npm run build
npm run test:e2e
```

Production dùng `.env.production` nằm ngoài Git hoặc secret manager; không dùng file `.env.properties` mẫu đang được track.

## 17. Cấu trúc mục tiêu

```text
e-wallet/
├── backend/
│   ├── src/main/java/.../{auth,catalog,inventory,wallet,payment,order,audit}/
│   ├── src/main/resources/db/migration/
│   └── src/test/
├── frontend/
│   ├── app/{page,search,product,wallet,orders,auth,admin}/
│   ├── components/{layout,product,wallet,order,ui}/
│   ├── lib/{api,auth,contracts,format}/
│   └── e2e/
├── docs/
├── design-system/
├── tasks/
├── docker-compose.yml
├── docker-compose.dev.yml
└── docker-compose.prod.yml
```

## 18. Boundaries

### Luôn làm

- Xác minh chữ ký và đối chiếu giao dịch trước khi credit.
- Dùng idempotency + unique constraints cho webhook và checkout.
- Khóa row và transaction cho mọi thay đổi balance/inventory.
- Mã hóa secret trước khi persist; redact log/error/trace.
- Viết Flyway migration tiến tới, có test rollback/restore dữ liệu.
- Chạy test liên quan và dependency/security scan trước mỗi mốc.

### Phải hỏi trước

- Đổi hợp đồng API/IPN MoMo.
- Đổi chính sách hoàn tiền/điều chỉnh balance.
- Xóa dữ liệu production, rotate/revoke production credentials.
- Đổi branding, tên miền hoặc hạ tầng cloud có phát sinh chi phí.
- Import dữ liệu kho thật hoặc chạy webhook production.

### Tuyệt đối không làm

- Commit merchant key, JWT key, DB password hoặc encryption master key.
- Tin redirect từ MoMo là bằng chứng thanh toán.
- Cộng ví từ request do client tự gửi hoặc từ webhook không xác thực.
- Lưu token trong localStorage/sessionStorage.
- Log/email/cache plaintext secret.
- Dùng float/double cho tiền.
- Cho sửa/xóa ledger đã ghi.

## 19. Tiêu chí nghiệm thu

1. Khách có thể đăng ký, đăng nhập, đăng xuất và refresh phiên; không có auth token trong Web Storage.
2. Catalog public hiển thị đúng giá/tồn, responsive và không có blank screen.
3. MoMo sandbox tạo deposit; chỉ IPN hợp lệ mới credit.
4. Gửi cùng webhook ít nhất 10 lần chỉ tạo một credit ledger entry.
5. Hai khách mua đồng thời item cuối: đúng một đơn `COMPLETED`, đúng một debit, item không bán trùng.
6. Retry cùng idempotency key trả cùng order và không debit lần hai.
7. DB dump không chứa plaintext inventory; log scan không thấy token/merchant secret/product secret.
8. Chỉ owner xem được secret của đơn; request chéo user trả `404/403` theo policy và được test.
9. Frontend pass lint, typecheck, unit, production build, E2E và accessibility smoke test.
10. Backend pass unit/integration/concurrency/security tests và Flyway chạy được trên database sạch.
11. Docker production chỉ public reverse proxy; DB/backend nội bộ, HTTPS và security headers hoạt động.
12. Có runbook deploy, backup/restore, key rotation, reconciliation và rollback.

## 20. Câu hỏi còn mở trước adapter production

Các câu hỏi này không chặn việc xây domain/UI và MoMo sandbox, nhưng phải được trả lời trước khi kết nối tiền thật:

1. Tên thương hiệu, logo, domain và màu nhận diện cuối cùng là gì?
2. Chính sách hoàn tiền/đổi mã khi credential lỗi sẽ xử lý thủ công hay tự động?
3. Secret production sẽ đặt ở dịch vụ nào (Docker secrets, Vault, cloud secret manager)?

Không gửi giá trị secret qua chat hoặc commit Git; chỉ cấu hình trực tiếp ở môi trường deploy.

## 21. Nguồn quyết định kỹ thuật

- Spring Boot stable lines và system requirements: <https://docs.spring.io/spring-boot/>
- Spring Security CSRF cho SPA: <https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html>
- PostgreSQL row-level locks: <https://www.postgresql.org/docs/17/explicit-locking.html>
- MoMo IPN và yêu cầu xác minh signature/đối chiếu giao dịch: <https://developers.momo.vn/v3/docs/payment/api/result-handling/notification/>
- MoMo onboarding/credentials/environments: <https://developers.momo.vn/v3/docs/payment/onboarding/integration-process/>
- React thông báo deprecate Create React App: <https://react.dev/blog/2025/02/14/sunsetting-create-react-app>
- Next.js 16.3.3 Active LTS security release: <https://nextjs.org/blog>
- Next.js Server/Client Components: <https://nextjs.org/docs/app/getting-started/server-and-client-components>
- Next.js data security với external HTTP APIs: <https://nextjs.org/docs/app/guides/data-security>
- Next.js Backend for Frontend/proxy: <https://nextjs.org/docs/app/guides/backend-for-frontend>
- Tailwind CSS 4 với Next.js: <https://tailwindcss.com/docs/installation/framework-guides/nextjs>
- OWASP Session Management: <https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html>
- OWASP Cryptographic Storage: <https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html>
- Shopizer tham khảo domain e-commerce: <https://github.com/shopizer-ecommerce/shopizer>
- Vercel Commerce làm nguồn UI chính (MIT), commit tham chiếu `3761e52`: <https://github.com/vercel/commerce>
- Spree Storefront chỉ tham khảo luồng account/order: <https://github.com/spree/storefront>
- E-commerce Admin Dashboard tham khảo admin UX: <https://github.com/raouf-b-dev/ecommerce-admin-dashboard>
