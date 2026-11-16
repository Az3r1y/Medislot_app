import { context, propagation, trace } from '@opentelemetry/api';
import { W3CTraceContextPropagator } from '@opentelemetry/core';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { registerInstrumentations } from '@opentelemetry/instrumentation';
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch';
import { resourceFromAttributes } from '@opentelemetry/resources';
import {
  BatchSpanProcessor,
  TracerProvider,
  StackContextManager,
  type Span,
  type SpanProcessor,
} from '@opentelemetry/sdk-trace';

// Retire la chaîne de requête (saisies du patient) avant l'export.
// Le span n'est plus modifiable après onStart.
const stripQueryString: SpanProcessor = {
  onStart(span: Span) {
    for (const key of ['url.full', 'http.url']) {
      const value = span.attributes[key];
      if (typeof value === 'string' && value.includes('?'))
        span.setAttribute(key, value.split('?')[0]);
    }
  },
  onEnd() {},
  forceFlush: async () => {},
  shutdown: async () => {},
};

/** `processeur` remplace l'export par lots (utilisé par les tests). */
export function demarrerTraces({ processeur }: { processeur?: SpanProcessor } = {}) {
  const provider = new TracerProvider({
    resource: resourceFromAttributes({
      'service.name': 'medislot-web',
      'service.version': '1.0.0',
    }),
    // /otlp/ est sur la même origine : Nginx relaie au collecteur, pas de CORS.
    spanProcessors: [
      stripQueryString,
      processeur ??
        new BatchSpanProcessor({ exporter: new OTLPTraceExporter({ url: '/otlp/v1/traces' }) }),
    ],
  });
  // Sans gestionnaire de contexte ni propagateur, l'en-tête traceparent ne part pas.
  context.setGlobalContextManager(new StackContextManager().enable());
  propagation.setGlobalPropagator(new W3CTraceContextPropagator());
  trace.setGlobalTracerProvider(provider);
  // Ne pas tracer l'export lui-même.
  registerInstrumentations({
    instrumentations: [new FetchInstrumentation({ ignoreUrls: [/\/otlp\//] })],
  });
  return provider;
}
