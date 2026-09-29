const vnd = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
});

export function Price({ value, className }: { value: number; className?: string }) {
  return <span className={className}>{vnd.format(value)}</span>;
}
