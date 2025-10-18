package com.example.rmq.api;

import com.example.rmq.core.ProducerManager;
import com.example.rmq.model.ProduceRequest;
import com.example.rmq.model.ProduceResponse;
import org.apache.rocketmq.client.producer.SendResult;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
public class ProduceController {

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }

  @PostMapping("/produce")
  public ProduceResponse produce(@RequestBody ProduceRequest req) {
    ProduceResponse resp = new ProduceResponse();
    try {
      if (isBlank(req.namesrv))        throw new IllegalArgumentException("namesrv required");
      if (isBlank(req.producerGroup))  throw new IllegalArgumentException("producerGroup required");
      if (isBlank(req.topic))          throw new IllegalArgumentException("topic required");

      String body = req.body == null ? "" : req.body;
      SendResult sr = ProducerManager.send(
          req.producerGroup, req.namesrv,
          req.topic, req.tag, req.key,
          body.getBytes(StandardCharsets.UTF_8),
          req.sendTimeoutMs,
          req.accessKey, req.accessSecret
      );
      resp.ok = true;
      resp.msgId = sr.getMsgId();
      resp.status = sr.getSendStatus() == null ? null : sr.getSendStatus().name();
      return resp;
    } catch (Exception e) {
      resp.ok = false;
      resp.error = e.getMessage();
      return resp;
    }
  }
}