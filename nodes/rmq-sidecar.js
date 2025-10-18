module.exports = function(RED) {
  const { spawn } = require('child_process');
  const path = require('path');
  const http = require('http');
  const getPort = require('get-port');

  let proc = null;
  let baseUrl = null;
  let stopping = false;

  async function waitForHealthy(url, timeoutMs) {
    const deadline = Date.now() + timeoutMs;
    return new Promise((resolve, reject) => {
      (function ping() {
        http.get(url + '/actuator/health', res => {
          if (res.statusCode === 200) resolve();
          else (Date.now() < deadline) ? setTimeout(ping, 400) : reject(new Error('health non-200'));
        }).on('error', () => {
          (Date.now() < deadline) ? setTimeout(ping, 400) : reject(new Error('health timeout'));
        });
      })();
    });
  }

  function pickBinary() {
    const dir = path.join(__dirname, '..', 'sidecar');
    if (process.platform === 'win32') return path.join(dir, 'windows', 'rocketmq-sidecar.exe');
    return path.join(dir, 'linux', 'rocketmq-sidecar');
  }

  async function startSidecar(node, port) {
    if (proc && !proc.killed) return baseUrl;
    const bin = pickBinary();
    const args = [`--server.port=${port}`, `--server.address=127.0.0.1`];

    proc = spawn(bin, args, { stdio: ['ignore', 'ignore', 'pipe'], windowsHide: true });
    proc.stderr.on('data', d => node.debug && node.debug(d.toString()));
    proc.on('exit', code => { if (!stopping) node.warn(`sidecar exited code=${code}`); proc = null; });

    baseUrl = `http://127.0.0.1:${port}`;
    await waitForHealthy(baseUrl, 15000);
    return baseUrl;
  }

  function RmqSidecar(config) {
    RED.nodes.createNode(this, config);
    const node = this;

    node.on('input', async (msg, send, done) => {
      try {
        const port = config.port || await getPort({ port: 18080 });
        const url = await startSidecar(node, port);
        node.status({ fill: 'green', shape: 'dot', text: `sidecar ${url}` });
        msg.payload = { ok: true, baseUrl: url };
        send(msg); done();
      } catch (e) {
        node.status({ fill: 'red', shape: 'ring', text: 'sidecar start failed' });
        msg.payload = { ok: false, error: e.message };
        send(msg); done();
      }
    });

    node.on('close', async (removed, done) => {
      try { stopping = true; if (proc && !proc.killed) proc.kill('SIGTERM'); } catch (_) {}
      setTimeout(done, 200);
    });
  }

  RED.nodes.registerType('rmq-sidecar', RmqSidecar);
};