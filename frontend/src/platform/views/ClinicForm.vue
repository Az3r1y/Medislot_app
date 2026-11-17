<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { ApiError } from '../../api';
import { body, platformApi } from '../platformApi';
import { messageOf, useConsole } from '../context';
import { COLOR, inkOn, logoError, slugError, suggestSlug } from '../format';
import { navigate } from '../router';
import PfIcon from '../components/PfIcon.vue';
import PfLink from '../components/PfLink.vue';
import type { ClinicDetail, ClinicInput, ClinicRow, Created } from '../types';

// Un même formulaire pour la création et la modification. L'identifiant ne change plus après la
// création.
const props = defineProps<{ slug?: string }>();
const { fail, notify } = useConsole();
const editing = computed(() => !!props.slug);
const form = reactive<ClinicInput>({
  name: '',
  slug: '',
  city: '',
  address: '',
  color: '#0873b9',
  adminEmail: '',
});
const errors = ref<Record<string, string>>({}),
  error = ref(''),
  busy = ref(false),
  loading = ref(false),
  slugTouched = ref(false),
  logo = ref<File | null>(null),
  logoUrl = ref(''),
  existingLogo = ref(false),
  created = ref<Created | null>(null);

onMounted(async () => {
  if (!props.slug) return;
  loading.value = true;
  try {
    const detail = await platformApi<ClinicDetail>(`/clinics/${props.slug}`);
    Object.assign(form, {
      name: detail.clinic.name,
      slug: detail.clinic.slug,
      city: detail.clinic.city,
      address: detail.clinic.address ?? '',
      color: detail.clinic.color,
      adminEmail: detail.adminEmail ?? '',
    });
    existingLogo.value = detail.clinic.hasLogo;
  } catch (e) {
    if (!fail(e)) error.value = messageOf(e);
  } finally {
    loading.value = false;
  }
});
onBeforeUnmount(() => logoUrl.value && URL.revokeObjectURL(logoUrl.value));

// Proposition d'identifiant tant que la personne ne l'a pas modifié elle-même.
watch(
  () => form.name,
  (name) => {
    if (!editing.value && !slugTouched.value) form.slug = suggestSlug(name);
  },
);

function chooseLogo(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null;
  const problem = logoError(file);
  errors.value = { ...errors.value, logo: problem };
  if (logoUrl.value) URL.revokeObjectURL(logoUrl.value);
  logo.value = problem ? null : file;
  logoUrl.value = logo.value ? URL.createObjectURL(logo.value) : '';
}

// Contrôlé des deux côtés ; le back reste l'arbitre (unicité en base).
function validate(): boolean {
  const e: Record<string, string> = {};
  if (!form.name.trim()) e.name = 'Saisissez le nom du cabinet.';
  if (!editing.value && slugError(form.slug)) e.slug = slugError(form.slug);
  if (!form.city.trim()) e.city = 'Saisissez la ville.';
  if (!COLOR.test(form.color)) e.color = 'Couleur au format #RRGGBB.';
  if (!editing.value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.adminEmail.trim()))
    e.adminEmail = 'Saisissez une adresse e-mail valide.';
  if (errors.value.logo) e.logo = errors.value.logo;
  errors.value = e;
  return !Object.values(e).some(Boolean);
}

async function submit() {
  if (busy.value || !validate()) {
    document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
    return;
  }
  busy.value = true;
  error.value = '';
  try {
    let clinic: ClinicRow;
    if (editing.value) {
      clinic = await platformApi<ClinicRow>(`/clinics/${props.slug}`, {
        method: 'PUT',
        body: body({
          name: form.name,
          city: form.city,
          address: form.address,
          color: form.color,
        }),
      });
    } else {
      const result = await platformApi<Created>('/clinics', { method: 'POST', body: body(form) });
      clinic = result.clinic;
      created.value = result;
    }
    if (logo.value)
      await platformApi(`/clinics/${clinic.slug}/logo`, {
        method: 'PUT',
        headers: { 'Content-Type': logo.value.type },
        body: logo.value,
      });
    if (editing.value) {
      notify('Les modifications du cabinet sont enregistrées.');
      navigate(`/platform/clinics/${clinic.slug}`);
    }
  } catch (e) {
    if (e instanceof ApiError && Object.keys(e.fields).length) {
      errors.value = e.fields;
      error.value = created.value ? '' : e.message;
    } else if (!fail(e)) error.value = messageOf(e);
  } finally {
    busy.value = false;
  }
}

