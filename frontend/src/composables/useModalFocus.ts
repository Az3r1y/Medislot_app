import { nextTick, ref, watch, type Ref } from 'vue';

const TITLES = {
  search: 'Trouver un praticien · MediSlot',
  appointments: 'Mes rendez-vous · MediSlot',
};

/**
 * Focus clavier de la fenêtre modale et des vues (RGAA 8.6, 12.7) : à l'ouverture, focus sur le h2 ;
 * à la fermeture, retour à l'élément d'origine, sinon au h1. Chaque vue a son titre d'onglet.
 */
export function useModalFocus(
  view: Ref<'search' | 'appointments'>,
  openKind: Ref<string | null>,
  onEscape: () => void,
) {
  const modal = ref<HTMLElement | null>(null);
  let priorFocus: HTMLElement | null = null;
  const focusTitle = () => document.querySelector<HTMLElement>('main h1')?.focus();

  document.title = TITLES[view.value];
  watch(view, async (v) => {
    document.title = TITLES[v];
    await nextTick();
    focusTitle();
  });

  watch(openKind, async (kind, old) => {
    if (kind && !old) priorFocus = document.activeElement as HTMLElement;
    await nextTick();
    if (kind) modal.value?.querySelector<HTMLElement>('h2')?.focus();
    else if (priorFocus?.isConnected) priorFocus.focus();
    else focusTitle();
  });

  /** Échap ferme ; Tab reste dans la fenêtre. */
  function trap(e: KeyboardEvent) {
    if (e.key === 'Escape') onEscape();
    if (e.key === 'Tab' && modal.value) {
      const focusable = [
        ...modal.value.querySelectorAll<HTMLElement>(
          'button:not([disabled]),input,select,a[href],summary',
        ),
      ];
      const first = focusable[0],
        last = focusable[focusable.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last?.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first?.focus();
      }
    }
  }

  /** Après une fermeture forcée (créneau pris), le focus ne doit pas rester sur un élément disparu. */
  function refocusIfLost() {
    if (!priorFocus?.isConnected) focusTitle();
  }

  return { modal, focusTitle, trap, refocusIfLost };
}
