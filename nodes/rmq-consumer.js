module.exports = function(RED) {
  const axios = require('axios');

  function Consumer(n) {
    RED.nodes.createNode(this, n);
    const node = this;
    const base = n.baseUrl || 'http://127.0.0.1:18080';
    let running = false, t = null, lastCfg = null;

    async function loop() {
      if (!running || !lastCfg) return;
      try {
        const res = await axios.get(base + '/consumer/poll', {
          params: {
            namesrv: lastCfg.namesrv,
            consumerGroup: lastCfg.consumerGroup,
            topic: lastCfg.topic,
            tag: lastCfg.tag || '*',
            max: lastCfg.max || 16,
            timeoutMs: lastCfg.timeoutMs || 2000,
            accessKey: lastCfg.accessKey
          },
          timeout: (lastCfg.timeoutMs || 2000) + 1000
        });
        const data = res.data || {};
        if (data.ok && Array.isArray(data.messages) && data.messages.length) {
          data.messages.forEach(m => node.send({ payload: m }));
          node.status({ fill:'green', shape:'dot', text:`msgs ${data.messages.length}` });
        } else {
          node.status({ fill:'yellow', shape:'ring', text:'idle' });
        }
      } catch (e) {
        node.status({ fill:'red', shape:'ring', text:'poll error' });
        node.warn(e.message);
      } finally {
        t = setTimeout(loop, 300);
      }
    }

    node.on('input', async (msg, send, done) => {
      try {
        const cfg = msg.rocketmq || {};
        if (msg.cmd === 'start') {
          lastCfg = cfg; running = true;
          await axios.post(base + '/consumer/start', {
            namesrv: cfg.namesrv, consumerGroup: cfg.consumerGroup,
            topic: cfg.topic, tag: cfg.tag || '*',
            bufferSize: cfg.bufferSize || 1000,
            threadNums: cfg.threadNums || 2,
            maxReconsumeTimes: cfg.maxReconsumeTimes || 16,
            accessKey: cfg.accessKey, accessSecret: cfg.accessSecret
          }, { timeout: 15000 });
          loop();
          send({ payload: { ok:true, started:true } }); done();
        } else if (msg.cmd === 'stop') {
          running = false; if (t) clearTimeout(t);
          await axios.post(base + '/consumer/stop', {
            namesrv: cfg.namesrv, consumerGroup: cfg.consumerGroup,
            topic: cfg.topic, tag: cfg.tag || '*'
          }, { timeout: 10000 });
          send({ payload: { ok:true, stopped:true } }); done();
        } else {
          send({ payload: { ok:false, error:'use msg.cmd=start|stop' } }); done();
        }
      } catch (e) {
        send({ payload: { ok:false, error:e.message } }); done();
      }
    });

    node.on('close', () => { running = false; if (t) clearTimeout(t); });
  }

  RED.nodes.registerType('rmq-consumer', Consumer);
};