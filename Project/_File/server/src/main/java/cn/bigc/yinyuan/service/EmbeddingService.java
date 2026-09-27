package cn.bigc.yinyuan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {
    private static final int DIMENSIONS=1536;
    private final String apiKey,endpoint,model;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public EmbeddingService(ObjectMapper mapper,@Value("${app.embedding.api-key:}") String apiKey,
                            @Value("${app.embedding.endpoint}") String endpoint,@Value("${app.embedding.model}") String model) {
        this.mapper=mapper; this.apiKey=apiKey; this.endpoint=endpoint; this.model=model;
        this.client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }
    public boolean remoteEnabled(){return apiKey!=null&&!apiKey.isBlank();}

    public List<float[]> embedAll(List<String> texts,String textType) {
        if(!remoteEnabled()) return texts.stream().map(this::localVector).toList();
        List<float[]> result=new ArrayList<>();
        for(int start=0;start<texts.size();start+=20) result.addAll(remoteBatch(texts.subList(start,Math.min(start+20,texts.size())),textType));
        return result;
    }

    private List<float[]> remoteBatch(List<String> texts,String textType) {
        try {
            Map<String,Object> body=Map.of("model",model,"input",Map.of("texts",texts),
                    "parameters",Map.of("output_type","dense","text_type",textType));
            HttpRequest request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(90))
                    .header("Authorization","Bearer "+apiKey).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body),StandardCharsets.UTF_8)).build();
            HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()<200||response.statusCode()>=300) throw new IllegalStateException("DashScope embedding request failed with HTTP "+response.statusCode());
            JsonNode root=mapper.readTree(response.body()); JsonNode items=root.path("output").path("embeddings");
            if(!items.isArray()) items=root.path("data");
            if(!items.isArray()||items.size()!=texts.size()) throw new IllegalStateException("Unexpected embedding response shape");
            List<float[]> vectors=new ArrayList<>();
            for(JsonNode item:items) {
                JsonNode values=item.path("embedding");
                if(!values.isArray()||values.size()!=DIMENSIONS) throw new IllegalStateException("Embedding model must return 1536 dimensions");
                float[] vector=new float[DIMENSIONS];
                for(int i=0;i<DIMENSIONS;i++) vector[i]=(float)values.get(i).asDouble();
                vectors.add(vector);
            }
            return vectors;
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Embedding request was interrupted",ex);
        } catch(Exception ex) {
            if(ex instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Embedding request failed",ex);
        }
    }

    private float[] localVector(String text) {
        float[] vector=new float[DIMENSIONS];
        String normalized=text==null?"":text.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
        int[] chars=normalized.codePoints().toArray();
        for(int n=1;n<=3;n++) for(int i=0;i+n<=chars.length;i++) {
            int hash=0x811c9dc5;
            for(int j=0;j<n;j++){hash^=chars[i+j];hash*=0x01000193;}
            vector[Math.floorMod(hash,DIMENSIONS)]+=((hash&1)==0?1f:-1f)/n;
        }
        double norm=0; for(float value:vector) norm+=value*value;
        if(norm>0){float scale=(float)(1.0/Math.sqrt(norm));for(int i=0;i<vector.length;i++)vector[i]*=scale;}
        return vector;
    }

    public static String toPgVector(float[] vector) {
        StringBuilder out=new StringBuilder("[");
        for(int i=0;i<vector.length;i++){if(i>0)out.append(',');out.append(Float.toString(vector[i]));}
        return out.append(']').toString();
    }
}
