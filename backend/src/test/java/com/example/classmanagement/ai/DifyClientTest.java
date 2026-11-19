package com.example.classmanagement.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class DifyClientTest {
    @Test void parsesWorkflowStreamAndKeepsKeyOutOfInputs()throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var request=new AtomicReference<String>();var auth=new AtomicReference<String>();
        var json=new ObjectMapper();
        String reply=json.writeValueAsString(Map.of("content","请补充具体科目"));
        String finished=json.writeValueAsString(Map.of("event","workflow_finished","data",Map.of("status","succeeded","outputs",Map.of("reply",reply))));
        server.createContext("/v1/workflows/run",exchange->{request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));auth.set(exchange.getRequestHeaders().getFirst("Authorization"));byte[] bytes=("event: ping\n\ndata: {\"event\":\"node_started\",\"data\":{\"node_type\":\"llm\"}}\n\ndata: "+finished+"\n\n").getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try{
            var client=new DifyClient(json);client.key="synthetic-dify-key";client.url="http://127.0.0.1:"+server.getAddress().getPort()+"/v1";
            var progress=new ArrayList<String>();
            var result=client.chat(List.of(Map.of("role","user","content","谁的成绩最好")),List.of(),Duration.ofSeconds(3),progress::add);
            assertThat(result.path("content").asText()).isEqualTo("请补充具体科目");assertThat(progress).hasSize(1);
            assertThat(auth.get()).isEqualTo("Bearer synthetic-dify-key");assertThat(request.get()).contains("request_json","streaming","谁的成绩最好").doesNotContain(client.key);
        }finally{server.stop(0);}
    }
    @Test void rejectsFailedWorkflowEvenWhenHttpStatusIs200()throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/workflows/run",exchange->{byte[] bytes="data: {\"event\":\"workflow_finished\",\"data\":{\"status\":\"failed\",\"error\":\"sensitive upstream detail\"}}\n\n".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try{var client=new DifyClient(new ObjectMapper());client.key="synthetic-key";client.url="http://127.0.0.1:"+server.getAddress().getPort()+"/v1";assertThatThrownBy(()->client.chat(List.of(),List.of(),Duration.ofSeconds(3),s->{})).hasMessageContaining("工作流未完成").hasMessageNotContaining("sensitive");}finally{server.stop(0);}
    }
    @Test void explainsSslFailureWithoutExposingUpstreamDetails()throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/workflows/run",exchange->{byte[] bytes="data: {\"event\":\"workflow_finished\",\"data\":{\"status\":\"failed\",\"error\":\"SSLError UNEXPECTED_EOF sensitive-key\"}}\n\n".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try{var client=new DifyClient(new ObjectMapper());client.key="synthetic-key";client.url="http://127.0.0.1:"+server.getAddress().getPort()+"/v1";assertThatThrownBy(()->client.chat(List.of(),List.of(),Duration.ofSeconds(3),s->{})).hasMessageContaining("网络连接中断").hasMessageNotContaining("sensitive-key").hasMessageNotContaining("SSLError");}finally{server.stop(0);}
    }
    @Test void requiresAnAppKey(){var client=new DifyClient(new ObjectMapper());client.key="";assertThatThrownBy(()->client.chat(List.of(),List.of(),Duration.ofSeconds(1),s->{})).hasMessageContaining("尚未完成配置");}
}
