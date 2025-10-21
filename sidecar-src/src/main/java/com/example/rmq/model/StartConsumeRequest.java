package com.example.rmq.model;

public class StartConsumeRequest {
  public String namesrv;
  public String consumerGroup;
  public String topic;
  public String tag;               // optional
  public Integer bufferSize;       // default 1000
  public Integer threadNums;       // default 2~4
  public Integer maxReconsumeTimes;// default 16
  public String accessKey;         // optional
  public String accessSecret;      // optional
}