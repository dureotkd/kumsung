package kr.co.kumsungenc.platform.quote;

import org.springframework.beans.factory.annotation.Value;
import kr.co.kumsungenc.platform.file.FileValidationService;
import kr.co.kumsungenc.platform.file.FileStorageService;
import kr.co.kumsungenc.platform.file.StorageKeys;
import kr.co.kumsungenc.platform.notification.EmailOutboxService;
import kr.co.kumsungenc.platform.integration.GoogleSheetsOutboxService;
import kr.co.kumsungenc.platform.privacy.PrivacyConsentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class QuoteService {
    private final QuoteRequestRepository repository;
    private final String recipient;
    private final JdbcTemplate jdbc;
    private final FileValidationService fileValidation;
    private final EmailOutboxService outbox;private final PrivacyConsentService privacy;
    private final FileStorageService fileStorage;
    private final TransactionTemplate transactions;
    private final GoogleSheetsOutboxService sheets;

    public QuoteService(QuoteRequestRepository repository,
                        @Value("${app.quote-recipient}") String recipient,
                        JdbcTemplate jdbc,FileValidationService fileValidation,
                        FileStorageService fileStorage,EmailOutboxService outbox,PrivacyConsentService privacy,
                        TransactionTemplate transactions,GoogleSheetsOutboxService sheets) {
        this.repository = repository;
        this.recipient = recipient; this.jdbc = jdbc;
        this.fileValidation=fileValidation;this.fileStorage=fileStorage;this.outbox=outbox;this.privacy=privacy;
        this.transactions=transactions;
        this.sheets=sheets;
    }

    public QuoteRequest submit(QuoteForm form, List<MultipartFile> files,String ip,String userAgent) throws IOException {
        fileValidation.validateBatch(files);
        for(MultipartFile file:files)if(!file.isEmpty())fileValidation.validateQuoteFile(file);
        QuoteRequest q = new QuoteRequest();
        q.setReceiptNumber("KS-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        q.setCompanyName(form.companyName()); q.setBusinessNumber(form.businessNumber());
        q.setContactName(form.contactName()); q.setEmail(form.email()); q.setPhone(form.phone());
        q.setSiteName(form.siteName()); q.setSiteAddress(form.siteAddress());
        q.setProductType(form.productType()); q.setSubject(form.subject());
        q.setDetails(form.details()); q.setCustomerWebhardUrl(blankToNull(form.webhardUrl()));
        q.setDesiredDate(form.desiredDate());
        List<Long> owners=jdbc.query("select id from app_users where lower(email)=lower(?) and role='CUSTOMER' and email_verified=true and enabled=true",
            (rs,n)->rs.getLong(1),form.email());
        if(!owners.isEmpty())q.setOwnerUserId(owners.getFirst());

        List<String> storedKeys=new ArrayList<>();
        try{
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                String original = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "file"));
                String ext = fileValidation.extension(original);
                String stored = UUID.randomUUID() + (ext.isBlank()?"":"." + ext);
                String key=StorageKeys.quoteAttachment(q.getReceiptNumber(),stored);
                fileStorage.store(file,key);storedKeys.add(key);
                q.addAttachment(new QuoteAttachment(original, stored,
                        Objects.requireNonNullElse(file.getContentType(), "application/octet-stream"), file.getSize()));
            }
            QuoteRequest saved=transactions.execute(status -> {
                QuoteRequest persisted=repository.save(q);
                repository.flush();
                jdbc.update("insert into quote_status_history(quote_request_id,status,note,changed_by) values (?,?,?,?)",
                    persisted.getId(), "RECEIVED", "온라인 견적 접수", persisted.getEmail());
                privacy.record("QUOTE",persisted.getId(),persisted.getEmail(),ip,userAgent);
                Map<String,Object> sheetPayload=new LinkedHashMap<>();
                sheetPayload.put("receiptNumber",persisted.getReceiptNumber());sheetPayload.put("companyName",persisted.getCompanyName());
                sheetPayload.put("contactName",persisted.getContactName());sheetPayload.put("phone",persisted.getPhone());
                sheetPayload.put("email",persisted.getEmail());sheetPayload.put("productType",persisted.getProductType());
                sheetPayload.put("subject",persisted.getSubject());sheetPayload.put("details",persisted.getDetails());
                sheets.enqueue("QUOTE_INQUIRY","QUOTE",persisted.getId(),sheetPayload);
                sendNotifications(persisted);
                return persisted;
            });
            if(saved==null)throw new IllegalStateException("견적 저장 트랜잭션을 완료하지 못했습니다.");
            return saved;
        }catch(IOException|RuntimeException e){
            for(String key:storedKeys)try{fileStorage.delete(key);}catch(IOException ignored){}
            throw e;
        }
    }

    private void sendNotifications(QuoteRequest q) {
        String body = """
            (주)금성이엔씨 온라인 견적이 접수되었습니다.
            접수번호: %s
            회사명: %s
            담당자: %s
            연락처: %s
            제품/공종: %s
            제목: %s
            """.formatted(q.getReceiptNumber(), q.getCompanyName(), q.getContactName(),
                q.getPhone(), q.getProductType(), q.getSubject());
        outbox.enqueue(q.getId(),recipient,"[견적접수] "+q.getReceiptNumber()+" "+q.getSubject(),body);
        outbox.enqueue(q.getId(),q.getEmail(),"[(주)금성이엔씨] 견적 요청이 접수되었습니다",
            body+"\n담당자 검토 후 연락드리겠습니다.");
    }
    private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
}
