'use client';

import { FormEvent, useState } from 'react';

import type { AdminCategory, AdminProduct, ProductInput } from '@/lib/api/admin-catalog';

type ProductFormProps = {
  categories: AdminCategory[];
  product?: AdminProduct;
  onSave: (input: ProductInput) => Promise<void>;
  onCancel: () => void;
};

type FormErrors = Partial<Record<'categoryId' | 'name' | 'slug' | 'description' | 'priceVnd' | 'imageUrl', string>>;
type ProductFormValues = {
  categoryId: string;
  name: string;
  slug: string;
  description: string;
  priceVnd: string;
  imageUrl: string;
};

export function ProductForm({ categories, product, onSave, onCancel }: ProductFormProps) {
  const [categoryId, setCategoryId] = useState(product?.category.id ?? categories[0]?.id ?? '');
  const [name, setName] = useState(product?.name ?? '');
  const [slug, setSlug] = useState(product?.slug ?? '');
  const [description, setDescription] = useState(product?.description ?? '');
  const [priceVnd, setPriceVnd] = useState(product ? String(product.priceVnd) : '');
  const [imageUrl, setImageUrl] = useState(product?.imageUrl ?? '');
  const [status, setStatus] = useState<ProductInput['status']>(product?.status ?? 'ACTIVE');
  const [errors, setErrors] = useState<FormErrors>({});
  const [requestError, setRequestError] = useState('');
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validate({ categoryId, name, slug, description, priceVnd, imageUrl });
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    setPending(true);
    setRequestError('');
    try {
      await onSave({
        categoryId,
        name: name.trim(),
        slug: slug.trim(),
        description: description.trim(),
        priceVnd: Number(priceVnd),
        status,
        imageUrl: imageUrl.trim() || null,
      });
    } catch {
      setRequestError('Không thể lưu sản phẩm. Kiểm tra dữ liệu hoặc thử lại.');
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="admin-editor-form" onSubmit={submit} noValidate>
      <div className="admin-field-grid">
        <Field label="Tên sản phẩm" error={errors.name}>
          <input aria-label="Tên sản phẩm" value={name} maxLength={160} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="Slug sản phẩm" error={errors.slug}>
          <input aria-label="Slug sản phẩm" value={slug} maxLength={180} onChange={(e) => setSlug(e.target.value)} />
        </Field>
        <Field label="Danh mục" error={errors.categoryId}>
          <select aria-label="Danh mục" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
            {categories.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
        </Field>
        <Field label="Giá bán VND" error={errors.priceVnd}>
          <input aria-label="Giá bán VND" value={priceVnd} inputMode="numeric" onChange={(e) => setPriceVnd(e.target.value)} />
        </Field>
        <Field label="Trạng thái">
          <select aria-label="Trạng thái" value={status} onChange={(e) => setStatus(e.target.value as ProductInput['status'])}>
            <option value="ACTIVE">Đang bán</option>
            <option value="INACTIVE">Ngừng bán</option>
          </select>
        </Field>
        <Field label="Ảnh HTTPS" error={errors.imageUrl}>
          <input aria-label="Ảnh HTTPS" value={imageUrl} maxLength={2048} onChange={(e) => setImageUrl(e.target.value)} />
        </Field>
      </div>
      <Field label="Mô tả sản phẩm" error={errors.description}>
        <textarea aria-label="Mô tả sản phẩm" value={description} rows={5} maxLength={10_000} onChange={(e) => setDescription(e.target.value)} />
      </Field>
      {requestError && <p className="auth-request-error" role="alert">{requestError}</p>}
      <div className="admin-form-actions">
        <button className="button button-secondary" type="button" onClick={onCancel} disabled={pending}>Hủy</button>
        <button className="button button-primary" type="submit" disabled={pending || categories.length === 0}>
          {pending ? 'Đang lưu…' : 'Lưu sản phẩm'}
        </button>
      </div>
    </form>
  );
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <label className="admin-field">
      <span>{label}</span>
      {children}
      {error && <small className="auth-field-error">{error}</small>}
    </label>
  );
}

function validate(values: ProductFormValues): FormErrors {
  const errors: FormErrors = {};
  if (!values.categoryId) errors.categoryId = 'Hãy tạo hoặc chọn một danh mục.';
  if (!values.name.trim()) errors.name = 'Tên sản phẩm không được để trống.';
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(values.slug.trim())) {
    errors.slug = 'Slug chỉ gồm chữ thường, số và dấu gạch ngang.';
  }
  if (!values.description.trim()) errors.description = 'Mô tả không được để trống.';
  if (!/^[1-9]\d*$/.test(values.priceVnd) || !Number.isSafeInteger(Number(values.priceVnd))) {
    errors.priceVnd = 'Giá phải là số nguyên VND dương, không chứa dấu phân cách.';
  }
  if (values.imageUrl.trim()) {
    try {
      const url = new URL(values.imageUrl.trim());
      if (url.protocol !== 'https:' || url.username || url.password) throw new Error();
    } catch {
      errors.imageUrl = 'Ảnh phải là URL HTTPS hợp lệ.';
    }
  }
  return errors;
}
