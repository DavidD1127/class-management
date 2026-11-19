package com.example.classmanagement.ai;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Service
public class DifyClient {
    @Value("${AI_ENGINE:deepseek}") String engine;
    @Value("${DIFY_API_URL:http://127.0.0.1:8088/v1}") String url;
    @Value("${DIFY_API_KEY:}") String key;
    private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ScheduledExecutorService TIMEOUTS=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"dify-timeouts");t.setDaemon(true);return t;});
    public DifyClient(ObjectMapper json){this.json=json;}
    public boolean enabled(){return "dify".equalsIgnoreCase(engine);}
    public Map<String,Object> status(){return Map.of("engine",enabled()?"Dify":"DeepSeek","ready",!enabled()||!key.isBlank());}
    public JsonNode chat(List<Map<String,Object>> messages,List<Map<String,Object>> tools,Duration remaining,Consumer<String> progress){
        if(key.isBlank())throw DeepSeekClient.failure("智能助手尚未完成配置，请联系管理员完成 Dify 连接");
        try{
            String context=json.writeValueAsString(Map.of("messages",messages,"tools",tools));
            if(context.length()>100000)throw new IllegalArgumentException("会话内容过长，请新建会话或缩小名单范围");
            String body=json.writeValueAsString(Map.of("inputs",Map.of("request_json",context),"response_mode","streaming","user","class-management"));
            var request=HttpRequest.newBuilder(URI.create(url.replaceAll("/+$","")+"/workflows/run"))
                .header("Authorization","Bearer "+key).header("Content-Type","application/json")
                .timeout(remaining).POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(var stream=response.body()){
                if(response.statusCode()!=200)throw DeepSeekClient.failure(response.statusCode()==401?"智能助手连接认证失败，请联系管理员":"智能助手服务暂不可用，请稍后重试");
                var timeout=TIMEOUTS.schedule(()->{try{stream.close();}catch(IOException ignored){}},Math.max(1,remaining.toMillis()),TimeUnit.MILLISECONDS);
                try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
                    String line;int size=0;
                    while((line=reader.readLine())!=null){
                        if((size+=line.length())>524288)throw DeepSeekClient.failure("智能助手返回内容过长");
                        if(!line.startsWith("data:"))continue;
                        JsonNode event=json.readTree(line.substring(5).trim());
                        if(event.path("event").asText().equals("error"))throw DeepSeekClient.failure("智能助手执行失败，请检查模型配置后重试");
                        if(event.path("event").asText().equals("node_started")&&event.path("data").path("node_type").asText().equals("llm"))progress.accept("正在理解你的指令……");
                        if(event.path("event").asText().equals("workflow_finished")){
                            var data=event.path("data");
                            if(!data.path("status").asText().equals("succeeded")){
                                String cause=data.path("error").asText().toLowerCase(Locale.ROOT);
                                if(cause.contains("insufficient balance")||cause.contains("402"))throw DeepSeekClient.failure("模型账户余额不足，请充值后重试；这不是网络或配置问题");
                                if(cause.contains("ssl")||cause.contains("connection")||cause.contains("timed out"))throw DeepSeekClient.failure("助手与模型服务的网络连接中断，请稍后重试；若持续失败，请联系管理员检查网络连接");
                                throw DeepSeekClient.failure("智能助手工作流未完成，请稍后重试");
                            }
                            String reply=data.path("outputs").path("reply").asText();
                            if(reply.isBlank()||reply.length()>100000)throw DeepSeekClient.failure("智能助手返回内容无效");
                            // 工作流返回结构化指令，仍由原有后端工具校验权限与业务规则。
                            if(reply.startsWith("```"))reply=reply.replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","");
                            JsonNode result=json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(reply);
                            if(!result.isObject())throw DeepSeekClient.failure("智能助手指令格式异常");
                            return result;
                        }
                    }
                    throw DeepSeekClient.failure("智能助手连接提前结束，请重试");
                }finally{timeout.cancel(false);}
            }
        }catch(org.springframework.web.server.ResponseStatusException|IllegalArgumentException e){throw e;}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw DeepSeekClient.failure("智能助手请求已中断");}
        catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Dify request failed: {}",e.getClass().getSimpleName());throw DeepSeekClient.failure("智能助手连接或返回格式异常，请稍后重试");}
    }
}
