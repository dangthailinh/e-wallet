'use client';

import { FormEvent, useState } from 'react';

import {
  changeCategoryStatus,
  createCategory,
  updateCategory,
  type AdminCategory,
  type CategoryInput,
} from '@/lib/api/admin-catalog';

type CategoryManagerProps = {
  categories: AdminCategory[];
  onChange: (category: AdminCategory) => void;
};

export function CategoryManager({ categories, onChange }: CategoryManagerProps) {
  const [editing, setEditing] = useState<AdminCategory>();
  const [creating, setCreating] = useState(false);
  const [pendingId, setPendingId] = useState<string>();
  const [error, setError] = useState('');

  async function save(input: CategoryInput) {
    setPendingId(editing?.id ?? 'new');
    setError('');
    try {
      const saved = editing ? await updateCategory(editing.id, input) : await createCategory(input);
      onChange(saved);
      setEditing(undefined);
      setCreating(false);
    } catch {
      setError('Không thể lưu danh mục. Slug có thể đã được sử dụng.');
    } finally {
      setPendingId(undefined);
    }
  }

  async function toggle(category: AdminCategory) {
    setPendingId(category.id);
    setError('');
    try {
      onChange(await changeCategoryStatus(category.id, category.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'));
    } catch {
      setError('Không thể đổi trạng thái danh mục.');
    } finally {
      setPendingId(undefined);
    }
  }

  return (
    <section className="admin-panel" aria-labelledby="category-title">
      <div className="admin-panel-heading">
        <div><p className="eyebrow">Phân loại</p><h2 id="category-title">Danh mục</h2></div>
        <button className="button button-secondary" type="button" onClick={() => { setEditing(undefined); setCreating(true); }}>
          Thêm danh mục
        </button>
      </div>
      {error && <p className="auth-request-error" role="alert">{error}</p>}
      <div className="category-admin-list">
        {categories.map((category) => (
          <article key={category.id}>
            <div>
              <strong>{category.name}</strong>
              <small>/{category.slug} · Thứ tự {category.displayOrder}</small>
            </div>
            <span className={`admin-status admin-status-${category.status.toLowerCase()}`}>
              {category.status === 'ACTIVE' ? 'Đang dùng' : 'Đã ẩn'}
            </span>
            <div className="admin-row-actions">
              <button type="button" aria-label={`Sửa danh mục ${category.name}`} onClick={() => { setCreating(false); setEditing(category); }}>Sửa</button>
              <button type="button" aria-label={`${category.status === 'ACTIVE' ? 'Ẩn' : 'Hiện'} danh mục ${category.name}`} disabled={pendingId === category.id} onClick={() => void toggle(category)}>
                {category.status === 'ACTIVE' ? 'Ẩn' : 'Hiện'}
              </button>
            </div>
          </article>
        ))}
        {categories.length === 0 && <p className="admin-empty">Chưa có danh mục. Tạo danh mục trước khi thêm sản phẩm.</p>}
      </div>
      {(creating || editing) && (
        <CategoryForm
          key={editing?.id ?? 'new'}
          category={editing}
          pending={pendingId === (editing?.id ?? 'new')}
          onCancel={() => { setCreating(false); setEditing(undefined); }}
          onSave={save}
        />
      )}
    </section>
  );
}

function CategoryForm({
  category, pending, onSave, onCancel,
}: {
  category?: AdminCategory;
  pending: boolean;
  onSave: (input: CategoryInput) => Promise<void>;
  onCancel: () => void;
}) {
  const [name, setName] = useState(category?.name ?? '');
  const [slug, setSlug] = useState(category?.slug ?? '');
  const [displayOrder, setDisplayOrder] = useState(String(category?.displayOrder ?? 0));
  const [validation, setValidation] = useState('');

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!name.trim() || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug) || !/^\d+$/.test(displayOrder)) {
      setValidation('Nhập tên, slug chữ thường hợp lệ và thứ tự không âm.');
      return;
    }
    setValidation('');
    void onSave({ name: name.trim(), slug, displayOrder: Number(displayOrder) });
  }

  return (
    <form className="category-admin-form" onSubmit={submit} noValidate>
      <label><span>Tên danh mục</span><input value={name} maxLength={100} onChange={(e) => setName(e.target.value)} /></label>
      <label><span>Slug danh mục</span><input value={slug} maxLength={120} onChange={(e) => setSlug(e.target.value)} /></label>
      <label><span>Thứ tự</span><input value={displayOrder} inputMode="numeric" onChange={(e) => setDisplayOrder(e.target.value)} /></label>
      {validation && <p className="auth-field-error" role="alert">{validation}</p>}
      <div className="admin-form-actions">
        <button className="button button-secondary" type="button" onClick={onCancel}>Hủy</button>
        <button className="button button-primary" type="submit" disabled={pending}>{pending ? 'Đang lưu…' : 'Lưu danh mục'}</button>
      </div>
    </form>
  );
}
