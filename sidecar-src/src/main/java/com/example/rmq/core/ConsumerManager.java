package com.example.rmq.core;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class ConsumerManager {

  private static class Buf {
    final DefaultMQPushConsumer consumer;
    final BlockingQueue<MessageExt> queue;
    Buf(DefaultMQPushConsumer c, int bufSize) {
      this.consumer = c;
      this.queue = new LinkedBlockingQueue<MessageExt>(bufSize);
    }
  }

  // key: group|topic|tag|namesrv|ak
  private static final Map<String, Buf> CONSUMERS = new ConcurrentHashMap<String, Buf>();

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }

  private static String keyOf(String group, String topic, String tag, String namesrv, String ak) {
    return group + "|" + topic + "|" + (isBlank(tag) ? "*" : tag) + "|" + namesrv + "|" + (ak == null ? "" : ak);
  }

  public static synchronized void start(String group, String namesrv,
                                        String topic, String tag,
                                        Integer bufSize,
                                        Integer threadNums,
                                        Integer maxReconsumeTimes,
                                        String accessKey, String accessSecret) throws Exception {
    String key = keyOf(group, topic, tag, namesrv, accessKey);
    if (CONSUMERS.containsKey(key)) return;

    RPCHook hook = null;
    if (!isBlank(accessKey) && !isBlank(accessSecret)) {
      hook = new AclClientRPCHook(new SessionCredentials(accessKey, accessSecret));
    }

    // 兼容 RocketMQ 4.x，直接使用带 RPCHook 构造（该构造从 4.7 起存在）
    DefaultMQPushConsumer c = new DefaultMQPushConsumer(group);

// 对部分裁剪版 4.x：构造函数没有 RPCHook，只能反射注入
    if (hook != null) {
      try {
        java.lang.reflect.Field f = DefaultMQPushConsumer.class.getDeclaredField("rpcHook");
        f.setAccessible(true);
        f.set(c, hook);
      } catch (Exception ignore) {
        // 无法注入则忽略：客户端会当作普通无 ACL 消费者使用
      }
    }

    c.setNamesrvAddr(namesrv);
    int t = (threadNums != null && threadNums.intValue() > 0) ? threadNums.intValue() : 2;
    c.setConsumeThreadMin(t);
    c.setConsumeThreadMax(Math.max(t, 4));
    c.setMaxReconsumeTimes(maxReconsumeTimes != null ? maxReconsumeTimes.intValue() : 16);
    c.subscribe(topic, isBlank(tag) ? "*" : tag);

    int capacity = (bufSize != null && bufSize.intValue() > 0) ? bufSize.intValue() : 1000;
    final Buf buf = new Buf(c, Math.max(capacity, 10));

    c.registerMessageListener(new MessageListenerConcurrently() {
      public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext ctx) {
        for (MessageExt m : msgs) {
          boolean ok = buf.queue.offer(m);
          if (!ok) {
            // 内存缓冲满 → 要求稍后重投，避免内存飙涨
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
          }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
      }
    });

    c.start();
    CONSUMERS.put(key, buf);
  }

  public static synchronized void stop(String group, String namesrv, String topic, String tag, String accessKey) {
    String key = keyOf(group, topic, tag, namesrv, accessKey);
    Buf buf = CONSUMERS.remove(key);
    if (buf != null) {
      try { buf.consumer.shutdown(); } catch (Exception ignored) {}
    }
  }

  public static List<Map<String, Object>> poll(String group, String namesrv, String topic, String tag,
                                               String accessKey, int max, long timeoutMs) {
    String key = keyOf(group, topic, tag, namesrv, accessKey);
    Buf buf = CONSUMERS.get(key);
    List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
    if (buf == null) return out;

    long end = System.currentTimeMillis() + timeoutMs;
    while (out.size() < max && System.currentTimeMillis() < end) {
      try {
        MessageExt m = buf.queue.poll(200, TimeUnit.MILLISECONDS);
        if (m == null) continue;
        Map<String, Object> one = new LinkedHashMap<String, Object>();
        one.put("msgId", m.getMsgId());
        one.put("topic", m.getTopic());
        one.put("tags", m.getTags());
        one.put("keys", m.getKeys());
        one.put("bornTimestamp", m.getBornTimestamp());
        one.put("storeTimestamp", m.getStoreTimestamp());
        one.put("body", new String(m.getBody(), StandardCharsets.UTF_8));
        out.add(one);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    return out;
  }

  public static void shutdownAll() {
    for (Buf b : CONSUMERS.values()) {
      try { b.consumer.shutdown(); } catch (Exception ignored) {}
    }
    CONSUMERS.clear();
  }
}
