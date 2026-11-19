package com.example.classmanagement.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component @ConfigurationProperties(prefix="ai.deepseek")
public class AiConfig {
    public String apiKey="",baseUrl="https://api.deepseek.com",model="deepseek-flash";
    public int connectTimeoutSeconds=5,requestTimeoutSeconds=25,totalTimeoutSeconds=90,maxHistory=10,maxTokens=2000,maxRounds=6;
    public String getApiKey(){return apiKey;} public void setApiKey(String v){apiKey=v;}
    public String getBaseUrl(){return baseUrl;} public void setBaseUrl(String v){baseUrl=v;}
    public String getModel(){return model;} public void setModel(String v){model=v;}
    public int getConnectTimeoutSeconds(){return connectTimeoutSeconds;} public void setConnectTimeoutSeconds(int v){connectTimeoutSeconds=Math.max(1,Math.min(v,30));}
    public int getRequestTimeoutSeconds(){return requestTimeoutSeconds;} public void setRequestTimeoutSeconds(int v){requestTimeoutSeconds=Math.max(1,Math.min(v,60));}
    public int getTotalTimeoutSeconds(){return totalTimeoutSeconds;} public void setTotalTimeoutSeconds(int v){totalTimeoutSeconds=Math.max(1,Math.min(v,120));}
    public int getMaxHistory(){return maxHistory;} public void setMaxHistory(int v){maxHistory=Math.max(2,Math.min(v,20));}
    public int getMaxTokens(){return maxTokens;} public void setMaxTokens(int v){maxTokens=Math.max(100,Math.min(v,4000));}
    public int getMaxRounds(){return maxRounds;} public void setMaxRounds(int v){maxRounds=Math.max(1,Math.min(v,8));}
}
