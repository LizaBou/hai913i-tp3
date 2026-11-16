// Montre les frontières de processus d'une trace : le même TraceId partout,
// et pour chaque span dont le parent appartient à un autre service,
// le ParentSpanId qui désigne ce parent.
//   node observabilite/frontieres.mjs --trace <TraceId>
const args = process.argv.slice(2);
const option = (nom, defaut) => {
  const i = args.indexOf(nom);
  return i !== -1 && args[i + 1] ? args[i + 1] : defaut;
};
const jaeger = option('--jaeger', 'http://localhost:16686');
const id = option('--trace', null);
if (!id) {
  console.error('usage : node observabilite/frontieres.mjs --trace <TraceId>');
  process.exit(2);
}

const r = await fetch(`${jaeger}/api/v3/traces/${id}`);
if (!r.ok) {
  console.error(`Jaeger a répondu ${r.status}`);
  process.exit(1);
}
const corps = await r.json();
const spans = [];
for (const lot of Array.isArray(corps) ? corps : [corps]) {
  for (const rs of lot?.result?.resourceSpans ?? []) {
    const service = (rs.resource?.attributes ?? []).find((a) => a.key === 'service.name')
      ?.value?.stringValue;
    for (const ss of rs.scopeSpans ?? []) {
      for (const s of ss.spans ?? []) {
        spans.push({ service, trace: s.traceId, span: s.spanId, parent: s.parentSpanId || null, nom: s.name });
      }
    }
  }
}

const traces = [...new Set(spans.map((s) => s.trace))];
const services = [...new Set(spans.map((s) => s.service))];
console.log(`TraceId  : ${traces.join(', ')}  (${traces.length === 1 ? 'un seul' : 'PLUSIEURS'})`);
console.log(`spans    : ${spans.length}`);
console.log(`services : ${services.join(', ')}`);
console.log('');
console.log('frontières de processus :');
for (const enfant of spans) {
  const parent = spans.find((p) => p.span === enfant.parent);
  if (parent && parent.service !== enfant.service) {
    console.log(`  ${parent.service} : ${parent.nom}  [${parent.span}]`);
    console.log(`    -> ${enfant.service} : ${enfant.nom}  [${enfant.span}]  ParentSpanId = ${enfant.parent}`);
  }
}
