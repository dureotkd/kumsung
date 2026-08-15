package kr.co.kumsungenc.platform.file;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class FileValidationService {
    private static final int MAX_FILES_PER_REQUEST=20;
    private static final long MAX_TOTAL_SIZE=200L*1024*1024;
    private static final Set<String> QUOTE_EXTENSIONS=Set.of("pdf","jpg","jpeg","png","dwg","dxf","xls","xlsx","zip");
    private static final Set<String> DOCUMENT_EXTENSIONS=Set.of("pdf","xls","xlsx","doc","docx");
    private static final Set<String> IMAGE_EXTENSIONS=Set.of("jpg","jpeg","png","webp");
    private static final Set<String> RESOURCE_EXTENSIONS=Set.of("pdf","jpg","jpeg","png","webp","dwg","dxf","xls","xlsx","doc","docx","zip");
    public String validateQuoteFile(MultipartFile file) throws IOException {
        return validate(file,QUOTE_EXTENSIONS,50L*1024*1024);
    }
    public String validateDocument(MultipartFile file) throws IOException {
        return validate(file,DOCUMENT_EXTENSIONS,50L*1024*1024);
    }
    public String validateImage(MultipartFile file) throws IOException {
        return validate(file,IMAGE_EXTENSIONS,15L*1024*1024);
    }
    public String validateResourceFile(MultipartFile file) throws IOException {
        return validate(file,RESOURCE_EXTENSIONS,50L*1024*1024);
    }
    public void validateBatch(List<MultipartFile> files) {
        long count=files==null?0:files.stream().filter(Objects::nonNull).filter(file->!file.isEmpty()).count();
        if(count<1)throw new IllegalArgumentException("파일을 1개 이상 첨부해 주세요.");
        if(count>MAX_FILES_PER_REQUEST)throw new IllegalArgumentException("한 번에 최대 20개 파일까지 첨부할 수 있습니다.");
        long total=0;
        for(MultipartFile file:files){
            if(file==null||file.isEmpty())continue;
            if(file.getSize()>MAX_TOTAL_SIZE-total)
                throw new IllegalArgumentException("첨부파일 전체 용량은 200MB까지 가능합니다.");
            total+=file.getSize();
        }
    }
    private String validate(MultipartFile file,Set<String> allowed,long maxSize) throws IOException {
        if(file==null||file.isEmpty())throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
        String name=StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(),""));
        String ext=extension(name);
        if(name.isBlank()||name.contains("..")||!allowed.contains(ext))
            throw new IllegalArgumentException("허용되지 않는 파일입니다: "+name);
        if(file.getSize()>maxSize)throw new IllegalArgumentException("파일 용량 제한을 초과했습니다: "+name);
        byte[] header;
        try(InputStream in=file.getInputStream()){header=in.readNBytes(256);}
        if(!matchesSignature(ext,header))
            throw new IllegalArgumentException("파일 내용과 확장자가 일치하지 않습니다: "+name);
        return ext;
    }
    private boolean matchesSignature(String ext,byte[] b){
        return switch(ext){
            case "pdf" -> startsAscii(b,"%PDF-");
            case "jpg","jpeg" -> b.length>=3&&(b[0]&255)==0xff&&(b[1]&255)==0xd8&&(b[2]&255)==0xff;
            case "png" -> b.length>=8&&(b[0]&255)==0x89&&startsAt(b,1,new byte[]{0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a});
            case "webp" -> startsAscii(b,"RIFF")&&startsAt(b,8,"WEBP".getBytes(StandardCharsets.US_ASCII));
            case "zip","xlsx","docx" -> b.length>=4&&b[0]=='P'&&b[1]=='K'&&((b[2]==3&&b[3]==4)||(b[2]==5&&b[3]==6)||(b[2]==7&&b[3]==8));
            case "xls","doc" -> startsAt(b,0,new byte[]{(byte)0xd0,(byte)0xcf,0x11,(byte)0xe0,(byte)0xa1,(byte)0xb1,0x1a,(byte)0xe1});
            case "dwg" -> startsAscii(b,"AC10");
            case "dxf" -> {
                String text=new String(b,StandardCharsets.ISO_8859_1).toUpperCase(Locale.ROOT);
                yield text.contains("SECTION")||text.startsWith("AUTOCAD BINARY DXF");
            }
            default -> false;
        };
    }
    private boolean startsAscii(byte[] b,String value){return startsAt(b,0,value.getBytes(StandardCharsets.US_ASCII));}
    private boolean startsAt(byte[] b,int offset,byte[] expected){
        if(b.length<offset+expected.length)return false;
        for(int i=0;i<expected.length;i++)if(b[offset+i]!=expected[i])return false;
        return true;
    }
    public String extension(String name){
        int dot=name.lastIndexOf('.');
        return dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);
    }
}
