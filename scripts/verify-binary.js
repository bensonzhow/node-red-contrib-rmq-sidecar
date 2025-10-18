const fs = require('fs');
const path = require('path');
const plat = process.platform === 'win32' ? 'windows' : 'linux';
const bin = path.join(__dirname, '..', 'sidecar', plat, process.platform==='win32'?'rocketmq-sidecar.exe':'rocketmq-sidecar');
if (!fs.existsSync(bin)) {
  console.error(`[ERR] binary not found: ${bin}`);
  process.exit(1);
}
console.log('[OK] binary exists:', bin);