package com.example.classmanagement.ai;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.*;
import java.io.IOException;
import java.time.*;
import java.util.*;

@Service
public class DeepSeekClient {
    private final AiConfig config; private final ObjectMapper json; private volatile HttpClient http;
    public DeepSeekClient(AiConfig config,ObjectMapper json){this.config=config;this.json=json;}
    public JsonNode chat(List<Map<String,Object>> messages,List<Map<String,Object>> tools,Duration remaining){
        if(config.apiKey.isBlank())throw failure("DeepSeek API Key 未配置，请联系管理员");
        try {
            String body=json.writeValueAsString(Map.of("model",config.model,"messages",messages,"tools",tools,"max_tokens",config.maxTokens,"thinking",Map.of("type","disabled")));
            if(body.length()>100000)throw failure("查询上下文过长，请缩小范围或新建会话");
            var request=HttpRequest.newBuilder(URI.create(config.baseUrl.replaceAll("/+$","")+"/chat/completions"))
                .timeout(remaining.compareTo(Duration.ofSeconds(config.requestTimeoutSeconds))<0?remaining:Duration.ofSeconds(config.requestTimeoutSeconds))
                .header("Authorization","Bearer "+config.apiKey).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response=send(request,remaining);
            {
                int status=response.statusCode();
                org.slf4j.LoggerFactory.getLogger(getClass()).info("DeepSeek status={}",status);
                if(status!=200)throw failure(switch(status){case 401,403->"DeepSeek 认证失败，请检查后端配置";case 402->"DeepSeek 额度不足";case 429->"DeepSeek 请求限流，请稍后再试";default->"DeepSeek 服务暂不可用，请稍后再试";});
                byte[] bytes=response.body();if(bytes.length>262144)throw failure("DeepSeek 返回内容过长");
                JsonNode root=json.readTree(bytes),choice=root.path("choices").path(0);
                if(!Set.of("stop","tool_calls").contains(choice.path("finish_reason").asText())||!choice.path("message").isObject())throw failure("DeepSeek 返回异常或输出被截断，请缩小查询范围");
                JsonNode usage=root.path("usage");org.slf4j.LoggerFactory.getLogger(getClass()).info("DeepSeek tokens={}",usage.path("total_tokens").asInt(0));
                return choice.path("message");
            }
        }catch(ResponseStatusException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw failure("AI 请求已中断");}
        catch(Exception e){
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("DeepSeek request failed: type={}, cause={}",e.getClass().getSimpleName(),e.getCause()==null?"none":e.getCause().getClass().getSimpleName());
            throw failure(e instanceof HttpTimeoutException?"DeepSeek 请求超时，请稍后重试":e instanceof com.fasterxml.jackson.core.JsonProcessingException?"DeepSeek 返回的数据无法解析，请重试":e instanceof IOException?"DeepSeek 连接失败，请检查网络后重试":"DeepSeek 请求配置异常，请联系管理员");
        }
    }
    HttpClient newHttpClient(){return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(config.connectTimeoutSeconds)).followRedirects(HttpClient.Redirect.NEVER).build();}
    private HttpResponse<byte[]> send(HttpRequest request,Duration remaining)throws IOException,InterruptedException{
        Instant deadline=Instant.now().plus(remaining);
        for(int attempt=0;;attempt++){
            if(http==null)synchronized(this){if(http==null)http=newHttpClient();}
            Duration left=Duration.between(Instant.now(),deadline);
            if(left.isNegative()||left.isZero())throw new HttpTimeoutException("AI request deadline exceeded");
            var current=HttpRequest.newBuilder(request,(name,value)->true).timeout(left.compareTo(request.timeout().orElse(left))<0?left:request.timeout().orElse(left)).build();
            try{return http.send(current,HttpResponse.BodyHandlers.ofByteArray());}
            catch(IOException e){
                http=null;
                if(attempt==1||e instanceof HttpTimeoutException||e instanceof javax.net.ssl.SSLException)throw e;
            }
        }
    }
    static ResponseStatusException failure(String message){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,message);}
}
