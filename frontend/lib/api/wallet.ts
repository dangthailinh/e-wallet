import { ApiError, apiRequest } from './client';

export type WalletView = {
  walletId: number;
  balanceVnd: number;
  currency: 'VND';
};

export type WalletEntryView = {
  id: string;
  direction: 'CREDIT' | 'DEBIT';
  type: 'OPENING_BALANCE' | 'DEPOSIT' | 'PURCHASE' | 'REFUND' | 'TRANSFER_IN' | 'TRANSFER_OUT' | 'ADJUSTMENT';
  amountVnd: number;
  balanceAfterVnd: number;
  businessLabel: string;
  createdAt: string;
};

export type WalletEntriesPage = {
  items: WalletEntryView[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export async function getCurrentWallet(): Promise<WalletView> {
  const wallet = await apiRequest<WalletView>('/wallet');
  assertSafeVnd(wallet.balanceVnd, true);
  return wallet;
}

export async function getWalletEntries(page = 0, size = 5): Promise<WalletEntriesPage> {
  const entries = await apiRequest<WalletEntriesPage>(`/wallet/entries?page=${page}&size=${size}`);
  entries.items.forEach((entry) => {
    assertSafeVnd(entry.amountVnd, false);
    assertSafeVnd(entry.balanceAfterVnd, true);
  });
  return entries;
}

function assertSafeVnd(amount: number, allowZero: boolean): void {
  if (!Number.isSafeInteger(amount) || amount < (allowZero ? 0 : 1)) {
    throw new ApiError(0, { code: 'WALLET_AMOUNT_UNSAFE', detail: 'Wallet amount cannot be displayed accurately.' });
  }
}
