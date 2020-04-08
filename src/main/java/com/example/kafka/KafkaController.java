package com.example.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/kafka")
@Slf4j(topic = "kafka_logger")
public class KafkaController {
    @GetMapping("test")
    public Object testLog() {
        for (int i = 0; i < 10; i++) {
            log.info("开始打印日志了[{}]", i);
        }
        return "日志打印成功";
    }
}
