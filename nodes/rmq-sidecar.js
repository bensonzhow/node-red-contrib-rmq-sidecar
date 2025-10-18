module.exports = function(RED) {
  const { spawn } = require('child_process');
  const path = require('path');
  const fs = require('fs');
  const http = require('http');
  const getPort = require('get-port');

  let proc = null;
  let baseUrl = null;
  let stopping = false;

  async function waitForHealthy(url, timeoutMs) {
    const deadline = Date.now() + timeoutMs;
    return new Promise((resolve, reject) => {
      let timer = null;
      const ping = () => {
        const req = http.get(url + '/actuator/health', res => {
          if (res.statusCode === 200) {
            if (timer) clearTimeout(timer);
            return resolve();
          }
          schedule();
        });
        req.on('error', schedule);
        req.setTimeout(3000, () => { try { req.destroy(); } catch(_){}; schedule(); });
      };
      const schedule = () => {
        if (Date.now() >= deadline) return reject(new Error('health timeout'));
        timer = setTimeout(ping, 400);
      };
      ping();
    });
  }

  function pickBinary() {
    const dir = path.join(__dirname, '..', 'sidecar');
    const winExe = path.join(dir, 'windows', 'rocketmq-sidecar.exe');
    const linElf = path.join(dir, 'linux', 'rocketmq-sidecar');

    // Prefer native binaries by platform
    if (process.platform === 'win32' && fs.existsSync(winExe)) {
      return { cmd: winExe, baseArgs: [] };
    }
    if (process.platform !== 'win32' && fs.existsSync(linElf)) {
      return { cmd: linElf, baseArgs: [] };
    }

    // Fallback to jar if native not found
    const jarCandidates = [
      path.join(dir, 'rocketmq-sidecar-1.0.0.jar'),
      path.join(__dirname, '..', 'sidecar-src', 'target', 'rocketmq-sidecar-1.0.0.jar'),
      path.join(dir, 'rocketmq-sidecar.jar')
    ];
    const jar = jarCandidates.find(p => fs.existsSync(p));
    if (jar) {
      const javaCmd = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
      return { cmd: javaCmd, baseArgs: ['-jar', jar] };
    }

    // Nothing found → return null for the caller to handle
    return null;
  }

  let startPromise = null;
  async function startSidecar(node, port) {
    if (proc && !proc.killed) return baseUrl;
    if (startPromise) return startPromise; // de-duplicate concurrent starts

    startPromise = (async () => {
      const launcher = pickBinary();
      if (!launcher) {
        throw new Error('sidecar binary/jar not found. Please place windows/rocketmq-sidecar.exe or linux/rocketmq-sidecar or rocketmq-sidecar-1.0.0.jar under sidecar/.');
      }
      const args = [...launcher.baseArgs, `--server.port=${port}`, `--server.address=127.0.0.1`];

      return await new Promise((resolve, reject) => {
        try {
          proc = spawn(launcher.cmd, args, { stdio: ['ignore', 'ignore', 'pipe'], windowsHide: true });
        } catch (spawnErr) {
          startPromise = null;
          return reject(new Error(`failed to spawn sidecar: ${spawnErr.message}`));
        }

        const onError = (err) => {
          // Prevent Node-RED crash on ENOENT or spawn error
          node.warn && node.warn(`[rmq-sidecar] spawn error: ${err.message}`);
        };
        proc.on('error', onError);

        proc.stderr.on('data', d => {
          // downgrade noisy stderr to debug to avoid log floods
          if (node && typeof node.debug === 'function') node.debug(d.toString());
        });

        proc.on('exit', code => {
          proc = null;
          startPromise = null;
          if (!stopping) {
            node.warn && node.warn(`[rmq-sidecar] exited with code=${code}`);
          }
        });

        baseUrl = `http://127.0.0.1:${port}`;
        waitForHealthy(baseUrl, 15000).then(() => {
          startPromise = null;
          resolve(baseUrl);
        }).catch((e) => {
          // If health check fails, kill the child and surface a clean error
          try { if (proc && !proc.killed) proc.kill('SIGTERM'); } catch (_) {}
          proc = null;
          startPromise = null;
          reject(new Error(`sidecar health check failed: ${e.message}`));
        });
      });
    })();

    return startPromise;
  }

  function RmqSidecar(config) {
    RED.nodes.createNode(this, config);
    const node = this;

    node.on('input', async (msg, send, done) => {
      const cleanup = () => { if (typeof done === 'function') done(); };
      try {
        const configured = parseInt(config.port, 10);
        const port = Number.isFinite(configured) && configured > 0 ? configured : await getPort({ port: 18080 });
        const url = await startSidecar(node, port);
        node.status({ fill: 'green', shape: 'dot', text: `sidecar ${url}` });
        msg.payload = { ok: true, baseUrl: url };
        send(msg);
        cleanup();
      } catch (e) {
        node.status({ fill: 'red', shape: 'ring', text: e.message || 'sidecar start failed' });
        msg.payload = { ok: false, error: e.message };
        send(msg);
        cleanup();
      }
    });

    node.on('close', (removed, done) => {
      stopping = true;
      try {
        if (proc && !proc.killed) {
          proc.removeAllListeners('error');
          proc.removeAllListeners('exit');
          proc.stderr && proc.stderr.removeAllListeners('data');
          proc.kill('SIGTERM');
        }
      } catch (_) {}
      proc = null;
      startPromise = null;
      setTimeout(done, 200);
    });
  }

  RED.nodes.registerType('rmq-sidecar', RmqSidecar);
};