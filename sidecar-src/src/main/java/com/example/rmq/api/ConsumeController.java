package com.example.rmq.api;

import com.example.rmq.core.ConsumerManager;
import com.example.rmq.model.PolledMessage;
import com.example.rmq.model.StartConsumeRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ConsumeController {

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }

  @PostMapping("/consumer/start")
  public Map<String, Object> start(@RequestBody StartConsumeRequest req) throws Exception {
    if (isBlank(req.namesrv))        throw new IllegalArgumentException("namesrv required");
    if (isBlank(req.consumerGroup))  throw new IllegalArgumentException("consumerGroup required");
    if (isBlank(req.topic))          throw new IllegalArgumentException("topic required");

    ConsumerManager.start(
        req.consumerGroup, req.namesrv, req.topic, req.tag,
        req.bufferSize, req.threadNums, req.maxReconsumeTimes,
        req.accessKey, req.accessSecret
    );
    return java.util.Collections.unmodifiableMap(
        new java.util.LinkedHashMap<String, Object>() {{
          put("ok", true); put("started", true);
        }}
    );
  }

  @PostMapping("/consumer/stop")
  public Map<String, Object> stop(@RequestBody StartConsumeRequest req) {
    if (isBlank(req.namesrv) || isBlank(req.consumerGroup) || isBlank(req.topic)) {
      return java.util.Collections.unmodifiableMap(
          new java.util.LinkedHashMap<String, Object>() {{
            put("ok", false); put("error", "namesrv/consumerGroup/topic required");
          }}
      );
    }
    ConsumerManager.stop(req.consumerGroup, req.namesrv, req.topic, req.tag, req.accessKey);
    return java.util.Collections.unmodifiableMap(
        new java.util.LinkedHashMap<String, Object>() {{
          put("ok", true); put("stopped", true);
        }}
    );
  }

  @GetMapping("/consumer/poll")
  public PolledMessage poll(@RequestParam String namesrv,
                            @RequestParam String consumerGroup,
                            @RequestParam String topic,
                            @RequestParam(required = false) String tag,
                            @RequestParam(defaultValue = "10") int max,
                            @RequestParam(defaultValue = "2000") long timeoutMs,
                            @RequestParam(required = false) String accessKey) {
    PolledMessage resp = new PolledMessage();
    try {
      List<Map<String, Object>> msgs = ConsumerManager.poll(consumerGroup, namesrv, topic, tag, accessKey, max, timeoutMs);
      resp.ok = true;
      resp.messages = msgs;
      return resp;
    } catch (Exception e) {
      resp.ok = false;
      resp.error = e.getMessage();
      return resp;
    }
  }
}