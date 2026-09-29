-- Development/test metadata only. This seed intentionally contains no sellable
-- credential, account, redemption code, token value or merchant secret.
INSERT INTO categories (id, name, slug, display_order, status, created_at, updated_at, version)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'Tài khoản AI', 'ai-accounts', 10, 'ACTIVE', now(), now(), 0),
    ('10000000-0000-0000-0000-000000000002', 'Gói API', 'api-packages', 20, 'ACTIVE', now(), now(), 0),
    ('10000000-0000-0000-0000-000000000003', 'Học tập & công việc', 'productivity', 30, 'ACTIVE', now(), now(), 0),
    ('10000000-0000-0000-0000-000000000004', 'Giải trí', 'entertainment', 40, 'ACTIVE', now(), now(), 0),
    ('10000000-0000-0000-0000-000000000005', 'Thiết kế & sáng tạo', 'creator-tools', 50, 'ACTIVE', now(), now(), 0)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO products (
    id, category_id, name, slug, description, price_vnd, status, image_url, created_at, updated_at, version
)
VALUES
    (
        '20000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        'ChatGPT Free đã xác minh',
        'chatgpt-free-verified',
        'Tài khoản dùng thử dành cho nhu cầu cơ bản. Thông tin bàn giao được hiển thị trong đơn hàng.',
        5000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000002',
        '10000000-0000-0000-0000-000000000001',
        'ChatGPT Plus - 1 tháng',
        'chatgpt-plus-1-month',
        'Gói tài khoản AI một tháng, hỗ trợ kiểm tra trạng thái sau khi nhận hàng.',
        95000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000003',
        '10000000-0000-0000-0000-000000000001',
        'ChatGPT Plus Pay Money',
        'chatgpt-plus-pay-money',
        'Gói tài khoản cấp cao dành cho người dùng có nhu cầu sử dụng thường xuyên.',
        350000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000004',
        '10000000-0000-0000-0000-000000000001',
        'GPT Pro x20',
        'gpt-pro-x20',
        'Gói tài khoản chuyên nghiệp số lượng lớn dành cho nhóm làm việc.',
        4500000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000005',
        '10000000-0000-0000-0000-000000000001',
        'Grok Super - 10 ngày',
        'grok-super-10-days',
        'Gói truy cập Grok ngắn hạn, phù hợp để trải nghiệm và đánh giá dịch vụ.',
        100000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000006',
        '10000000-0000-0000-0000-000000000002',
        'Codex 10M credits - 1 ngày',
        'codex-10m-1-day',
        'Gói sử dụng API theo hạn mức trong một ngày. Dữ liệu truy cập chỉ xuất hiện sau khi mua.',
        50000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000007',
        '10000000-0000-0000-0000-000000000002',
        'Codex 50M credits - 1 ngày',
        'codex-50m-1-day',
        'Gói sử dụng API hạn mức trung bình trong một ngày dành cho tác vụ lập trình.',
        110000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000008',
        '10000000-0000-0000-0000-000000000002',
        'Codex 100M credits - 1 ngày',
        'codex-100m-1-day',
        'Gói sử dụng API hạn mức cao trong một ngày dành cho tác vụ lập trình.',
        130000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000009',
        '10000000-0000-0000-0000-000000000002',
        'Claude 10M credits - 1 ngày',
        'claude-10m-1-day',
        'Gói sử dụng API theo hạn mức nhỏ trong một ngày cho nhu cầu thử nghiệm.',
        50000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000010',
        '10000000-0000-0000-0000-000000000002',
        'Claude 50M credits - 1 ngày',
        'claude-50m-1-day',
        'Gói sử dụng API theo hạn mức trung bình trong một ngày.',
        120000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000011',
        '10000000-0000-0000-0000-000000000002',
        'Claude 100M credits - 1 ngày',
        'claude-100m-1-day',
        'Gói sử dụng API theo hạn mức cao trong một ngày.',
        150000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000012',
        '10000000-0000-0000-0000-000000000003',
        'Notion Plus Edu - 3 tháng',
        'notion-plus-edu-3-months',
        'Không gian làm việc Notion dành cho học tập và quản lý dự án cá nhân.',
        90000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000013',
        '10000000-0000-0000-0000-000000000003',
        'Microsoft 365 Premium',
        'microsoft-365-premium',
        'Gói ứng dụng văn phòng dành cho công việc và học tập.',
        85000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000014',
        '10000000-0000-0000-0000-000000000004',
        'Netflix Premium 4K - 1 tháng',
        'netflix-premium-4k-1-month',
        'Gói giải trí chất lượng cao trong một tháng, thông tin bảo hành hiển thị tại đơn hàng.',
        35000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000015',
        '10000000-0000-0000-0000-000000000004',
        'Netflix Premium 4K - 1 năm',
        'netflix-premium-4k-1-year',
        'Gói giải trí chất lượng cao dài hạn trong một năm.',
        200000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000016',
        '10000000-0000-0000-0000-000000000005',
        'Canva Pro - 1 năm',
        'canva-pro-1-year',
        'Gói công cụ thiết kế cho cá nhân, học tập và nội dung mạng xã hội.',
        130000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000017',
        '10000000-0000-0000-0000-000000000005',
        'CapCut Pro - 7 ngày',
        'capcut-pro-7-days',
        'Gói chỉnh sửa video cá nhân trong bảy ngày.',
        7000, 'ACTIVE', NULL, now(), now(), 0
    ),
    (
        '20000000-0000-0000-0000-000000000018',
        '10000000-0000-0000-0000-000000000003',
        'LinkedIn Premium Career - 3 tháng',
        'linkedin-premium-career-3-months',
        'Gói hỗ trợ tìm việc và phát triển hồ sơ nghề nghiệp trong ba tháng.',
        90000, 'ACTIVE', NULL, now(), now(), 0
    )
ON CONFLICT (slug) DO NOTHING;

-- The development storefront needs visible stock for browser smoke tests. These
-- rows are intentionally synthetic, non-decryptable payloads; production stock
-- must always be imported by an authorized admin through the encrypted API.
UPDATE products
SET name = 'API 10M Token Codex'
WHERE slug = 'codex-10m-1-day';

INSERT INTO inventory_items (
    id, product_id, ciphertext, nonce, auth_tag, key_version,
    secret_checksum, status, order_item_id, created_at, updated_at, version
)
SELECT
    ('40000000-0000-0000-0000-' || lpad(series::text, 12, '0'))::uuid,
    '20000000-0000-0000-0000-000000000006'::uuid,
    decode('00', 'hex'),
    decode(repeat('00', 12), 'hex'),
    decode(repeat('00', 16), 'hex'),
    1,
    decode(lpad(to_hex(series), 64, '0'), 'hex'),
    'AVAILABLE',
    NULL,
    now(), now(), 0
FROM generate_series(1, 11) AS series
ON CONFLICT (id) DO NOTHING;
