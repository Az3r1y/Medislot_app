import { clinicSnapshot } from '../clinicRequest';
import { onBeforeUnmount, ref } from 'vue';
export function useLive() {
  const connected = ref(false);
  let stream: EventSource | null = null;
  function connect(path: string, onChange: () => void, event = 'slots-changed') {
    stream?.close();
    const clinic = clinicSnapshot();
    const query = clinic ? `?clinic=${encodeURIComponent(clinic.slug)}` : '';
    stream = new EventSource(path + query);
    const opened = stream;
    stream.addEventListener('ready', () => {
      if (stream !== opened) return;
      connected.value = true;
      onChange();
    });
    stream.addEventListener(event, () => {
      if (stream === opened) onChange();
    });
    stream.onerror = () => {
      if (stream === opened) connected.value = false;
    };
  }
  function close() {
    stream?.close();
    stream = null;
    connected.value = false;
  }
  onBeforeUnmount(close);
  return { connected, connect, close };
}
