package com.example.rmq.model;

public class ProduceRequest {
  public String namesrv;         // "host1:9876;host2:9876"
  public String producerGroup;   // "PG_XXX"
  public String topic;           // "TOPIC_XXX"
  public String tag;             // optional
  public String key;             // optional
  public String body;            // JSON string or plain text
  public Integer sendTimeoutMs;  // optional
  public String accessKey;       // optional (ACL)
  public String accessSecret;    // optional (ACL)
  public String sendMode;        // optional: sync|async|oneway
  public Integer asyncWaitMs;    // optional: override async wait timeout
}
