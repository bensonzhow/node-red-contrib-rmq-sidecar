module.exports = function(RED) {
  const axios = require('axios');

  function Producer(n) {
    RED.nodes.createNode(this, n);
    const node = this;
    const base = n.baseUrl || 'http://127.0.0.1:18080';

    node.on('input', async (msg, send, done) => {
      try {
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
        const res = await axios.post(base + '/produce', body, { timeout: 15000 });
        msg.payload = res.data;
        send(msg); done();
      } catch (e) {
        msg.payload = { ok:false, error:e.message };
        node.status({ fill:'red', shape:'ring', text:'send failed' });
        send(msg); done();
      }
    });
  }

  RED.nodes.registerType('rmq-producer', Producer);
};