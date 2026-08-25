package kr.co.kumsungenc.platform.shop;

import kr.co.kumsungenc.platform.file.FileStorageService;
import kr.co.kumsungenc.platform.file.FileValidationService;
import kr.co.kumsungenc.platform.file.StorageKeys;
import kr.co.kumsungenc.platform.integration.GoogleSheetsOutboxService;
import kr.co.kumsungenc.platform.notification.EmailOutboxService;
import kr.co.kumsungenc.platform.privacy.PrivacyConsentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ShopInquiryService {
    private final JdbcTemplate jdbc;private final EmailOutboxService emailOutbox;private final GoogleSheetsOutboxService sheets;
    private final PrivacyConsentService privacy;private final String recipient;
    private final FileValidationService fileValidation;private final FileStorageService fileStorage;

    public ShopInquiryService(JdbcTemplate jdbc,EmailOutboxService emailOutbox,GoogleSheetsOutboxService sheets,
        PrivacyConsentService privacy,@Value("${app.quote-recipient}") String recipient,
        FileValidationService fileValidation,FileStorageService fileStorage){
        this.jdbc=jdbc;this.emailOutbox=emailOutbox;this.sheets=sheets;this.privacy=privacy;this.recipient=recipient;
        this.fileValidation=fileValidation;this.fileStorage=fileStorage;
    }

    public record Item(Long productId,Integer quantity,String specifications,List<Integer> attachmentIndexes){}
    public record Request(String companyName,String contactName,String phone,String email,String message,
        Boolean privacyAgreed,List<Item> items,String submissionKey,String website){}

    @Transactional(rollbackFor=Exception.class)
    public Map<String,String> submit(Request request,List<MultipartFile> files,String ip,String userAgent) throws IOException{
        List<MultipartFile> uploads=files==null?List.of():files.stream().filter(Objects::nonNull).filter(f->!f.isEmpty()).toList();
        rejectBot(request.website());validate(request,uploads.size());
        if(!uploads.isEmpty()){
            fileValidation.validateBatch(uploads);
            for(MultipartFile file:uploads)fileValidation.validateQuoteFile(file);
        }
        String submissionKey=submissionKey(request.submissionKey());Map<String,String> duplicate=existing(submissionKey);if(duplicate!=null)return duplicate;
        List<Long> productIds=request.items().stream().map(Item::productId).distinct().toList();Map<Long,Map<String,Object>> products=new HashMap<>();
        String placeholders=String.join(",",Collections.nCopies(productIds.size(),"?"));
        for(Map<String,Object> row:jdbc.queryForList("select id,code,name from shop_products where active=true and id in ("+placeholders+")",productIds.toArray()))
            products.put(((Number)row.get("id")).longValue(),row);
        if(products.size()!=productIds.size())throw new IllegalArgumentException("판매 문의가 불가능한 제품이 포함되어 있습니다.");

        String receipt="SHOP-"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
        Long owner=owner(request.email());Long id=jdbc.queryForObject("""
            insert into shop_inquiries(receipt_number,customer_user_id,company_name,contact_name,phone,email,message,submission_key)
            values (?,?,?,?,?,?,?,?::uuid) returning id
            """,Long.class,receipt,owner,clean(request.companyName()),clean(request.contactName()),clean(request.phone()),clean(request.email()).toLowerCase(),clean(request.message()),submissionKey);
        if(id==null)throw new IllegalStateException("SMART SHOP 문의 저장에 실패했습니다.");

        List<String> storedKeys=new ArrayList<>();List<Map<String,Object>> sheetItems=new ArrayList<>();StringBuilder itemText=new StringBuilder();
        try{
        for(Item item:request.items()){
            Map<String,Object> product=products.get(item.productId());int quantity=item.quantity();String specs=cleanNullable(item.specifications());
            Long itemId=jdbc.queryForObject("""
                insert into shop_inquiry_items(shop_inquiry_id,product_id,product_code,product_name,quantity,specifications)
                values (?,?,?,?,?,?) returning id
                """,Long.class,id,item.productId(),product.get("code"),product.get("name"),quantity,specs);
            if(itemId==null)throw new IllegalStateException("제품 문의 항목 저장에 실패했습니다.");
            List<String> attachmentNames=new ArrayList<>();
            for(Integer index:indexes(item)){
                MultipartFile file=uploads.get(index);String original=StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(),"file"));
                String ext=fileValidation.extension(original);String stored=product.get("code")+"-"+UUID.randomUUID()+"."+ext;
                String key=StorageKeys.shopAttachment(receipt,stored);String sha256=fileStorage.store(file,key);storedKeys.add(key);
                String contentType=Objects.requireNonNullElse(file.getContentType(),"application/octet-stream");
                jdbc.update("""
                    insert into shop_inquiry_attachments(shop_inquiry_id,shop_inquiry_item_id,original_name,stored_name,content_type,file_size,sha256)
                    values (?,?,?,?,?,?,?)
                    """,id,itemId,original,stored,contentType,file.getSize(),sha256);attachmentNames.add(original);
            }
            itemText.append("- ").append(product.get("name")).append(" × ").append(quantity);
            if(specs!=null)itemText.append(" / ").append(specs);if(!attachmentNames.isEmpty())itemText.append(" / 첨부: ").append(String.join(", ",attachmentNames));itemText.append('\n');
            Map<String,Object> sheetItem=new LinkedHashMap<>();sheetItem.put("product",product.get("name"));sheetItem.put("quantity",quantity);
            sheetItem.put("specifications",Objects.toString(specs,""));sheetItem.put("attachments",attachmentNames);sheetItems.add(sheetItem);
        }
        privacy.record("SHOP_INQUIRY",id,request.email(),ip,userAgent);
        Map<String,Object> payload=new LinkedHashMap<>();payload.put("receiptNumber",receipt);payload.put("companyName",clean(request.companyName()));payload.put("contactName",clean(request.contactName()));
        payload.put("phone",clean(request.phone()));payload.put("email",clean(request.email()).toLowerCase());payload.put("message",clean(request.message()));payload.put("items",sheetItems);
        sheets.enqueue("SHOP_INQUIRY","SHOP_INQUIRY",id,payload);
        String body="SMART SHOP 문의가 접수되었습니다.\n접수번호: "+receipt+"\n회사명: "+clean(request.companyName())+"\n담당자: "+clean(request.contactName())+
            "\n연락처: "+clean(request.phone())+"\n\n[문의 제품]\n"+itemText+"\n[문의내용]\n"+clean(request.message());
        emailOutbox.enqueue("SHOP_INQUIRY",id,recipient,"[SMART SHOP 접수] "+receipt,body);
        emailOutbox.enqueue("SHOP_INQUIRY",id,clean(request.email()).toLowerCase(),"[(주)금성이엔씨] SMART SHOP 문의 접수 - "+receipt,body+"\n\n담당자 확인 후 연락드리겠습니다.");
        return Map.of("receiptNumber",receipt,"message","SMART SHOP 문의가 접수되었습니다.");
        }catch(IOException|RuntimeException e){
            for(String key:storedKeys)try{fileStorage.delete(key);}catch(IOException ignored){}
            throw e;
        }
    }

    private List<Integer> indexes(Item item){return item.attachmentIndexes()==null?List.of():item.attachmentIndexes();}
    private Map<String,String> existing(String key){if(key==null)return null;jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},key);List<String> found=jdbc.query("select receipt_number from shop_inquiries where submission_key=?::uuid",(rs,n)->rs.getString(1),key);return found.isEmpty()?null:Map.of("receiptNumber",found.getFirst(),"message","이미 접수된 SMART SHOP 문의입니다.");}
    private String submissionKey(String value){if(value==null||value.isBlank())return null;try{return UUID.fromString(value.trim()).toString();}catch(IllegalArgumentException e){throw new IllegalArgumentException("문의 요청 식별값이 올바르지 않습니다.");}}
    private void rejectBot(String website){if(website!=null&&!website.isBlank())throw new IllegalArgumentException("문의 접수를 처리할 수 없습니다.");}
    private void validate(Request r,int fileCount){
        text(r.companyName(),150,"회사명");text(r.contactName(),100,"담당자");text(r.phone(),30,"연락처");text(r.email(),120,"이메일");text(r.message(),5000,"문의내용");
        if(!r.phone().matches("^[0-9+()\\-\\s]+$")||r.phone().replaceAll("\\D","").length()<7)throw new IllegalArgumentException("연락처 형식을 확인해 주세요.");
        if(!r.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new IllegalArgumentException("이메일 형식을 확인해 주세요.");
        if(!Boolean.TRUE.equals(r.privacyAgreed()))throw new IllegalArgumentException("개인정보 수집 및 이용에 동의해 주세요.");
        if(r.items()==null||r.items().isEmpty()||r.items().size()>4)throw new IllegalArgumentException("문의할 제품을 1개 이상 선택해 주세요.");
        Set<Long> productIds=new HashSet<>();Set<Integer> claimed=new HashSet<>();
        for(Item item:r.items()){
            if(item==null||item.productId()==null||!productIds.add(item.productId())||item.quantity()==null||item.quantity()<1||item.quantity()>1000)throw new IllegalArgumentException("제품과 수량을 확인해 주세요.");
            if(item.specifications()!=null&&item.specifications().length()>1000)throw new IllegalArgumentException("제품별 요청사항은 1000자 이하로 입력해 주세요.");
            for(Integer index:indexes(item))if(index==null||index<0||index>=fileCount||!claimed.add(index))throw new IllegalArgumentException("제품 첨부파일 연결 정보가 올바르지 않습니다.");
        }
        if(claimed.size()!=fileCount)throw new IllegalArgumentException("제품에 연결되지 않은 첨부파일이 있습니다.");
    }
    private Long owner(String email){List<Long> ids=jdbc.query("select id from app_users where lower(email)=lower(?) and role='CUSTOMER' and email_verified=true and enabled=true",(rs,n)->rs.getLong(1),email);return ids.isEmpty()?null:ids.getFirst();}
    private void text(String v,int max,String label){if(v==null||v.isBlank()||v.trim().length()>max)throw new IllegalArgumentException(label+" 값을 "+max+"자 이내로 입력해 주세요.");}
    private String clean(String v){return v.trim();}private String cleanNullable(String v){return v==null||v.isBlank()?null:v.trim();}
}
