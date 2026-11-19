package com.example.classmanagement.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeepSeekConnectionTest {
    DeepSeekClient client(HttpClient... clients){
        var config=new AiConfig();config.apiKey="test-only-key";
        var client=spy(new DeepSeekClient(config,new ObjectMapper()));
        var next=new ArrayDeque<>(List.of(clients));
        doAnswer(call->next.removeFirst()).when(client).newHttpClient();
        return client;
    }
    HttpResponse<byte[]> response(int status,String body){
        @SuppressWarnings("unchecked") HttpResponse<byte[]> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));return response;
    }
    @Test void reconnectsOnceAfterBrokenConnection()throws Exception{
        var first=mock(HttpClient.class);var second=mock(HttpClient.class);
        when(first.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenThrow(new IOException("connection closed"));
        var success=response(200,"{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"OK\"}}]}");
        when(second.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(success);
        assertThat(client(first,second).chat(List.of(),List.of(),Duration.ofSeconds(2)).path("content").asText()).isEqualTo("OK");
        verify(first,times(1)).send(any(HttpRequest.class),any());verify(second,times(1)).send(any(HttpRequest.class),any());
    }
    @Test void stopsAfterSecondNetworkFailure()throws Exception{
        var http=mock(HttpClient.class);
        when(http.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenThrow(new IOException("connection closed"));
        assertThatThrownBy(()->client(http,http).chat(List.of(),List.of(),Duration.ofSeconds(2))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("连接失败");
        verify(http,times(2)).send(any(HttpRequest.class),any());
    }
    @Test void doesNotRetryTimeoutOrInvalidJson()throws Exception{
        var http=mock(HttpClient.class);
        when(http.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenThrow(new HttpTimeoutException("timeout"));
        assertThatThrownBy(()->client(http).chat(List.of(),List.of(),Duration.ofSeconds(2))).hasMessageContaining("请求超时");
        verify(http,times(1)).send(any(HttpRequest.class),any());
        reset(http);
        var invalid=response(200,"invalid json");
        when(http.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(invalid);
        assertThatThrownBy(()->client(http).chat(List.of(),List.of(),Duration.ofSeconds(2))).hasMessageContaining("无法解析");
        verify(http,times(1)).send(any(HttpRequest.class),any());
    }
}
