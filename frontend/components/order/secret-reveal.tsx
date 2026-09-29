'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';

import { announceSessionEnded, subscribeSessionEnded } from '@/lib/api/auth';
import { ApiError } from '@/lib/api/errors';
import { revealOrderItem } from '@/lib/api/orders';

type SecretRevealProps = { orderId: string; itemId: string };

export function SecretReveal({ orderId, itemId }: SecretRevealProps) {
  const [secret, setSecret] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<'auth' | 'generic' | null>(null);
  const [feedback, setFeedback] = useState('');
  const mounted = useRef(true);
  const requestVersion = useRef(0);
  const revealButton = useRef<HTMLButtonElement>(null);
  const copyButton = useRef<HTMLButtonElement>(null);
  const loginLink = useRef<HTMLAnchorElement>(null);

  useEffect(() => {
    mounted.current = true;
    function onSessionEnded() {
      requestVersion.current += 1;
      setSecret(null);
      setPending(false);
      setError('auth');
      setFeedback('Thông tin bàn giao đã được ẩn vì phiên đăng nhập kết thúc.');
      requestAnimationFrame(() => loginLink.current?.focus());
    }
    const unsubscribe = subscribeSessionEnded(onSessionEnded);
    return () => {
      mounted.current = false;
      requestVersion.current += 1;
      unsubscribe();
    };
  }, []);

  async function reveal() {
    if (pending) return;
    const version = ++requestVersion.current;
    setPending(true);
    setError(null);
    setFeedback('');
    try {
      const response = await revealOrderItem(orderId, itemId);
      if (mounted.current && requestVersion.current === version) {
        setSecret(response.secret);
        setFeedback('Thông tin bàn giao đã hiển thị.');
        requestAnimationFrame(() => copyButton.current?.focus());
      }
    } catch (reason) {
      if (mounted.current && requestVersion.current === version) {
        if (reason instanceof ApiError && reason.status === 401) announceSessionEnded();
        else setError('generic');
      }
    } finally {
      if (mounted.current && requestVersion.current === version) setPending(false);
    }
  }

  async function copy() {
    if (!secret) return;
    try {
      await navigator.clipboard.writeText(secret);
      setFeedback('Đã sao chép thông tin bàn giao.');
    } catch {
      setFeedback('Không thể sao chép tự động. Anh có thể chọn và sao chép thủ công.');
    }
  }

  function hide() {
    requestVersion.current += 1;
    setSecret(null);
    setError(null);
    setFeedback('Đã ẩn thông tin bàn giao.');
    requestAnimationFrame(() => revealButton.current?.focus());
  }

  return (
    <section
      className="mt-5 rounded-lg border border-[var(--border)] bg-[var(--surface-subtle)] p-4"
      aria-label="Thông tin bàn giao"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="m-0 text-xs font-bold uppercase tracking-[0.08em] text-[var(--muted)]">Thông tin bàn giao</p>
          {secret ? (
            <code className="mt-2 block max-w-full select-all overflow-x-auto whitespace-pre-wrap break-all text-sm text-[var(--foreground)]">
              {secret}
            </code>
          ) : (
            <span className="mt-2 block font-mono text-base tracking-[0.12em]" aria-label="Thông tin đang được ẩn">
              ••••••••••••
            </span>
          )}
        </div>

        <div className="flex flex-wrap gap-2">
          {secret ? (
            <>
              <button ref={copyButton} className="button button-secondary" type="button" onClick={copy}>
                Sao chép
              </button>
              <button className="button button-secondary" type="button" onClick={hide}>
                Ẩn lại
              </button>
            </>
          ) : error === 'auth' ? (
            <Link ref={loginLink} className="button button-primary" href="/login">
              Đăng nhập lại
            </Link>
          ) : (
            <button
              ref={revealButton}
              className="button button-primary"
              type="button"
              onClick={reveal}
              disabled={pending}
            >
              {pending ? 'Đang mở…' : error === 'generic' ? 'Thử mở lại' : 'Hiển thị thông tin bàn giao'}
            </button>
          )}
        </div>
      </div>

      {error && (
        <p className="mb-0 mt-3 text-sm text-[var(--destructive)]" role="alert">
          {error === 'auth'
            ? 'Phiên đăng nhập đã kết thúc. Thông tin bàn giao đã được ẩn.'
            : 'Không thể mở thông tin bàn giao. Dữ liệu vẫn đang được bảo vệ, vui lòng thử lại.'}
        </p>
      )}
      <p className={feedback ? 'mb-0 mt-3 text-sm text-[var(--muted)]' : 'sr-only'} role="status" aria-live="polite">
        {feedback}
      </p>
    </section>
  );
}
