package cn.bigc.yinyuan.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {
    private static final int CHUNK_SIZE=1100,OVERLAP=100;
    private final JdbcTemplate jdbc;
    private final EmbeddingService embeddings;
    public KnowledgeService(JdbcTemplate jdbc,EmbeddingService embeddings){this.jdbc=jdbc;this.embeddings=embeddings;}
    public record ImportResult(String documentId,String filename,int pages,int chunks,String embeddingMode){}
    public record SearchHit(String id,String document,int page,String section,String content,double score){}
    private record Chunk(int page,String section,String content){}

    public ImportResult importPdf(String filename,byte[] bytes) {
        if(bytes==null||bytes.length==0)throw new IllegalArgumentException("PDF 文件为空");
        String safeName=filename==null||filename.isBlank()?"knowledge.pdf":filename.replaceAll("[\\r\\n]","_");
        if(!safeName.toLowerCase().endsWith(".pdf"))throw new IllegalArgumentException("只支持 PDF 文件");
        String hash=sha256(bytes);List<Chunk> chunks=new ArrayList<>();int pages;
        try(PDDocument document=Loader.loadPDF(bytes)) {
            pages=document.getNumberOfPages();if(pages>5000)throw new IllegalArgumentException("PDF 页数超过导入上限");
            PDFTextStripper stripper=new PDFTextStripper();
            for(int page=1;page<=pages;page++) {
                stripper.setStartPage(page);stripper.setEndPage(page);
                String text=stripper.getText(document).replace((char)0,' ').trim();
                for(String part:split(text)) {
                    String clean=part.replaceAll("[\\t ]+"," ").trim();if(clean.length()<20)continue;
                    String first=clean.lines().findFirst().orElse("").trim();String section=first.length()>100?first.substring(0,100):first;
                    chunks.add(new Chunk(page,section,clean));
                }
            }
        }catch(IOException ex){throw new IllegalArgumentException("无法读取 PDF",ex);}
        if(chunks.isEmpty())throw new IllegalArgumentException("PDF 中没有提取到可检索文本");
        List<float[]> vectors=embeddings.embedAll(chunks.stream().map(Chunk::content).toList(),"document");
        UUID documentId=UUID.randomUUID();
        jdbc.update("insert into knowledge_documents(id,filename,file_hash,page_count,chunk_count,status) values(?,?,?,?,?,'READY') on conflict(file_hash) do update set filename=excluded.filename,imported_at=now(),page_count=excluded.page_count,chunk_count=excluded.chunk_count,status='READY'",documentId,safeName,hash,pages,chunks.size());
        documentId=jdbc.queryForObject("select id from knowledge_documents where file_hash=?",UUID.class,hash);
        jdbc.update("delete from knowledge_chunks where document_id=?",documentId);
        List<Object[]> rows=new ArrayList<>(chunks.size());
        for(int i=0;i<chunks.size();i++) {
            Chunk chunk=chunks.get(i);
            rows.add(new Object[]{UUID.randomUUID(),documentId,chunk.page(),chunk.section(),chunk.content(),EmbeddingService.toPgVector(vectors.get(i))});
        }
        jdbc.batchUpdate("insert into knowledge_chunks(id,document_id,page_no,section_title,content,embedding) values (?,?,?,?,?,?::vector)",rows);
        return new ImportResult(documentId.toString(),safeName,pages,chunks.size(),embeddings.remoteEnabled()?"dashscope":"local-hash-fallback");
    }

    public List<SearchHit> search(String query,int limit) {
        if(query==null||query.isBlank())return List.of();
        String vector=EmbeddingService.toPgVector(embeddings.embedAll(List.of(query),"query").get(0));
        return jdbc.query("select c.id::text,k.filename,c.page_no,c.section_title,c.content,1-(c.embedding <=> cast(? as vector)) as score from knowledge_chunks c join knowledge_documents k on k.id=c.document_id order by c.embedding <=> cast(? as vector) limit ?",
                (rs,row)->new SearchHit(rs.getString(1),rs.getString(2),rs.getInt(3),rs.getString(4),rs.getString(5),rs.getDouble(6)),vector,vector,Math.max(1,Math.min(limit,10)));
    }
    public List<Map<String,Object>> documents(){return jdbc.queryForList("select id::text as id,filename,page_count,chunk_count,status,imported_at from knowledge_documents order by imported_at desc");}
    private List<String> split(String text) {
        List<String> parts=new ArrayList<>();if(text.isBlank())return parts;int start=0;
        while(start<text.length()) {
            int end=Math.min(start+CHUNK_SIZE,text.length());
            if(end<text.length()){int boundary=Math.max(text.lastIndexOf('\n',end),text.lastIndexOf('。',end));if(boundary>start+CHUNK_SIZE/2)end=boundary+1;}
            String part=text.substring(start,end).trim();if(!part.isBlank())parts.add(part);
            if(end>=text.length())break;start=Math.max(start+1,end-OVERLAP);
        }
        return parts;
    }
    private String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception ex){throw new IllegalStateException("SHA-256 is unavailable",ex);}}
}
