'use client';

import { FormEvent, useState } from 'react';

import type { InventoryImportResponse } from '@/lib/api/admin-inventory';

type InventoryImportFormProps = {
  disabled: boolean;
  onImport: (secrets: string[]) => Promise<InventoryImportResponse>;
};

export function InventoryImportForm({ disabled, onImport }: InventoryImportFormProps) {
  const [plaintext, setPlaintext] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string>();
  const [response, setResponse] = useState<InventoryImportResponse>();

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const secrets = rows(plaintext);
    if (secrets.length === 0) {
      setError('Nhập ít nhất một dòng dữ liệu.');
      return;
    }
    setPlaintext('');
    setError(undefined);
    setResponse(undefined);
    setPending(true);
    try {
      setResponse(await onImport(secrets));
    } catch {
      setError('Không thể nhập kho. Dữ liệu trong ô đã được xóa; vui lòng kiểm tra và nhập lại nếu cần.');
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="admin-panel" aria-labelledby="inventory-import-title">
      <div className="admin-panel-heading">
        <div>
          <p className="eyebrow">Kho mã hóa</p>
          <h2 id="inventory-import-title">Nhập sản phẩm bí mật</h2>
        </div>
        <span>Tối đa 100 dòng/lần</span>
      </div>
      <form className="inventory-import-form" onSubmit={handleSubmit}>
        <label htmlFor="inventory-secrets">Mỗi dòng là một tài khoản, mã hoặc API key</label>
        <textarea
          id="inventory-secrets"
          value={plaintext}
          onChange={(event) => setPlaintext(event.target.value)}
          rows={8}
          disabled={disabled || pending}
          autoComplete="off"
          spellCheck={false}
          placeholder="Dữ liệu được mã hóa trước khi lưu và không xuất hiện lại trong danh sách."
        />
        <p className="admin-hint">Không dán dữ liệu thật khi đang chia sẻ màn hình. Nội dung ô sẽ bị xóa ngay khi gửi.</p>
        {error && <p className="auth-request-error" role="alert">{error}</p>}
        <button className="button button-primary" type="submit" disabled={disabled || pending}>
          {pending ? 'Đang mã hóa…' : 'Mã hóa và nhập kho'}
        </button>
      </form>

      {response && (
        <div className="import-results" role="status">
          <strong>Đã nhập {response.imported}/{response.requested} dòng</strong>
          <ol>
            {response.results.map((result) => (
              <li key={result.row} className={result.status === 'IMPORTED' ? 'result-ok' : 'result-error'}>
                Dòng {result.row}: {result.status === 'IMPORTED' ? 'Đã nhập' : result.detail ?? 'Bị từ chối'}
              </li>
            ))}
          </ol>
        </div>
      )}
    </section>
  );
}

function rows(value: string): string[] {
  const result = value.split(/\r?\n/);
  while (result.at(-1) === '') result.pop();
  return result.slice(0, 101);
}
