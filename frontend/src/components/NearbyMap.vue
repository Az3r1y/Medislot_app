<script setup lang="ts">
// La carte complète la liste, qui reste lisible sans elle.
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { RADIUS_KM, formatDistance, type Position } from '../geo';
import type { Professional } from '../types';
const props = defineProps<{ center: Position; professionals: Professional[] }>();
const el = ref<HTMLDivElement>();
let map: L.Map | null = null;
let layer: L.LayerGroup | null = null;
// bindTooltip(chaîne) insère du HTML : on passe un nœud texte.
function text(s: string): HTMLElement {
  const e = document.createElement('span');
  e.textContent = s;
  return e;
}

function draw() {
  if (!map || !layer) return;
  layer.clearLayers();
  const center: L.LatLngExpression = [props.center.lat, props.center.lon];
  const circle = L.circle(center, {
    radius: RADIUS_KM * 1000,
    color: '#096ca7',
    weight: 2,
    fillOpacity: 0.06,
  }).addTo(layer);
  L.circleMarker(center, { radius: 6, color: '#14213d', fillColor: '#14213d', fillOpacity: 1 })
    .bindTooltip(text(props.center.label))
    .addTo(layer);
  for (const p of props.professionals) {
    if (p.latitude == null || p.longitude == null) continue;
    L.circleMarker([p.latitude, p.longitude], {
      radius: 8,
      color: '#fff',
      weight: 2,
      fillColor: '#1d4ed8',
      fillOpacity: 1,
    })
      .bindTooltip(
        text(`${p.name}${p.distanceKm != null ? ' · ' + formatDistance(p.distanceKm) : ''}`),
      )
      .addTo(layer);
  }
  // Sans animation : un redessin pendant le zoom fait planter Leaflet (« _leaflet_pos »).
  map.fitBounds(circle.getBounds(), { padding: [8, 8], animate: false });
}

onMounted(() => {
  // Sans vue initiale, Leaflet n'ajoute pas les calques.
  map = L.map(el.value!, { scrollWheelZoom: false }).setView(
    [props.center.lat, props.center.lon],
    13,
  );
  // Politique d'usage d'OpenStreetMap : attribution obligatoire, pas de préchargement.
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution:
      '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>',
  }).addTo(map);
  layer = L.layerGroup().addTo(map);
  draw();
});
watch(() => [props.center, props.professionals], draw);
onBeforeUnmount(() => {
  map?.stop();
  map?.remove();
  map = null;
  layer = null;
});
</script>

<template>
  <figure class="nearby-map">
    <div
      ref="el"
      class="nearby-map-canvas"
      role="region"
      :aria-label="`Carte : ${professionals.length} praticiens dans un rayon de 5 km`"
    ></div>
    <figcaption>
      Cercle de 5 km à vol d’oiseau. Les mêmes praticiens sont listés ci-dessous.
    </figcaption>
  </figure>
</template>

<style scoped>
.nearby-map {
  margin: 0 0 20px;
}
.nearby-map-canvas {
  height: 320px;
  border-radius: 12px;
  border: 1px solid #d9dee8;
}
.nearby-map figcaption {
  font-size: 13px;
  color: #5b6475;
  margin-top: 6px;
}
</style>
