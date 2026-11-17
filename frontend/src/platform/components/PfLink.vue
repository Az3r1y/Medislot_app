<script setup lang="ts">
import { follow } from '../router';
defineProps<{ to: string }>();
</script>
<template>
  <a :href="to" @click="follow($event, to)"><slot /></a>
</template>
