module.exports = function(RED) {
  const axios = require('axios');
  const http = require('http');
  const https = require('https');

  function Producer(n) {
    RED.nodes.createNode(this, n);
    const node = this;
    const pickBaseUrl = (msg) => {
      const cand = msg.baseUrl || (msg.sidecar && msg.sidecar.baseUrl) || n.baseUrl || process.env.RMQ_SIDECAR_BASE || 'http://127.0.0.1:18080';
      return String(cand).replace(/\/$/, '');
    };

    async function probeHealth(baseUrl) {
      try {
        const res = await axios.get(baseUrl + '/actuator/health', { timeout: 2500, httpAgent: new http.Agent({ keepAlive: false }), httpsAgent: new https.Agent({ keepAlive: false }) });
        return res.status === 200 && res.data && (res.data.status === 'UP' || res.data.status === 'up');
      } catch (_) { return false; }
    }

    const RETRYABLE_CODES = new Set(['ECONNREFUSED','ECONNRESET','ETIMEDOUT']);
    async function withRetry(fn, times, baseDelayMs) {
      let lastErr;
      for (let i = 0; i < times; i++) {
        try { return await fn(); } catch (e) {
          lastErr = e;
          const code = e && e.code;
          if (!RETRYABLE_CODES.has(code)) break;
          await new Promise(r => setTimeout(r, baseDelayMs * Math.pow(2, i)));
        }
      }
      throw lastErr;
    }

    node.on('input', async (msg, send, done) => {
      const finish = () => { if (typeof done === 'function') done(); };
      const base = pickBaseUrl(msg);
      try {
        // Fast health probe to avoid immediate ECONNREFUSED
        const healthy = await probeHealth(base);
        if (!healthy) {
          const errMsg = `sidecar not ready at ${base}`;
          node.status({ fill:'red', shape:'ring', text: errMsg });
          msg.payload = { ok:false, error: errMsg };
          send(msg); return finish();
        }

        const cfg = msg.rocketmq || {};
        const body = {
          namesrv: cfg.namesrv,
          producerGroup: cfg.producerGroup,
          topic: cfg.topic || msg.topic,
          tag: cfg.tag || msg.tag,
          key: cfg.key,
          body: typeof msg.payload === 'string' ? msg.payload : JSON.stringify(msg.payload ?? {}),
          sendTimeoutMs: cfg.sendTimeoutMs || 10000,
          accessKey: cfg.accessKey,
          accessSecret: cfg.accessSecret
        };
        if(cfg.sendMode){
            body.sendMode = cfg.sendMode;
        }
        const client = axios.create({ baseURL: base, timeout: (cfg.sendTimeoutMs || 10000) + 1000, httpAgent: new http.Agent({ keepAlive: false }), httpsAgent: new https.Agent({ keepAlive: false }) });

        const res = await withRetry(() => client.post('/produce', body), 3, 300);
        msg.payload = res.data;
        node.status({ fill:'green', shape:'dot', text:'sent' });
        send(msg); finish();
      } catch (e) {
        const txt = (e && e.message) ? e.message : 'send failed';
        msg.payload = { ok:false, error: txt };
        node.status({ fill:'red', shape:'ring', text: txt });
        send(msg); finish();
      }
    });
  }

  RED.nodes.registerType('rmq-producer', Producer);
};