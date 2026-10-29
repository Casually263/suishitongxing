package com.suishi.ai.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix="app.ai")
public class AiProperties {
    private String apiKey="", baseUrl="", model="";
    private List<String> allowedOrigins=List.of("https://api.openai.com");
    private boolean allowLocalEndpoints=false;
    @JsonIgnore public String getApiKey(){return apiKey;}
    public void setApiKey(String value){apiKey=value==null?"":value.strip();}
    public String getBaseUrl(){return baseUrl;}
    public void setBaseUrl(String value){baseUrl=value==null?"":value.strip();}
    public String getModel(){return model;}
    public void setModel(String value){model=value==null?"":value.strip();}
    public List<String> getAllowedOrigins(){return allowedOrigins;}
    public void setAllowedOrigins(List<String> value){allowedOrigins=value==null?List.of():List.copyOf(value);}
    public boolean isAllowLocalEndpoints(){return allowLocalEndpoints;}
    public void setAllowLocalEndpoints(boolean value){allowLocalEndpoints=value;}
    @Override public String toString(){return "AiProperties[credentials=REDACTED]";}
}
