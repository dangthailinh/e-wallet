'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';

import { AuthSession, logout, restoreSession } from '@/lib/api/auth';
import { WalletDrawer } from '@/components/wallet/wallet-drawer';

export function SessionNavigation() {
  const pathname = usePathname();
  const [sessionState, setSessionState] = useState<{ pathname: string | null; session: AuthSession | null }>();
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    let active = true;
    restoreSession()
      .then((value) => active && setSessionState({ pathname, session: value }))
      .catch(() => active && setSessionState({ pathname, session: null }));
    return () => {
      active = false;
    };
  }, [pathname]);

  if (!sessionState || sessionState.pathname !== pathname) {
    return <span className="session-nav-placeholder" role="status" aria-label="Đang kiểm tra phiên" />;
  }
  const session = sessionState.session;
  if (session === null) {
    return (
      <Link href="/login" className="button button-primary">
        Đăng nhập
      </Link>
    );
  }

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await logout();
    } finally {
      setSessionState({ pathname, session: null });
      setLoggingOut(false);
    }
  }

  return (
    <div className="session-nav" aria-label="Tài khoản đang đăng nhập">
      {session.roles.includes('ROLE_USER') && (
        <WalletDrawer key={session.id} onSessionExpired={() => setSessionState({ pathname, session: null })} />
      )}
      {session.roles.includes('ROLE_ADMIN') && (
        <Link href="/admin/products" className="nav-link">
          Quản trị
        </Link>
      )}
      <span className="session-name">{session.firstName}</span>
      <button className="nav-link session-logout" type="button" onClick={handleLogout} disabled={loggingOut}>
        {loggingOut ? 'Đang thoát…' : 'Đăng xuất'}
      </button>
    </div>
  );
}
