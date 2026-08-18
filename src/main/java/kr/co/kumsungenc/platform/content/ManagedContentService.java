package kr.co.kumsungenc.platform.content;

import kr.co.kumsungenc.platform.file.FileStorageService;
import kr.co.kumsungenc.platform.file.FileValidationService;
import kr.co.kumsungenc.platform.file.ObjectStorage;
import kr.co.kumsungenc.platform.file.StorageKeys;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
public class ManagedContentService {
    private static final Set<String> POST_TYPES=Set.of("COMPANY_NEWS","CONSTRUCTION_CASE");
    private static final Set<String> INNOVATION_CATEGORIES=Set.of("RND","PATENT_CERT","TECHNICAL","KNOWLEDGE","SMART_FACTORY");
    private final JdbcTemplate jdbc;
    private final FileValidationService validation;
    private final FileStorageService storage;
    private final PasswordEncoder passwordEncoder;

    public ManagedContentService(JdbcTemplate jdbc,FileValidationService validation,
        FileStorageService storage,PasswordEncoder passwordEncoder){
        this.jdbc=jdbc;this.validation=validation;this.storage=storage;this.passwordEncoder=passwordEncoder;
    }

    public List<Map<String,Object>> adminProducts(int limit,int offset){
        List<Map<String,Object>> rows=jdbc.queryForList("""
            select id,code,name,category,description,price,active,display_order,image_url,
                   image_key,image_original_name,image_content_type,image_size,created_at,updated_at
            from shop_products order by display_order,id limit ? offset ?
            """,pageSize(limit),pageOffset(offset));
        rows.forEach(this::addProductImageUrl);
        return rows;
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> createProduct(String code,String name,String category,String description,Long price,
        int displayOrder,boolean active,MultipartFile image) throws IOException{
        String cleanCode=productCode(code),cleanName=required(name,"제품명",120);
        String imageKey=null,originalName=null,contentType=null;Long imageSize=null;
        if(image!=null&&!image.isEmpty()){
            String ext=validation.validateImage(image),reference=UUID.randomUUID().toString();
            imageKey=StorageKeys.shopProduct(reference,UUID.randomUUID()+"."+ext);
            storage.store(image,imageKey);originalName=originalName(image);contentType=contentType(image);imageSize=image.getSize();
        }
        Long id=jdbc.queryForObject("""
            insert into shop_products(code,name,category,description,price,active,display_order,image_key,image_original_name,image_content_type,image_size)
            values(?,?,?,?,?,?,?,?,?,?,?) returning id
            """,Long.class,cleanCode,cleanName,optional(category,80),optional(description,500),price(price),active,displayOrder,
            imageKey,originalName,contentType,imageSize);
        return product(id);
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> updateProduct(long id,String code,String name,String category,String description,Long price,
        int displayOrder,boolean active,MultipartFile image) throws IOException{
        Map<String,Object> before=product(id);
        String newKey=null,originalName=null,contentType=null;Long imageSize=null;
        if(image!=null&&!image.isEmpty()){
            String ext=validation.validateImage(image),reference=UUID.randomUUID().toString();
            newKey=StorageKeys.shopProduct(reference,UUID.randomUUID()+"."+ext);
            storage.store(image,newKey);originalName=originalName(image);contentType=contentType(image);imageSize=image.getSize();
            jdbc.update("""
                update shop_products set code=?,name=?,category=?,description=?,price=?,display_order=?,active=?,image_url=null,
                    image_key=?,image_original_name=?,image_content_type=?,image_size=?,updated_at=current_timestamp where id=?
                """,productCode(code),required(name,"제품명",120),optional(category,80),optional(description,500),price(price),displayOrder,active,
                newKey,originalName,contentType,imageSize,id);
            deleteAfterCommit((String)before.get("image_key"));
        }else{
            jdbc.update("""
                update shop_products set code=?,name=?,category=?,description=?,price=?,display_order=?,active=?,updated_at=current_timestamp where id=?
                """,productCode(code),required(name,"제품명",120),optional(category,80),optional(description,500),price(price),displayOrder,active,id);
        }
        return product(id);
    }

    @Transactional
    public void productStatus(long id,boolean active){
        if(jdbc.update("update shop_products set active=?,updated_at=current_timestamp where id=?",active,id)==0)
            throw new NoSuchElementException();
    }

    public Map<String,Object> product(long id){
        Map<String,Object> row=one("""
            select id,code,name,category,description,price,active,display_order,image_url,image_key,
                   image_original_name,image_content_type,image_size,created_at,updated_at
            from shop_products where id=?
            """,id);
        addProductImageUrl(row);return row;
    }

    public ObjectStorage.StoredObject productImage(long id) throws IOException{
        String key=(String)product(id).get("image_key");
        if(key==null)throw new NoSuchElementException();
        return storage.load(key);
    }

    public List<Map<String,Object>> publicInnovation(){
        List<Map<String,Object>> rows=jdbc.queryForList("""
            select id,category,title,description,file_original_name,file_size,created_at
            from innovation_resources where published=true order by display_order,created_at desc,id desc
            """);
        rows.forEach(row->row.put("imageUrl","/api/public/content/innovation/"+row.get("id")+"/image"));
        return rows;
    }

    public List<Map<String,Object>> adminInnovation(int limit,int offset){
        List<Map<String,Object>> rows=jdbc.queryForList("""
            select id,category,title,description,file_original_name,file_content_type,file_size,published,display_order,created_at,updated_at
            from innovation_resources order by display_order,created_at desc,id desc limit ? offset ?
            """,pageSize(limit),pageOffset(offset));
        rows.forEach(row->row.put("imageUrl","/api/public/content/innovation/"+row.get("id")+"/image"));
        return rows;
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> createInnovation(String category,String title,String description,int displayOrder,boolean published,
        String password,MultipartFile image,MultipartFile file) throws IOException{
        String cleanPassword=required(password,"다운로드 비밀번호",100);
        if(cleanPassword.length()<4)throw new IllegalArgumentException("다운로드 비밀번호는 4자 이상 입력해 주세요.");
        String imageExt=validation.validateImage(image),fileExt=validation.validateResourceFile(file);
        String reference=UUID.randomUUID().toString();
        String imageKey=StorageKeys.innovationImage(reference,UUID.randomUUID()+"."+imageExt);
        String fileKey=StorageKeys.innovationFile(reference,UUID.randomUUID()+"."+fileExt);
        storage.store(image,imageKey);storage.store(file,fileKey);
        Long id=jdbc.queryForObject("""
            insert into innovation_resources(category,title,description,image_key,image_original_name,image_content_type,image_size,
                file_key,file_original_name,file_content_type,file_size,password_hash,published,display_order)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?) returning id
            """,Long.class,innovationCategory(category),required(title,"제목",180),optional(description,10000),imageKey,originalName(image),contentType(image),image.getSize(),
            fileKey,originalName(file),contentType(file),file.getSize(),passwordEncoder.encode(cleanPassword),published,displayOrder);
        return innovationAdmin(id);
    }

    @Transactional
    public void innovationStatus(long id,boolean published){
        if(jdbc.update("update innovation_resources set published=?,updated_at=current_timestamp where id=?",published,id)==0)
            throw new NoSuchElementException();
    }

    @Transactional(rollbackFor=Exception.class)
    public void deleteInnovation(long id){
        Map<String,Object> row=innovation(id);
        jdbc.update("delete from innovation_resources where id=?",id);
        deleteAfterCommit((String)row.get("image_key"));deleteAfterCommit((String)row.get("file_key"));
    }

    public Map<String,Object> innovation(long id){
        return one("select * from innovation_resources where id=?",id);
    }

    private Map<String,Object> innovationAdmin(long id){
        Map<String,Object> row=one("""
            select id,category,title,description,file_original_name,file_content_type,file_size,published,display_order,created_at,updated_at
            from innovation_resources where id=?
            """,id);
        row.put("imageUrl","/api/public/content/innovation/"+id+"/image");
        return row;
    }

    public ObjectStorage.StoredObject innovationImage(long id,boolean publicOnly) throws IOException{
        Map<String,Object> row=innovation(id);
        if(publicOnly&&!Boolean.TRUE.equals(row.get("published")))throw new NoSuchElementException();
        return storage.load((String)row.get("image_key"));
    }

    public Download innovationDownload(long id,String password) throws IOException{
        Map<String,Object> row=innovation(id);
        if(!Boolean.TRUE.equals(row.get("published")))throw new NoSuchElementException();
        if(password==null||!passwordEncoder.matches(password,(String)row.get("password_hash")))
            throw new org.springframework.security.access.AccessDeniedException("다운로드 비밀번호가 올바르지 않습니다.");
        return new Download(storage.load((String)row.get("file_key")),(String)row.get("file_original_name"),(String)row.get("file_content_type"));
    }

    public List<Map<String,Object>> publicPosts(String type){
        String cleanType=postType(type);
        List<Map<String,Object>> rows=jdbc.queryForList("""
            select id,post_type,title,content,pinned,created_at
            from customer_media_posts where post_type=? and published=true
            order by pinned desc,created_at desc,id desc
            """,cleanType);
        rows.forEach(row->row.put("imageUrl","/api/public/content/posts/"+row.get("id")+"/image"));
        return rows;
    }

    public List<Map<String,Object>> adminPosts(int limit,int offset){
        List<Map<String,Object>> rows=jdbc.queryForList("""
            select id,post_type,title,content,published,pinned,created_at,updated_at
            from customer_media_posts order by created_at desc,id desc limit ? offset ?
            """,pageSize(limit),pageOffset(offset));
        rows.forEach(row->row.put("imageUrl","/api/public/content/posts/"+row.get("id")+"/image"));
        return rows;
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> createPost(String type,String title,String content,boolean published,boolean pinned,
        MultipartFile image) throws IOException{
        String ext=validation.validateImage(image),reference=UUID.randomUUID().toString();
        String key=StorageKeys.customerPostImage(reference,UUID.randomUUID()+"."+ext);
        storage.store(image,key);
        Long id=jdbc.queryForObject("""
            insert into customer_media_posts(post_type,title,content,image_key,image_original_name,image_content_type,image_size,published,pinned)
            values(?,?,?,?,?,?,?,?,?) returning id
            """,Long.class,postType(type),required(title,"제목",180),optional(content,10000),key,originalName(image),contentType(image),image.getSize(),published,pinned);
        return post(id);
    }

    @Transactional
    public void postStatus(long id,boolean published,boolean pinned){
        if(jdbc.update("update customer_media_posts set published=?,pinned=?,updated_at=current_timestamp where id=?",published,pinned,id)==0)
            throw new NoSuchElementException();
    }

    @Transactional(rollbackFor=Exception.class)
    public void deletePost(long id){
        Map<String,Object> row=post(id);jdbc.update("delete from customer_media_posts where id=?",id);
        deleteAfterCommit((String)row.get("image_key"));
    }

    public Map<String,Object> post(long id){return one("select * from customer_media_posts where id=?",id);}

    public ObjectStorage.StoredObject postImage(long id,boolean publicOnly) throws IOException{
        Map<String,Object> row=post(id);
        if(publicOnly&&!Boolean.TRUE.equals(row.get("published")))throw new NoSuchElementException();
        return storage.load((String)row.get("image_key"));
    }

    private Map<String,Object> one(String sql,Object...args){
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args);
        if(rows.isEmpty())throw new NoSuchElementException();
        return new LinkedHashMap<>(rows.getFirst());
    }
    private void addProductImageUrl(Map<String,Object> row){
        if(row.get("image_key")!=null)row.put("imageUrl","/api/public/shop/products/"+row.get("id")+"/image");
        else row.put("imageUrl",row.get("image_url"));
    }
    private String required(String value,String label,int max){
        String clean=value==null?"":value.trim();
        if(clean.isEmpty())throw new IllegalArgumentException(label+"을(를) 입력해 주세요.");
        if(clean.length()>max)throw new IllegalArgumentException(label+"은(는) "+max+"자까지 입력할 수 있습니다.");
        return clean;
    }
    private String optional(String value,int max){
        if(value==null||value.trim().isEmpty())return null;
        String clean=value.trim();if(clean.length()>max)throw new IllegalArgumentException("입력 가능한 글자 수를 초과했습니다.");return clean;
    }
    private String productCode(String value){
        String clean=required(value,"제품 코드",40).toUpperCase(Locale.ROOT);
        if(!clean.matches("[A-Z0-9_]{2,40}"))throw new IllegalArgumentException("제품 코드는 영문 대문자, 숫자, 밑줄만 사용할 수 있습니다.");
        return clean;
    }
    private Long price(Long value){
        if(value!=null&&value<0)throw new IllegalArgumentException("제품 가격은 0원 이상으로 입력해 주세요.");
        return value;
    }
    private String postType(String type){
        String clean=type==null?"":type.toUpperCase(Locale.ROOT);
        if(!POST_TYPES.contains(clean))throw new IllegalArgumentException("게시물 구분을 확인해 주세요.");
        return clean;
    }
    private String innovationCategory(String category){
        String clean=category==null?"TECHNICAL":category.toUpperCase(Locale.ROOT);
        if(!INNOVATION_CATEGORIES.contains(clean))throw new IllegalArgumentException("기술자료 분류를 확인해 주세요.");
        return clean;
    }
    private int pageSize(int limit){return Math.max(1,Math.min(limit,100));}
    private int pageOffset(int offset){return Math.max(0,offset);}
    private String originalName(MultipartFile file){return StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(),"file"));}
    private String contentType(MultipartFile file){return Objects.requireNonNullElse(file.getContentType(),"application/octet-stream");}
    private void deleteAfterCommit(String key){
        if(key==null||key.isBlank())return;
        if(!TransactionSynchronizationManager.isSynchronizationActive()){try{storage.delete(key);}catch(IOException ignored){}return;}
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){try{storage.delete(key);}catch(IOException ignored){}}
        });
    }

    public record Download(ObjectStorage.StoredObject object,String filename,String contentType){}
}
