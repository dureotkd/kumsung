package kr.co.kumsungenc.platform.file;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.io.InputStream;
import java.security.*;
import java.util.HexFormat;

@Service
public class FileStorageService {
    private final MalwareScanService malware;
    private final ObjectStorage storage;
    public FileStorageService(MalwareScanService malware,ObjectStorage storage){this.malware=malware;this.storage=storage;}
    public String store(MultipartFile file,String key) throws IOException {
        malware.scan(file);
        Path temp=Files.createTempFile("kumsung-upload-",".tmp");
        try{
            Files.copy(file.getInputStream(),temp,StandardCopyOption.REPLACE_EXISTING);
            String checksum=sha256(temp);
            storage.put(key,temp,file.getContentType()==null?"application/octet-stream":file.getContentType());
            registerRollbackCleanup(key);
            return checksum;
        }finally{Files.deleteIfExists(temp);}
    }
    public String store(Path source,String key,String contentType) throws IOException{
        String checksum=sha256(source);storage.put(key,source,contentType);registerRollbackCleanup(key);return checksum;
    }
    public ObjectStorage.StoredObject load(String key) throws IOException{return storage.get(key);}
    public void delete(String key) throws IOException{storage.delete(key);}
    private void registerRollbackCleanup(String key){
        if(!TransactionSynchronizationManager.isSynchronizationActive())return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){
                if(status==STATUS_ROLLED_BACK)try{storage.delete(key);}catch(IOException ignored){}
            }
        });
    }
    public String sha256(Path path) throws IOException {
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=Files.newInputStream(path)){
                byte[] buffer=new byte[8192];int read;
                while((read=in.read(buffer))!=-1)digest.update(buffer,0,read);
            }
            return HexFormat.of().formatHex(digest.digest());
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
