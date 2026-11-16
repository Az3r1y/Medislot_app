<script setup lang="ts">
import { useClinic } from '../stores/clinic';
const clinic = useClinic();
defineProps<{ disabled: boolean }>();
const emit = defineEmits<{ error: [message: string] }>();
function change(event: Event) {
  const select = event.target as HTMLSelectElement;
  try {
    clinic.switchTo(select.value);
  } catch (error) {
    select.value = clinic.slug;
    emit('error', (error as Error).message);
  }
}
</script>
<template>
  <section
    class="clinic-banner"
    :style="{ borderColor: clinic.color }"
    aria-label="Cabinet courant"
  >
    <div>
      <strong>{{ clinic.current?.name ?? clinic.slug }}</strong><span>{{
        clinic.current?.city
      }}</span>
    </div>
    <div>
      <label for="clinic-select">Cabinet</label>
      <select id="clinic-select" :value="clinic.slug" :disabled="disabled" @change="change">
        <option v-for="item in clinic.available" :key="item.slug" :value="item.slug">
          {{ item.name }} · {{ item.city }}
        </option>
      </select>
    </div>
  </section>
</template>
