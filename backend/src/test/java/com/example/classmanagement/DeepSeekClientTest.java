package com.example.classmanagement;

import com.example.classmanagement.ai.*;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class DeepSeekClientTest {
    @Test void timeoutIsSafeAndBounded()throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat/completions",exchange->{try{Thread.sleep(1500);exchange.sendResponseHeaders(200,-1);}catch(Exception ignored){}finally{exchange.close();}});server.start();
        try{AiConfig config=new AiConfig();config.apiKey="synthetic-test-key";config.baseUrl="http://127.0.0.1:"+server.getAddress().getPort();var client=new DeepSeekClient(config,new ObjectMapper());assertThatThrownBy(()->client.chat(List.of(),List.of(),Duration.ofMillis(100))).hasMessageContaining("超时");}finally{server.stop(0);}
    }
    @Test void missingKeyDoesNotNeedNetwork(){assertThatThrownBy(()->new DeepSeekClient(new AiConfig(),new ObjectMapper()).chat(List.of(),List.of(),Duration.ofSeconds(1))).hasMessageContaining("未配置");}
    @Test void officialCompatiblePayloadAndSafeErrors()throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var status=new AtomicInteger(200);var requests=new ArrayList<String>();
        server.createContext("/chat/completions",exchange->{
            requests.add(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            String body=status.get()==200?"{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"协议测试\"}}],\"usage\":{\"total_tokens\":12}}":"sensitive upstream error";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status.get(),bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try{
            AiConfig config=new AiConfig();config.apiKey="synthetic-test-key";config.model="test-model";config.baseUrl="http://127.0.0.1:"+server.getAddress().getPort();
            var client=new DeepSeekClient(config,new ObjectMapper());
            assertThat(client.chat(List.of(Map.of("role","user","content","你好")),List.of(),Duration.ofSeconds(5)).path("content").asText()).isEqualTo("协议测试");
            assertThat(requests.get(0)).contains("test-model","max_tokens","messages").doesNotContain(config.apiKey);
            for(int code:List.of(401,402,429,503)){status.set(code);assertThatThrownBy(()->client.chat(List.of(),List.of(),Duration.ofSeconds(5))).hasMessageNotContaining("sensitive");}
        }finally{server.stop(0);}
    }
}
