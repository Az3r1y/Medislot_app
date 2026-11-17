<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { platformApi } from '../platformApi';
import { messageOf, useConsole } from '../context';
import { actionLabels, dateLabel, label, timeLabel } from '../format';
import PfIcon from '../components/PfIcon.vue';
import PfLink from '../components/PfLink.vue';
import type { MasterEntry, MasterPage } from '../types';

// Journal master : chaque action de l'équipe plateforme, en ajout seul.
const { fail } = useConsole();
const items = ref<MasterEntry[]>([]),
  next = ref<number | null>(null),
  loading = ref(false),
  error = ref('');

async function load(more = false) {
  loading.value = true;
  error.value = '';
  try {
    const page = await platformApi<MasterPage>(
      `/audit${more && next.value ? `?cursor=${next.value}` : ''}`,
    );
    items.value = more ? [...items.value, ...page.items] : page.items;
    next.value = page.nextCursor;
  } catch (e) {
    if (!fail(e)) error.value = messageOf(e);
  } finally {
    loading.value = false;
  }
}
onMounted(() => load());
</script>
<template>
  <div class="pf-page">
    <header class="pf-page-head">
      <div class="pf-row">
        <h1 class="pf-title">Journal master</h1>
        <span class="pf-tag"><PfIcon name="lock" :size="13" />Ajout seul</span>
      </div>
    </header>
    <p v-if="error" class="pf-alert pf-alert-error" role="alert">
      <PfIcon name="alert" /><span>{{ error }}</span>
    </p>
    <div class="pf-card pf-table-wrap" :aria-busy="loading">
      <table class="pf-table">
        <thead>
          <tr>
            <th scope="col">Date et heure</th>
            <th scope="col">Auteur</th>
            <th scope="col">Action</th>
            <th scope="col">Cabinet</th>
            <th scope="col">Détail</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && !items.length">
            <td colspan="5" class="pf-empty-cell">Aucune action enregistrée.</td>
          </tr>
          <tr v-for="e in items" :key="e.id">
            <td>
              <span class="pf-stack"
                ><strong class="pf-num">{{ dateLabel(e.at) }}</strong
                ><span class="pf-muted pf-num">{{ timeLabel(e.at) }}</span></span
              >
            </td>
            <td>{{ e.actor }}</td>
            <td>{{ label(actionLabels, e.action) }}</td>
            <td>
              <PfLink
                v-if="e.clinic && e.action !== 'CLINIC_DELETED'"
                :to="`/platform/clinics/${e.clinic}`"
                class="pf-mono"
                >{{ e.clinic }}</PfLink
              >
              <span v-else class="pf-mono">{{ e.clinic ?? '—' }}</span>
            </td>
            <td>{{ e.target ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="next" class="pf-pagination">
        <span />
        <button
          type="button"
          class="pf-button pf-small-button"
          :disabled="loading"
          @click="load(true)"
        >
          Afficher plus
        </button>
      </div>
    </div>
  </div>
</template>
