import { useClinic } from './clinic';
import { defineStore } from 'pinia';
import { ref } from 'vue';
import { api, refreshCsrf, ApiError } from '../api';
import type { Identity, Providers } from '../types';
export const useSession = defineStore('session', () => {
  const user = ref<Identity | null>(null);
  const providers = ref<Providers>({ google: false });
  async function load() {
    try {
      const identity = await api<Identity>('/me');
      await useClinic().afterLogin();
      user.value = identity;
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) user.value = null;
      else throw e;
    }
  }
  // en cas d'échec, seul le mot de passe est proposé
  async function loadProviders() {
    try {
      providers.value = await api<Providers>('/auth/providers');
    } catch {
      providers.value = { google: false };
    }
  }
  async function login(email: string, password: string) {
    await refreshCsrf();
    await api('/login', {
      method: 'POST',
      body: new URLSearchParams({ username: email, password }),
    });
    await refreshCsrf();
    await load();
  }
  async function logout() {
    await api('/logout', { method: 'POST' });
    user.value = null;
    useClinic().afterLogout();
    await refreshCsrf();
  }
  return { user, providers, load, loadProviders, login, logout };
});