const previewUrl = computed(() =>
  logoUrl.value
    ? logoUrl.value
    : editing.value && existingLogo.value
      ? `/api/platform/clinics/${props.slug}/logo`
      : '',
);
const invalid = (field: string) => (errors.value[field] ? 'true' : undefined);
</script>
<template>
  <div class="pf-page">
    <nav class="pf-crumb" aria-label="Fil d’Ariane">
      <PfLink to="/platform/clinics">Cabinets</PfLink>
      <PfIcon name="right" :size="14" />
      <template v-if="editing">
        <PfLink :to="`/platform/clinics/${slug}`">{{ form.name || slug }}</PfLink>
        <PfIcon name="right" :size="14" />
        <span aria-current="page">Modifier</span>
      </template>
      <span v-else aria-current="page">Créer un cabinet</span>
    </nav>

    <section v-if="created" class="pf-card pf-success" aria-labelledby="created-title">
      <span class="pf-success-badge"><PfIcon name="check" :size="22" /></span>
      <div>
        <h1 id="created-title" class="pf-title">Cabinet « {{ created.clinic.name }} » créé</h1>
        <p>
          Le compte administrateur <strong>{{ created.adminEmail }}</strong> est prêt. Mot de passe
          provisoire, affiché une seule fois, à transmettre par un canal sûr :
        </p>
        <p class="pf-secret pf-mono">{{ created.temporaryPassword }}</p>
        <div class="pf-row">
          <PfLink :to="`/platform/clinics/${created.clinic.slug}`" class="pf-button pf-primary"
            >Ouvrir la fiche du cabinet</PfLink
          >
          <PfLink to="/platform/clinics" class="pf-button">Liste des cabinets</PfLink>
        </div>
      </div>
    </section>

    <template v-else>
      <h1 class="pf-title">
        {{ editing ? `Modifier · ${form.name || slug}` : 'Nouveau cabinet' }}
      </h1>
      <p v-if="error" class="pf-alert pf-alert-error" role="alert">
        <PfIcon name="alert" /><span>{{ error }}</span>
      </p>
      <div class="pf-form-layout">
        <form class="pf-card pf-form" novalidate :aria-busy="loading" @submit.prevent="submit">
          <fieldset class="pf-section">
            <legend><span class="pf-step">1</span>Identité</legend>
            <div class="pf-grid2">
              <label class="pf-field">
                <span>Nom du cabinet <abbr title="obligatoire">*</abbr></span>
                <input
                  v-model="form.name"
                  class="pf-input"
                  maxlength="120"
                  required
                  :aria-invalid="invalid('name')"
                  aria-describedby="name-error"
                />
                <span v-if="errors.name" id="name-error" class="pf-field-error">{{
                  errors.name
                }}</span>
              </label>
              <label class="pf-field">
                <span
                  >Identifiant (dans l’URL) <abbr title="obligatoire">*</abbr>
                  <PfIcon v-if="editing" name="lock" :size="14"
                /></span>
                <span
                  class="pf-prefixed"
                  :class="{ 'pf-invalid': errors.slug, 'pf-readonly': editing }"
                >
                  <span class="pf-prefix">/c/</span>
                  <input
                    v-model="form.slug"
                    class="pf-mono"
                    maxlength="40"
                    :readonly="editing"
                    autocomplete="off"
                    spellcheck="false"
                    :aria-invalid="invalid('slug')"
                    aria-describedby="slug-help"
                    @input="slugTouched = true"
                  />
                </span>
                <span v-if="errors.slug" id="slug-help" class="pf-field-error" role="alert">{{
                  errors.slug
                }}</span>
                <span v-else-if="editing" id="slug-help" class="pf-help"
                  >L’identifiant ne change plus après la création.</span
                >
                <span v-else id="slug-help" class="pf-help"
                  >Minuscules, chiffres et tirets · 3 à 40 caractères</span
                >
              </label>
              <div class="pf-field">
                <label for="clinic-color">Couleur</label>
                <span class="pf-row">
                  <input
                    v-model="form.color"
                    class="pf-swatch"
                    type="color"
                    aria-label="Choisir la couleur"
                  />
                  <input
                    id="clinic-color"
                    v-model="form.color"
                    class="pf-input pf-mono"
                    maxlength="7"
                    :aria-invalid="invalid('color')"
                    aria-describedby="color-error"
                  />
                </span>
                <span v-if="errors.color" id="color-error" class="pf-field-error">{{
                  errors.color
                }}</span>
              </div>
              <label class="pf-field">
                <span>Logo</span>
                <span class="pf-drop"
                  ><PfIcon name="upload" />{{ logo ? logo.name : 'Choisir un fichier…' }}
                  <input
                    class="pf-visually-hidden"
                    type="file"
                    accept="image/png,image/svg+xml"
                    :aria-invalid="invalid('logo')"
                    aria-describedby="logo-help"
                    @change="chooseLogo"
                /></span>
                <span v-if="errors.logo" id="logo-help" class="pf-field-error">{{
                  errors.logo
                }}</span>
                <span v-else id="logo-help" class="pf-help">PNG ou SVG, 200 Ko max</span>
              </label>
            </div>
          </fieldset>
          <fieldset class="pf-section">
            <legend><span class="pf-step">2</span>Localisation</legend>
            <div class="pf-grid2">
              <label class="pf-field">
                <span>Ville <abbr title="obligatoire">*</abbr></span>
                <input
                  v-model="form.city"
                  class="pf-input"
                  maxlength="80"
                  required
                  :aria-invalid="invalid('city')"
                  aria-describedby="city-error"
                />
                <span v-if="errors.city" id="city-error" class="pf-field-error">{{
                  errors.city
                }}</span>
              </label>
              <label class="pf-field">
                <span>Adresse</span>
                <input
                  v-model="form.address"
                  class="pf-input"
                  maxlength="200"
                  autocomplete="address-line1"
                />
              </label>
            </div>
          </fieldset>
          <fieldset class="pf-section">
            <legend><span class="pf-step">3</span>Premier administrateur</legend>
            <div class="pf-grid2">
              <label class="pf-field">
                <span
                  >E-mail du premier administrateur du cabinet
                  <abbr v-if="!editing" title="obligatoire">*</abbr></span
                >
                <input
                  v-model="form.adminEmail"
                  class="pf-input"
                  type="email"
                  maxlength="254"
                  :readonly="editing"
                  :aria-invalid="invalid('adminEmail')"
                  :aria-describedby="errors.adminEmail ? 'admin-error' : 'admin-help'"
                />
                <span v-if="errors.adminEmail" id="admin-error" class="pf-field-error">{{
                  errors.adminEmail
                }}</span>
              </label>
              <p v-if="!errors.adminEmail" id="admin-help" class="pf-help pf-align-end">
                {{
                  editing
                    ? 'Le compte administrateur se gère ensuite depuis le cabinet.'
                    : 'Créé dans la même transaction que le cabinet, avec un mot de passe provisoire.'
                }}
              </p>
            </div>
          </fieldset>
          <div class="pf-form-foot">
            <span class="pf-help"><abbr title="obligatoire">*</abbr> champ obligatoire</span>
            <span class="pf-row">
              <PfLink
                :to="editing ? `/platform/clinics/${slug}` : '/platform/clinics'"
                class="pf-button"
                >Annuler</PfLink
              >
              <button type="submit" class="pf-button pf-primary" :disabled="busy || loading">
                {{
                  busy
                    ? 'Enregistrement…'
                    : editing
                      ? 'Enregistrer les modifications'
                      : 'Créer le cabinet'
                }}
              </button>
            </span>
          </div>
        </form>
        <aside class="pf-card pf-preview" aria-label="Aperçu">
          <span class="pf-label">Aperçu</span>
          <img v-if="previewUrl" class="pf-tile pf-tile-lg" :src="previewUrl" alt="" />
          <span
            v-else
            class="pf-tile pf-tile-lg"
            :style="{
              background: COLOR.test(form.color) ? form.color : '#0873b9',
              color: inkOn(form.color),
            }"
            aria-hidden="true"
            >{{ (form.name || '?').charAt(0) }}</span
          >
          <strong>{{ form.name || 'Nom du cabinet' }}</strong>
          <span class="pf-mono pf-muted">/c/{{ form.slug || 'identifiant' }}/</span>
          <span class="pf-muted">{{ [form.city, form.address].filter(Boolean).join(' · ') }}</span>
          <hr />
          <span class="pf-label">{{
            editing ? 'Ce qui sera enregistré' : 'Ce qui sera créé'
          }}</span>
          <ul class="pf-checklist">
            <li>
              <PfIcon name="check" />{{
                editing ? 'Les informations du cabinet' : 'Le cabinet et son identifiant'
              }}
            </li>
            <li v-if="!editing"><PfIcon name="check" />Le compte du premier administrateur</li>
            <li><PfIcon name="check" />Une ligne dans le journal master</li>
          </ul>
        </aside>
      </div>
    </template>
  </div>
</template>
