// Démarre un service AVEC l'agent OpenTelemetry :
//   node observabilite/demarrer-observe.mjs parc
//   node observabilite/demarrer-observe.mjs locations
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const racine = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const agent = path.join(racine, 'opentelemetry-javaagent.jar');
const [nom, ...reste] = process.argv.slice(2);

if (!['parc', 'locations'].includes(nom)) {
    console.error('usage : node observabilite/demarrer-observe.mjs <parc|locations>');
    process.exit(2);
}
if (!fs.existsSync(agent)) {
    console.error(`agent absent : ${agent}\n    node resources/telecharger-agent.mjs`);
    process.exit(1);
}

const jvm = [
    `-javaagent:${agent}`,
    `-Dotel.service.name=${nom}`,
    '-Dotel.exporter.otlp.endpoint=http://localhost:4318',
    '-Dotel.exporter.otlp.protocol=http/protobuf',
    '-Dotel.logs.exporter=none',
    '-Dotel.metric.export.interval=5000',
    '-Dotel.instrumentation.logback-mdc.enabled=true',
    ...reste,
];

const p = spawn(
    process.execPath,
    [path.join(racine, 'scripts', 'demarrer.mjs'), nom, ...jvm],
    { stdio: 'inherit', cwd: racine },
);
p.on('exit', (code, signal) => process.exit(signal ? 0 : code ?? 0));