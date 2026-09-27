package cn.bigc.yinyuan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DeepSeekService {
    private final String apiKey, baseUrl, model;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public DeepSeekService(ObjectMapper mapper,
                           @Value("${app.deepseek.api-key:}") String apiKey,
                           @Value("${app.deepseek.base-url}") String baseUrl,
                           @Value("${app.deepseek.model}") String model,
                           @Value("${app.deepseek.connect-timeout:PT20S}") Duration connectTimeout) {
        this.mapper=mapper; this.apiKey=apiKey; this.baseUrl=baseUrl.replaceAll("/+$", ""); this.model=model;
        this.client=HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    public boolean configured() { return apiKey!=null && !apiKey.isBlank(); }

    public void stream(List<Map<String,String>> messages, Consumer<String> onToken) throws Exception {
        if(!configured()) throw new IllegalStateException("DeepSeek is not configured");
        var body=Map.of("model",model,"messages",messages,"stream",true,"max_tokens",2048);
        HttpRequest request=HttpRequest.newBuilder(URI.create(baseUrl+"/chat/completions"))
                .timeout(Duration.ofMinutes(3)).header("Authorization","Bearer "+apiKey)
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body),StandardCharsets.UTF_8)).build();
        HttpResponse<java.io.InputStream> response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
        if(response.statusCode()<200 || response.statusCode()>=300) {
            response.body().close(); throw new IllegalStateException("DeepSeek returned HTTP "+response.statusCode());
        }
        try(var reader=new BufferedReader(new InputStreamReader(response.body(),StandardCharsets.UTF_8))) {
            String line;
            while((line=reader.readLine())!=null) {
                if(!line.startsWith("data:")) continue;
                String data=line.substring(5).trim();
                if(data.isEmpty() || "[DONE]".equals(data)) continue;
                JsonNode root=mapper.readTree(data);
                String content=extractContent(root.path("choices").path(0).path("delta").path("content"));
                if(!content.isEmpty()) onToken.accept(content);
            }
        }
    }

    public String complete(List<Map<String,String>> messages) throws Exception {
        if(!configured()) return "";
        var body=Map.of("model",model,"messages",messages,"stream",false,"max_tokens",2048);
        HttpRequest request=HttpRequest.newBuilder(URI.create(baseUrl+"/chat/completions"))
                .timeout(Duration.ofMinutes(2)).header("Authorization","Bearer "+apiKey)
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body),StandardCharsets.UTF_8)).build();
        HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(response.statusCode()<200 || response.statusCode()>=300) throw new IllegalStateException("DeepSeek returned HTTP "+response.statusCode());
        JsonNode root=mapper.readTree(response.body());
        return extractContent(root.path("choices").path(0).path("message").path("content"));
    }

    private String extractContent(JsonNode content) {
        if(content.isTextual()) return content.asText();
        if(content.isArray()) {
            StringBuilder out=new StringBuilder();
            for(JsonNode part:content) {
                if(part.has("text")) out.append(part.path("text").asText());
                else if(part.has("content")) out.append(part.path("content").asText());
            }
            return out.toString();
        }
        return "";
    }
}
