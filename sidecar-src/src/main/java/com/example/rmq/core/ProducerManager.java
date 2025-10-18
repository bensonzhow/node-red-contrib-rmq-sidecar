package com.example.rmq.core;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProducerManager {

  private static final Map<String, DefaultMQProducer> PRODUCERS = new ConcurrentHashMap<>();

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }

  public static DefaultMQProducer getOrCreate(String producerGroup,
                                              String namesrv,
                                              Integer sendTimeoutMs,
                                              String accessKey,
                                              String accessSecret) throws Exception {
    String ak = accessKey == null ? "" : accessKey;
    String key = producerGroup + "|" + namesrv + "|" + ak;

    DefaultMQProducer p = PRODUCERS.get(key);
    if (p != null) return p;

    RPCHook hook = null;
    if (!isBlank(accessKey) && !isBlank(accessSecret)) {
      hook = new AclClientRPCHook(new SessionCredentials(accessKey, accessSecret));
    }

    // DefaultMQProducer(String) 构造在 4.x 一直存在；带 hook 的构造也存在，但为保险这里统一用 setRpcHook
    p = new DefaultMQProducer(producerGroup);
    if (hook != null) {
      try {
        java.lang.reflect.Field f = DefaultMQProducer.class.getDeclaredField("rpcHook");
        f.setAccessible(true);
        f.set(p, hook);
      } catch (Exception ignore) {}
    }


    p.setNamesrvAddr(namesrv);
    p.setSendMsgTimeout(sendTimeoutMs != null ? sendTimeoutMs : 10000);
    p.start();

    PRODUCERS.put(key, p);
    return p;
  }

  public static SendResult send(String producerGroup, String namesrv,
                                String topic, String tag, String key,
                                byte[] body,
                                Integer sendTimeoutMs,
                                String accessKey, String accessSecret) throws Exception {
    DefaultMQProducer p = getOrCreate(producerGroup, namesrv, sendTimeoutMs, accessKey, accessSecret);
    Message msg = new Message(topic, tag, key, body);
    return p.send(msg);
  }

  public static void shutdownAll() {
    for (DefaultMQProducer p : PRODUCERS.values()) {
      try { p.shutdown(); } catch (Exception ignored) {}
    }
    PRODUCERS.clear();
  }
}
