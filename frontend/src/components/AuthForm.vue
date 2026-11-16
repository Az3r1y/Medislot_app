<script setup lang="ts">
const email = defineModel<string>('email', { required: true });
const password = defineModel<string>('password', { required: true });
const name = defineModel<string>('name', { required: true });
defineProps<{
  register: boolean;
  busy: boolean;
  authError: string;
  fieldErrors: Record<string, string>;
}>();
const emit = defineEmits<{ submit: []; toggleMode: [] }>();
// Erreurs par champ : aria-invalid et message relié au champ (RGAA 11.10, 11.11).
const describedBy = (...ids: (string | false)[]) => ids.filter(Boolean).join(' ') || undefined;
</script>

<template>
  <p class="eyebrow dark">VOTRE ESPACE MEDISLOT</p>
  <h2 id="modal-title" tabindex="-1">
    {{ register ? 'Créer votre compte' : 'Heureux de vous retrouver' }}
  </h2>
  <p class="subtle">Connectez-vous pour confirmer et retrouver vos rendez-vous.</p>
  <!-- novalidate : la règle et son message viennent du 400 du back, affichés sous le champ -->
  <form class="auth-form" novalidate @submit.prevent="emit('submit')">
    <p class="subtle">Tous les champs sont obligatoires.</p>
    <div v-if="register" class="field">
      <label for="auth-name">Votre nom</label
      ><input
        id="auth-name"
        v-model="name"
        required
        maxlength="120"
        autocomplete="name"
        :aria-invalid="!!fieldErrors.name"
        :aria-describedby="describedBy(!!fieldErrors.name && 'auth-name-error')"
      />
      <p v-if="fieldErrors.name" id="auth-name-error" class="error-text">
        Erreur : {{ fieldErrors.name }}
      </p>
    </div>
    <div class="field">
      <label for="auth-email">Adresse e-mail</label
      ><input
        id="auth-email"
        v-model="email"
        type="email"
        required
        autocomplete="email"
        :aria-invalid="!!fieldErrors.email"
        :aria-describedby="describedBy(!!fieldErrors.email && 'auth-email-error')"
      />
      <p v-if="fieldErrors.email" id="auth-email-error" class="error-text">
        Erreur : {{ fieldErrors.email }}
      </p>
    </div>
    <div class="field">
      <label for="auth-password">Mot de passe</label>
      <p v-if="register" id="auth-password-hint" class="hint">12 caractères au moins.</p>
      <input
        id="auth-password"
        v-model="password"
        type="password"
        required
        maxlength="72"
        :autocomplete="register ? 'new-password' : 'current-password'"
        :aria-invalid="!!fieldErrors.password"
        :aria-describedby="
          describedBy(
            register && 'auth-password-hint',
            !!fieldErrors.password && 'auth-password-error',
          )
        "
      />
      <p v-if="fieldErrors.password" id="auth-password-error" class="error-text">
        Erreur : {{ fieldErrors.password }}
      </p>
    </div>
    <div role="alert">
      <p v-if="authError" class="error-text">{{ authError }}</p>
    </div>
    <button class="primary" :aria-disabled="busy">
      {{ busy ? 'Connexion…' : register ? 'Créer mon compte' : 'Se connecter' }}
    </button>
  </form>
  <button class="text-button" @click="emit('toggleMode')">
    {{ register ? 'J’ai déjà un compte' : 'Créer un compte' }}
  </button>
  <details class="demo-credentials">
    <summary>Compte de démonstration</summary>
    <p>patient@medislot.test<br />MediSlot-demo-2026!</p>
  </details>
</template>
