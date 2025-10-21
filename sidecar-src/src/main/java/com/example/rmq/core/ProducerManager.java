package com.example.rmq.core;

import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.RPCHook;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ProducerManager {

  private static final Map<String, DefaultMQProducer> PRODUCERS = new ConcurrentHashMap<>();
  private static final Duration DEFAULT_ASYNC_WAIT = Duration.ofSeconds(30);

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }

  public enum SendMode {
    SYNC,
    ASYNC,
    ONEWAY;

    public static SendMode of(String raw) {
      if (raw == null) return SYNC;
      switch (raw.trim().toUpperCase()) {
        case "ASYNC":
          return ASYNC;
        case "ONEWAY":
        case "ONE_WAY":
        case "ONE-WAY":
          return ONEWAY;
        case "SYNC":
        case "":
          return SYNC;
        default:
          throw new IllegalArgumentException("Unsupported sendMode: " + raw);
      }
    }
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

    if (hook != null) {
      try {
        p = new DefaultMQProducer(producerGroup, hook);
      } catch (Exception ignore) {}
    }else {
      p = new DefaultMQProducer(producerGroup);
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
                                String accessKey, String accessSecret,
                                SendMode mode,
                                Integer asyncWaitMs) throws Exception {
    DefaultMQProducer p = getOrCreate(producerGroup, namesrv, sendTimeoutMs, accessKey, accessSecret);
    Message msg = new Message(topic, tag, key, body);

    SendMode resolvedMode = mode == null ? SendMode.SYNC : mode;
    switch (resolvedMode) {
      case SYNC:
        return p.send(msg);
      case ASYNC:
        return sendAsync(p, msg, asyncWaitMs != null ? asyncWaitMs : (int) DEFAULT_ASYNC_WAIT.toMillis());
      case ONEWAY:
        p.sendOneway(msg);
        return null;
      default:
        throw new IllegalStateException("Unhandled send mode: " + resolvedMode);
    }
  }

  private static SendResult sendAsync(DefaultMQProducer producer, Message message, int waitMs) throws Exception {
    CompletableFuture<SendResult> future = new CompletableFuture<>();
    producer.send(message, new SendCallback() {
      @Override
      public void onSuccess(SendResult sendResult) {
        future.complete(sendResult);
      }

      @Override
      public void onException(Throwable e) {
        future.completeExceptionally(e);
      }
    });
    try {
      return future.get(waitMs, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      future.cancel(true);
      throw e;
    }
  }

  public static void shutdownAll() {
    for (DefaultMQProducer p : PRODUCERS.values()) {
      try { p.shutdown(); } catch (Exception ignored) {}
    }
    PRODUCERS.clear();
  }
}
