package kr.co.kumsungenc.platform.admin;

import kr.co.kumsungenc.platform.file.FileValidationService;
import kr.co.kumsungenc.platform.file.FileStorageService;
import kr.co.kumsungenc.platform.file.ObjectStorage;
import kr.co.kumsungenc.platform.file.StorageKeys;
import kr.co.kumsungenc.platform.notification.EmailOutboxService;
import kr.co.kumsungenc.platform.notification.SmtpConfigurationService;
import kr.co.kumsungenc.platform.document.EstimateDocumentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final Set<String> STATUSES=Set.of("RECEIVED","REVIEWING","SUPPLEMENT_REQUIRED","SUPPLEMENTED","QUOTED","COMPLETED","CANCELLED");
    private static final Set<String> DOC_TYPES=Set.of("ESTIMATE","CONTRACT","OTHER");
    private static final Set<String> PROJECT_STATUSES=Set.of("PLANNING","IN_PROGRESS","ON_HOLD","COMPLETED","CANCELLED");
    private static final Set<String> CONTRACT_STATUSES=Set.of("DRAFT","REVIEWING","SIGNED","COMPLETED","CANCELLED");
    private static final Set<String> DELIVERY_STATUSES=Set.of("PREPARING","SHIPPING","DELIVERED","CANCELLED");
    private static final Set<String> INVOICE_STATUSES=Set.of("PENDING","ISSUED","PAID","CANCELLED");
    private static final Set<String> SERVICE_STATUSES=Set.of("RECEIVED","REVIEWING","IN_PROGRESS","COMPLETED","CANCELLED");
    private static final Set<String> SHOP_STATUSES=Set.of("RECEIVED","REVIEWING","CONTACTED","COMPLETED","CANCELLED");
    private static final Set<String> SUPPORT_STATUSES=Set.of("RECEIVED","REVIEWING","ANSWERED","CLOSED","CANCELLED");
    private final JdbcTemplate jdbc;
    private final FileValidationService fileValidation; private final FileStorageService fileStorage;
    private final EmailOutboxService outbox; private final EstimateDocumentService estimates;
    private final SmtpConfigurationService smtpConfiguration;
    private final TransactionTemplate transactions;
    private final String supportEmail;
    public AdminController(JdbcTemplate jdbc,
        FileValidationService fileValidation,FileStorageService fileStorage,EmailOutboxService outbox,
        EstimateDocumentService estimates,SmtpConfigurationService smtpConfiguration,TransactionTemplate transactions,
        @Value("${app.support-email:support@kumsungenc.co.kr}") String supportEmail){
        this.jdbc=jdbc;
        this.fileValidation=fileValidation;this.fileStorage=fileStorage;this.outbox=outbox;this.estimates=estimates;
        this.smtpConfiguration=smtpConfiguration;
        this.transactions=transactions;
        this.supportEmail=supportEmail;
    }
    @GetMapping("/dashboard") public Map<String,Object> dashboard(){
        return jdbc.queryForMap("""
            select
              (select count(*) from quote_requests where status='RECEIVED') as received,
              (select count(*) from quote_requests where status in ('REVIEWING','SUPPLEMENT_REQUIRED','SUPPLEMENTED')) as reviewing,
              (select count(*) from quote_requests where status='COMPLETED') as completed,
              (select count(*) from app_users where role='CUSTOMER') as customers,
              (select count(*) from service_requests where status not in ('COMPLETED','CANCELLED')) as "serviceRequests",
              (select count(*) from support_inquiries where status in ('RECEIVED','REVIEWING')) as "supportOpen",
              (select count(*) from shop_inquiries where status in ('RECEIVED','REVIEWING')) as "shopOpen"
            """);
    }
    @GetMapping("/quotes") public List<Map<String,Object>> quotes(@RequestParam(required=false) String status,@RequestParam(required=false) String keyword,
        @RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){
        StringBuilder sql=new StringBuilder("""
          select id,receipt_number,company_name,contact_name,email,phone,product_type,subject,status,assigned_to,created_at,updated_at
          from quote_requests where 1=1
          """); List<Object> args=new ArrayList<>();
        if(status!=null&&!status.isBlank()){requireStatus(status,STATUSES);sql.append(" and status=?");args.add(status);}
        if(keyword!=null&&!keyword.isBlank()){
            String term=keyword.trim();if(term.length()>100)throw new IllegalArgumentException("검색어는 100자 이하로 입력해 주세요.");
            sql.append(" and (receipt_number ilike ? or company_name ilike ? or subject ilike ?)");String k="%"+term+"%";args.add(k);args.add(k);args.add(k);
        }
        sql.append(" order by created_at desc,id desc limit ? offset ?");args.add(pageSize(limit));args.add(pageOffset(offset));
        return jdbc.queryForList(sql.toString(),args.toArray());
    }
    @GetMapping("/quotes/{id}") public Map<String,Object> quote(@PathVariable long id){
        Map<String,Object> r=new LinkedHashMap<>(jdbc.queryForMap("select * from quote_requests where id=?",id));
        r.put("history",jdbc.queryForList("select * from quote_status_history where quote_request_id=? order by created_at",id));
        r.put("attachments",jdbc.queryForList("select id,original_name,file_size,content_type from quote_attachments where quote_request_id=?",id));
        r.put("documents",jdbc.queryForList("select id,document_type,title,original_name,approval_status,created_at from quote_documents where quote_request_id=?",id));
        r.put("messages",jdbc.queryForList("select * from quote_messages where quote_request_id=? order by created_at",id));
        return r;
    }
    @GetMapping("/quotes/{id}/files/{fileId}")
    public ResponseEntity<Resource> attachment(@PathVariable long id,@PathVariable long fileId) throws IOException{
        Map<String,Object> row=jdbc.queryForMap("""
            select q.receipt_number,a.original_name,a.stored_name,a.content_type
            from quote_attachments a join quote_requests q on q.id=a.quote_request_id
            where a.id=? and a.quote_request_id=?
            """,fileId,id);
        return resource(StorageKeys.quoteAttachment((String)row.get("receipt_number"),(String)row.get("stored_name")),
            (String)row.get("original_name"),(String)row.get("content_type"));
    }
    @GetMapping("/quotes/{id}/documents/{documentId}")
    public ResponseEntity<Resource> documentDownload(@PathVariable long id,@PathVariable long documentId) throws IOException{
        Map<String,Object> row=jdbc.queryForMap("""
            select q.receipt_number,d.original_name,d.stored_name,d.content_type
            from quote_documents d join quote_requests q on q.id=d.quote_request_id
            where d.id=? and d.quote_request_id=?
            """,documentId,id);
        return resource(StorageKeys.quoteDocument((String)row.get("receipt_number"),(String)row.get("stored_name")),
            (String)row.get("original_name"),(String)row.get("content_type"));
    }
    public record StatusRequest(String status,String note,String assignedTo){}
    @PutMapping("/quotes/{id}/status") @Transactional
    public Map<String,String> status(@PathVariable long id,@RequestBody StatusRequest r,Principal p){
        if(r.status()==null||!STATUSES.contains(r.status()))throw new IllegalArgumentException("올바르지 않은 상태입니다.");
        requireOptionalText(r.note(),500,"안내 내용");requireOptionalText(r.assignedTo(),120,"담당자");
        Map<String,Object> q=jdbc.queryForMap("select receipt_number,email,subject from quote_requests where id=?",id);
        jdbc.update("update quote_requests set status=?,assigned_to=?,updated_at=current_timestamp where id=?",r.status(),r.assignedTo(),id);
        jdbc.update("insert into quote_status_history(quote_request_id,status,note,changed_by) values (?,?,?,?)",id,r.status(),r.note(),p.getName());
        outbox.enqueue(id,(String)q.get("email"),
            "[(주)금성이엔씨] 견적 진행상태 안내 - "+q.get("receipt_number"),
            "견적 요청 상태가 "+r.status()+"(으)로 변경되었습니다.\n"+Objects.requireNonNullElse(r.note(),""));
        return Map.of("message","진행상태가 변경되었습니다.");
    }
    public record SupplementRequest(String requestText){}
    @PostMapping("/quotes/{id}/supplemental-requests") @Transactional
    public Map<String,String> supplement(@PathVariable long id,@RequestBody SupplementRequest r,Principal p){
        requireText(r.requestText(),5000,"보완 요청 내용");
        Map<String,Object> q=jdbc.queryForMap("select receipt_number,email from quote_requests where id=?",id);
        jdbc.update("insert into supplemental_requests(quote_request_id,request_text,requested_by) values (?,?,?)",id,r.requestText(),p.getName());
        jdbc.update("update quote_requests set status='SUPPLEMENT_REQUIRED',updated_at=current_timestamp where id=?",id);
        jdbc.update("insert into quote_status_history(quote_request_id,status,note,changed_by) values (?,'SUPPLEMENT_REQUIRED',?,?)",id,r.requestText(),p.getName());
        outbox.enqueue(id,(String)q.get("email"),
            "[(주)금성이엔씨] 보완자료 요청 - "+q.get("receipt_number"),r.requestText());
        return Map.of("message","보완자료 요청을 전송했습니다.");
    }
    @PostMapping(value="/quotes/{id}/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,String> document(@PathVariable long id,@RequestParam String documentType,@RequestParam String title,
        @RequestPart MultipartFile file,Principal p) throws IOException{
        if(!DOC_TYPES.contains(documentType)||file.isEmpty())throw new IllegalArgumentException("문서 정보를 확인해 주세요.");
        requireText(title,200,"문서 제목");
        Map<String,Object> quote=jdbc.queryForMap("select receipt_number,email from quote_requests where id=?",id);
        String receipt=(String)quote.get("receipt_number");
        String original=StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(),"document"));
        String ext=fileValidation.validateDocument(file);
        String stored=UUID.randomUUID()+"."+ext;String key=StorageKeys.quoteDocument(receipt,stored);
        String sha256=fileStorage.store(file,key);
        try{
            transactions.executeWithoutResult(status -> {
                jdbc.update("insert into quote_documents(quote_request_id,document_type,title,original_name,stored_name,content_type,file_size,content_sha256) values (?,?,?,?,?,?,?,?)",
                    id,documentType,title,original,stored,Objects.requireNonNullElse(file.getContentType(),"application/octet-stream"),file.getSize(),sha256);
                if("ESTIMATE".equals(documentType)){
                    jdbc.update("update quote_requests set status='QUOTED',updated_at=current_timestamp where id=?",id);
                    jdbc.update("insert into quote_status_history(quote_request_id,status,note,changed_by) values (?,'QUOTED','견적서 등록',?)",id,p.getName());
                }
                outbox.enqueue(id,(String)quote.get("email"),"[(주)금성이엔씨] 새 문서 등록 - "+receipt,
                    title+" 문서가 고객 포털에 등록되었습니다. 로그인 후 확인해 주세요.");
            });
            return Map.of("message","문서가 등록되었습니다.");
        }catch(RuntimeException e){
            try{fileStorage.delete(key);}catch(IOException ignored){}
            throw e;
        }
    }
    @PostMapping("/quotes/{id}/documents/generate")
    public Map<String,Object> generateDocuments(@PathVariable long id,
        @RequestBody EstimateDocumentService.GenerateRequest request,Principal p) throws IOException{
        return estimates.generate(id,request,p);
    }
    public record AdminMessage(String message){}
    @PostMapping("/quotes/{id}/messages") @Transactional
    public Map<String,String> message(@PathVariable long id,@RequestBody AdminMessage r,Principal p){
        requireText(r.message(),5000,"메시지");
        jdbc.update("insert into quote_messages(quote_request_id,sender_email,sender_role,message) values (?,?,?,?)",id,p.getName(),"ADMIN",r.message());
        Map<String,Object> q=jdbc.queryForMap("select receipt_number,email from quote_requests where id=?",id);
        outbox.enqueue(id,(String)q.get("email"),"[(주)금성이엔씨] 담당자 메시지 - "+q.get("receipt_number"),
            r.message()+"\n\n고객 포털에서 답변하실 수 있습니다.");
        return Map.of("message","메시지를 전송했습니다.");
    }
    public record WebhardRequest(String url){}
    @PutMapping("/quotes/{id}/webhard") public Map<String,String> webhard(@PathVariable long id,@RequestBody WebhardRequest r){
        requireOptionalText(r.url(),500,"웹하드 주소");
        if(r.url()!=null&&!r.url().isBlank()&&!r.url().matches("^https?://.+"))
            throw new IllegalArgumentException("http 또는 https 웹하드 주소를 입력해 주세요.");
        jdbc.update("update quote_requests set webhard_url=?,updated_at=current_timestamp where id=?",r.url(),id);
        return Map.of("message","웹하드 공유 주소가 저장되었습니다.");
    }
    @GetMapping("/customers") public List<Map<String,Object>> customers(@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){
        return jdbc.queryForList("select id,email,name,company_name,phone,enabled,created_at from app_users where role='CUSTOMER' order by created_at desc,id desc limit ? offset ?",pageSize(limit),pageOffset(offset));}
    @PutMapping("/customers/{id}/enabled") public Map<String,String> customerEnabled(@PathVariable long id,@RequestBody Map<String,Boolean> r){
        if(jdbc.update("update app_users set enabled=? where id=? and role='CUSTOMER'",Boolean.TRUE.equals(r.get("enabled")),id)!=1)
            throw new NoSuchElementException("고객을 찾을 수 없습니다.");
        return Map.of("message","고객 계정 상태가 변경되었습니다.");
    }
    @GetMapping("/service-requests") public List<Map<String,Object>> services(@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){
        return jdbc.queryForList("select * from service_requests order by created_at desc,id desc limit ? offset ?",pageSize(limit),pageOffset(offset));}
    @PutMapping("/service-requests/{id}/status") @Transactional
    public Map<String,String> serviceStatus(@PathVariable long id,@RequestBody Map<String,String> r){
        requireStatus(r.get("status"),SERVICE_STATUSES);
        Map<String,Object> item=jdbc.queryForMap("select customer_email,title from service_requests where id=?",id);
        jdbc.update("update service_requests set status=?,updated_at=current_timestamp where id=?",r.get("status"),id);
        outbox.enqueue(null,(String)item.get("customer_email"),"[(주)금성이엔씨] A/S 진행상태 안내",
            item.get("title")+" 요청의 상태가 "+r.get("status")+"(으)로 변경되었습니다.");
        return Map.of("message","A/S 상태가 변경되었습니다.");
    }
    @GetMapping("/support") public List<Map<String,Object>> support(@RequestParam(required=false) String status,
        @RequestParam(required=false) String keyword,@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){
        StringBuilder sql=new StringBuilder("select * from support_inquiries where 1=1");List<Object> args=new ArrayList<>();
        if(status!=null&&!status.isBlank()){requireStatus(status,SUPPORT_STATUSES);sql.append(" and status=?");args.add(status);}
        if(keyword!=null&&!keyword.isBlank()){
            String term=keyword.trim();if(term.length()>100)throw new IllegalArgumentException("검색어는 100자 이내로 입력해 주세요.");
            String value="%"+term+"%";
            sql.append(" and (receipt_number ilike ? or company_name ilike ? or contact_name ilike ? or customer_email ilike ? or subject ilike ?)");
            for(int i=0;i<5;i++)args.add(value);
        }
        sql.append(" order by created_at desc,id desc limit ? offset ?");args.add(pageSize(limit));args.add(pageOffset(offset));
        return jdbc.queryForList(sql.toString(),args.toArray());
    }
    @GetMapping("/support/{id}") public Map<String,Object> supportDetail(@PathVariable long id){
        Map<String,Object> result=new LinkedHashMap<>(jdbc.queryForMap("select * from support_inquiries where id=?",id));
        result.put("history",jdbc.queryForList("select status,note,changed_by,created_at from support_inquiry_history where support_inquiry_id=? order by created_at,id",id));
        result.put("emails",jdbc.queryForList("""
            select id,recipient,subject,status,attempts,last_error,created_at,sent_at
            from email_outbox where reference_type='SUPPORT_INQUIRY' and reference_id=? order by id desc
            """,id));
        return result;
    }
    @GetMapping("/shop/inquiries") public List<Map<String,Object>> shopInquiries(@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){
        return jdbc.queryForList("""
            select s.*,coalesce(string_agg(i.product_name||' × '||i.quantity||coalesce(' / '||i.specifications,''),', ' order by i.id),'') as items,
              (select count(*) from shop_inquiry_attachments a where a.shop_inquiry_id=s.id) as attachment_count
            from shop_inquiries s left join shop_inquiry_items i on i.shop_inquiry_id=s.id
            group by s.id order by s.created_at desc,s.id desc limit ? offset ?
            """,pageSize(limit),pageOffset(offset));
    }
    @GetMapping("/shop/inquiries/{id}") public Map<String,Object> shopInquiry(@PathVariable long id){
        Map<String,Object> result=new LinkedHashMap<>(jdbc.queryForMap("select * from shop_inquiries where id=?",id));
        result.put("items",jdbc.queryForList("select id,product_code,product_name,quantity,specifications from shop_inquiry_items where shop_inquiry_id=? order by id",id));
        result.put("attachments",jdbc.queryForList("""
            select a.id,a.shop_inquiry_item_id,a.original_name,a.content_type,a.file_size,i.product_name
            from shop_inquiry_attachments a join shop_inquiry_items i on i.id=a.shop_inquiry_item_id
            where a.shop_inquiry_id=? order by i.id,a.id
            """,id));
        return result;
    }
    @GetMapping("/shop/inquiries/{id}/files/{fileId}")
    public ResponseEntity<Resource> shopAttachment(@PathVariable long id,@PathVariable long fileId) throws IOException{
        Map<String,Object> row=jdbc.queryForMap("""
            select s.receipt_number,a.original_name,a.stored_name,a.content_type
            from shop_inquiry_attachments a join shop_inquiries s on s.id=a.shop_inquiry_id
            where a.id=? and a.shop_inquiry_id=?
            """,fileId,id);
        return resource(StorageKeys.shopAttachment((String)row.get("receipt_number"),(String)row.get("stored_name")),
            (String)row.get("original_name"),(String)row.get("content_type"));
    }
    public record ShopStatus(String status,String adminNote){}
    @PutMapping("/shop/inquiries/{id}") @Transactional public Map<String,String> shopStatus(@PathVariable long id,@RequestBody ShopStatus r,Principal p){
        requireStatus(r.status(),SHOP_STATUSES);if(r.adminNote()!=null&&r.adminNote().length()>5000)throw new IllegalArgumentException("관리자 메모는 5000자 이하로 입력해 주세요.");
        Map<String,Object> item=jdbc.queryForMap("select receipt_number,email from shop_inquiries where id=?",id);
        jdbc.update("update shop_inquiries set status=?,admin_note=?,updated_at=current_timestamp where id=?",r.status(),blankToNull(r.adminNote()),id);
        jdbc.update("insert into shop_inquiry_history(shop_inquiry_id,status,note,changed_by) values (?,?,?,?)",id,r.status(),blankToNull(r.adminNote()),p.getName());
        jdbc.update("insert into audit_logs(actor_email,action,target_type,target_id,details) values (?,'SHOP_INQUIRY_STATUS','SHOP_INQUIRY',?,?)",
            p.getName(),Long.toString(id),r.status()+" / "+Objects.toString(r.adminNote(),""));
        outbox.enqueue(null,(String)item.get("email"),"[(주)금성이엔씨] SMART SHOP 문의 상태 안내 - "+item.get("receipt_number"),
            "문의 상태가 "+r.status()+"(으)로 변경되었습니다.\n"+Objects.toString(r.adminNote(),""));
        return Map.of("message","SMART SHOP 문의 상태가 변경되었습니다.");
    }
    public record Answer(String answer){}
    @PutMapping("/support/{id}/answer") @Transactional
    public Map<String,String> answer(@PathVariable long id,@RequestBody Answer r,Principal p){
        return supportStatus(id,new SupportStatus("ANSWERED",r.answer(),null,p.getName()),p);
    }
    public record SupportStatus(String status,String answer,String internalNote,String assignedTo){}
    @PutMapping("/support/{id}") @Transactional
    public Map<String,String> supportStatus(@PathVariable long id,@RequestBody SupportStatus r,Principal p){
        requireStatus(r.status(),SUPPORT_STATUSES);requireOptionalText(r.answer(),10000,"고객 답변");
        requireOptionalText(r.internalNote(),5000,"내부 메모");requireOptionalText(r.assignedTo(),120,"담당자");
        Map<String,Object> item=jdbc.queryForMap("select customer_email,receipt_number,subject,answer from support_inquiries where id=?",id);
        String answer=blankToNull(r.answer());
        if("ANSWERED".equals(r.status())&&answer==null&&item.get("answer")==null)
            throw new IllegalArgumentException("답변 완료 상태에는 고객 답변을 입력해 주세요.");
        jdbc.update("""
            update support_inquiries set status=?,answer=coalesce(?,answer),internal_note=?,assigned_to=?,
            answered_at=case when ? is not null then current_timestamp else answered_at end,
            answered_by=case when ? is not null then ? else answered_by end,updated_at=current_timestamp where id=?
            """,r.status(),answer,blankToNull(r.internalNote()),blankToNull(r.assignedTo()),answer,answer,p.getName(),id);
        String note=answer!=null?answer:Objects.toString(r.internalNote(),"");
        jdbc.update("insert into support_inquiry_history(support_inquiry_id,status,note,changed_by) values (?,?,?,?)",id,r.status(),blankToNull(note),p.getName());
        jdbc.update("insert into audit_logs(actor_email,action,target_type,target_id,details) values (?,'SUPPORT_INQUIRY_STATUS','SUPPORT_INQUIRY',?,?)",
            p.getName(),Long.toString(id),r.status()+" / "+note);
        if(answer!=null){
            String mailBody="안녕하세요. (주)금성이엔씨입니다.\n\n문의하신 내용에 답변드립니다.\n\n"+answer+
                "\n\n접수번호: "+Objects.toString(item.get("receipt_number"),"-")+"\n문의 제목: "+item.get("subject")+
                "\n\n추가 문의는 "+supportEmail+"로 회신해 주세요.";
            outbox.enqueue("SUPPORT_INQUIRY",id,(String)item.get("customer_email"),
                "[(주)금성이엔씨] 고객센터 답변 - "+item.get("subject"),mailBody);
        }
        return Map.of("message","고객문의 처리정보가 저장되었습니다.");
    }
    public record ProjectInput(String customerEmail,Long quoteRequestId,String name,String status,LocalDate startDate,LocalDate endDate,Integer progress){}
    @PostMapping("/projects") @Transactional public Map<String,String> project(@RequestBody ProjectInput r){
        Map<String,Object> customer=requireCustomer(r.customerEmail()); requireText(r.name(),200,"프로젝트명");
        if(r.startDate()!=null&&r.endDate()!=null&&r.endDate().isBefore(r.startDate()))throw new IllegalArgumentException("프로젝트 종료일은 시작일 이후여야 합니다.");
        requireOwnedReference("quote_requests",r.quoteRequestId(),((Number)customer.get("id")).longValue());
        String status=Objects.requireNonNullElse(r.status(),"PLANNING");requireStatus(status,PROJECT_STATUSES);
        int progress=Objects.requireNonNullElse(r.progress(),0);
        if(progress<0||progress>100)throw new IllegalArgumentException("진행률은 0~100 사이여야 합니다.");
        jdbc.update("insert into projects(customer_email,customer_user_id,quote_request_id,name,status,start_date,end_date,progress) values (?,?,?,?,?,?,?,?)",
            customer.get("email"),customer.get("id"),r.quoteRequestId(),r.name(),status,r.startDate(),r.endDate(),progress);
        return Map.of("message","프로젝트가 등록되었습니다.");
    }
    public record ContractInput(String customerEmail,Long projectId,String contractNumber,String title,java.math.BigDecimal amount,String status,LocalDate contractDate){}
    @PostMapping("/contracts") @Transactional public Map<String,String> contract(@RequestBody ContractInput r){
        Map<String,Object> customer=requireCustomer(r.customerEmail()); requireText(r.contractNumber(),50,"계약번호"); requireText(r.title(),200,"계약명");
        if(r.amount()!=null&&r.amount().signum()<0)throw new IllegalArgumentException("계약 금액은 0 이상이어야 합니다.");
        requireOwnedReference("projects",r.projectId(),((Number)customer.get("id")).longValue());
        String status=Objects.requireNonNullElse(r.status(),"DRAFT");requireStatus(status,CONTRACT_STATUSES);
        jdbc.update("insert into contracts(customer_email,customer_user_id,project_id,contract_number,title,amount,status,contract_date) values (?,?,?,?,?,?,?,?)",
            customer.get("email"),customer.get("id"),r.projectId(),r.contractNumber(),r.title(),r.amount(),status,r.contractDate());
        return Map.of("message","계약이 등록되었습니다.");
    }
    public record DeliveryInput(String customerEmail,Long projectId,String itemName,String quantity,LocalDate expectedDate,String status){}
    @PostMapping("/deliveries") @Transactional public Map<String,String> delivery(@RequestBody DeliveryInput r){
        Map<String,Object> customer=requireCustomer(r.customerEmail()); requireText(r.itemName(),200,"납품 품목");requireOptionalText(r.quantity(),60,"수량");
        requireOwnedReference("projects",r.projectId(),((Number)customer.get("id")).longValue());
        String status=Objects.requireNonNullElse(r.status(),"PREPARING");requireStatus(status,DELIVERY_STATUSES);
        jdbc.update("insert into deliveries(customer_email,customer_user_id,project_id,item_name,quantity,expected_date,status) values (?,?,?,?,?,?,?)",
            customer.get("email"),customer.get("id"),r.projectId(),r.itemName(),r.quantity(),r.expectedDate(),status);
        return Map.of("message","납품일정이 등록되었습니다.");
    }
    public record InvoiceInput(String customerEmail,Long contractId,String issueNumber,java.math.BigDecimal amount,LocalDate issuedDate,String status){}
    @PostMapping("/tax-invoices") @Transactional public Map<String,String> invoice(@RequestBody InvoiceInput r){
        Map<String,Object> customer=requireCustomer(r.customerEmail()); requireText(r.issueNumber(),60,"발행번호");
        if(r.amount()==null||r.amount().signum()<0)throw new IllegalArgumentException("세금계산서 금액은 0 이상으로 입력해 주세요.");
        requireOwnedReference("contracts",r.contractId(),((Number)customer.get("id")).longValue());
        String status=Objects.requireNonNullElse(r.status(),"PENDING");requireStatus(status,INVOICE_STATUSES);
        jdbc.update("insert into tax_invoices(customer_email,customer_user_id,contract_id,issue_number,amount,issued_date,status) values (?,?,?,?,?,?,?)",
            customer.get("email"),customer.get("id"),r.contractId(),r.issueNumber(),r.amount(),r.issuedDate(),status);
        return Map.of("message","세금계산서 정보가 등록되었습니다.");
    }
    public record NoticeInput(String title,String content,Boolean pinned,Boolean published){}
    @PostMapping("/notices") public Map<String,String> notice(@RequestBody NoticeInput r){
        requireText(r.title(),200,"공지 제목");requireText(r.content(),20000,"공지 내용");
        jdbc.update("insert into notices(title,content,pinned,published) values (?,?,?,?)",r.title(),r.content(),Boolean.TRUE.equals(r.pinned()),!Boolean.FALSE.equals(r.published()));
        return Map.of("message","공지사항이 등록되었습니다.");
    }
    @GetMapping("/projects") public List<Map<String,Object>> projects(@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){return pagedRows("projects","created_at desc,id desc",limit,offset);}
    @GetMapping("/contracts") public List<Map<String,Object>> contracts(@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){return pagedRows("contracts","created_at desc,id desc",limit,offset);}
    @GetMapping("/deliveries") public List<Map<String,Object>> deliveries(@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){return pagedRows("deliveries","created_at desc,id desc",limit,offset);}
    @GetMapping("/tax-invoices") public List<Map<String,Object>> invoices(@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){return pagedRows("tax_invoices","created_at desc,id desc",limit,offset);}
    @GetMapping("/notices") public List<Map<String,Object>> notices(@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){return pagedRows("notices","pinned desc,created_at desc,id desc",limit,offset);}
    public record ProjectStatus(String status,Integer progress){}
    @PutMapping("/projects/{id}") @Transactional
    public Map<String,String> projectStatus(@PathVariable long id,@RequestBody ProjectStatus r){
        int progress=Objects.requireNonNullElse(r.progress(),0);
        if(progress<0||progress>100)throw new IllegalArgumentException("진행률은 0~100 사이여야 합니다.");
        String status=Objects.requireNonNullElse(r.status(),"PLANNING");requireStatus(status,PROJECT_STATUSES);
        jdbc.update("update projects set status=?,progress=? where id=?",status,progress,id);
        notifyBusiness("projects",id,"프로젝트 진행상태",status+" / 진행률 "+progress+"%");
        return Map.of("message","프로젝트 상태가 변경되었습니다.");
    }
    @PutMapping("/contracts/{id}/status") @Transactional
    public Map<String,String> contractStatus(@PathVariable long id,@RequestBody Map<String,String> r){
        requireStatus(r.get("status"),CONTRACT_STATUSES);
        jdbc.update("update contracts set status=? where id=?",r.get("status"),id);
        notifyBusiness("contracts",id,"계약 진행상태",r.get("status"));
        return Map.of("message","계약 상태가 변경되었습니다.");
    }
    public record DeliveryStatus(String status,LocalDate deliveredDate){}
    @PutMapping("/deliveries/{id}/status") @Transactional
    public Map<String,String> deliveryStatus(@PathVariable long id,@RequestBody DeliveryStatus r){
        requireStatus(r.status(),DELIVERY_STATUSES);
        if("DELIVERED".equals(r.status())&&r.deliveredDate()==null)throw new IllegalArgumentException("납품 완료일을 입력해 주세요.");
        jdbc.update("update deliveries set status=?,delivered_date=? where id=?",r.status(),r.deliveredDate(),id);
        notifyBusiness("deliveries",id,"납품 진행상태",r.status());
        return Map.of("message","납품 상태가 변경되었습니다.");
    }
    @PutMapping("/tax-invoices/{id}/status") @Transactional
    public Map<String,String> invoiceStatus(@PathVariable long id,@RequestBody Map<String,String> r){
        requireStatus(r.get("status"),INVOICE_STATUSES);
        jdbc.update("update tax_invoices set status=? where id=?",r.get("status"),id);
        notifyBusiness("tax_invoices",id,"세금계산서 진행상태",r.get("status"));
        return Map.of("message","세금계산서 상태가 변경되었습니다.");
    }
    @PutMapping("/notices/{id}") public Map<String,String> noticeStatus(@PathVariable long id,@RequestBody Map<String,Boolean> r){
        if(jdbc.update("update notices set published=?,pinned=? where id=?",Boolean.TRUE.equals(r.get("published")),Boolean.TRUE.equals(r.get("pinned")),id)!=1)
            throw new NoSuchElementException("공지사항을 찾을 수 없습니다.");
        return Map.of("message","공지 상태가 변경되었습니다.");
    }
    @GetMapping("/email/config")
    public Map<String,Object> emailConfig(){
        return smtpConfiguration.sanitizedView();
    }
    @GetMapping("/email/outbox")
    public List<Map<String,Object>> emailOutbox(@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset){
        return jdbc.queryForList("""
            select id,recipient,subject,status,attempts,next_attempt_at,last_error,created_at,sent_at
            from email_outbox
            order by id desc
            limit ? offset ?
            """,pageSize(limit),pageOffset(offset));
    }
    @PostMapping("/email/outbox/{id}/retry")
    public Map<String,String> retryEmail(@PathVariable long id){
        int changed=jdbc.update("""
            update email_outbox set status='PENDING',attempts=0,next_attempt_at=current_timestamp,last_error=null,
            claimed_at=null,claimed_by=null where id=? and status in ('FAILED','RETRY')
            """,id);
        if(changed!=1)throw new IllegalArgumentException("실패 또는 재시도 상태의 메일만 다시 처리할 수 있습니다.");
        return Map.of("message","메일을 발송 대기열에 다시 등록했습니다.");
    }
    @GetMapping("/integrations/sheets/outbox")
    public List<Map<String,Object>> sheetOutbox(@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset){
        return jdbc.queryForList("""
            select id,event_type,reference_type,reference_id,status,attempts,next_attempt_at,last_error,created_at,sent_at
            from sheet_outbox order by id desc limit ? offset ?
            """,pageSize(limit),pageOffset(offset));
    }
    @PostMapping("/integrations/sheets/outbox/{id}/retry")
    public Map<String,String> retrySheet(@PathVariable long id){
        int changed=jdbc.update("""
            update sheet_outbox set status='PENDING',attempts=0,next_attempt_at=current_timestamp,last_error=null,
            claimed_at=null,claimed_by=null where id=? and status in ('FAILED','RETRY')
            """,id);
        if(changed!=1)throw new IllegalArgumentException("실패 또는 재시도 상태의 Sheets 작업만 다시 처리할 수 있습니다.");
        return Map.of("message","Google Sheets 작업을 대기열에 다시 등록했습니다.");
    }
    public record TestEmailRequest(String recipient){}
    @PostMapping("/email/test")
    public Map<String,String> testEmail(@RequestBody TestEmailRequest request){
        String recipient=request.recipient()==null?"":request.recipient().trim();
        if(!recipient.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new IllegalArgumentException("올바른 수신 이메일 주소를 입력해 주세요.");
        outbox.enqueue(null,recipient,"[(주)금성이엔씨] SMTP 테스트 메일",
            "SMTP 설정 테스트 메일입니다.\n\n이 메일이 도착했다면 외부 메일 발송 설정이 정상입니다.");
        return Map.of("message","테스트 메일을 발송 대기열에 등록했습니다.");
    }
    private int count(String sql){return Objects.requireNonNull(jdbc.queryForObject(sql,Integer.class));}
    private int pageSize(int limit){return Math.max(1,Math.min(limit,200));}
    private int pageOffset(int offset){return Math.max(0,Math.min(offset,100000));}
    private List<Map<String,Object>> pagedRows(String table,String order,int limit,int offset){
        if(!Set.of("projects","contracts","deliveries","tax_invoices","notices").contains(table))
            throw new IllegalArgumentException("올바르지 않은 업무 유형입니다.");
        return jdbc.queryForList("select * from "+table+" order by "+order+" limit ? offset ?",pageSize(limit),pageOffset(offset));
    }
    private Map<String,Object> requireCustomer(String email){
        if(email==null||email.isBlank())throw new IllegalArgumentException("가입된 고객 이메일을 입력해 주세요.");
        List<Map<String,Object>> found=jdbc.queryForList("select id,email from app_users where role='CUSTOMER' and lower(email)=lower(?)",email);
        if(found.isEmpty())
            throw new IllegalArgumentException("가입된 고객 이메일을 입력해 주세요.");
        return found.getFirst();
    }
    private void requireOwnedReference(String table,Long id,long customerId){
        if(id==null)return;
        String ownerColumn;
        if("quote_requests".equals(table))ownerColumn="owner_user_id";
        else if(Set.of("projects","contracts").contains(table))ownerColumn="customer_user_id";
        else throw new IllegalArgumentException("올바르지 않은 관계 유형입니다.");
        Integer count=jdbc.queryForObject("select count(*) from "+table+" where id=? and "+ownerColumn+"=?",Integer.class,id,customerId);
        if(count==null||count!=1)throw new IllegalArgumentException("선택한 견적·프로젝트·계약이 해당 고객의 데이터가 아닙니다.");
    }
    private void notifyBusiness(String table,long id,String subject,String state){
        if(!Set.of("projects","contracts","deliveries","tax_invoices").contains(table))throw new IllegalArgumentException("올바르지 않은 업무 유형입니다.");
        Map<String,Object> item=jdbc.queryForMap("select customer_email from "+table+" where id=?",id);
        outbox.enqueue(null,(String)item.get("customer_email"),"[(주)금성이엔씨] "+subject,
            subject+"가 "+Objects.requireNonNullElse(state,"-")+"(으)로 변경되었습니다. 고객 포털에서 확인해 주세요.");
    }
    private void requireText(String value,int max,String label){if(value==null||value.isBlank()||value.trim().length()>max)throw new IllegalArgumentException(label+" 값을 확인해 주세요.");}
    private void requireOptionalText(String value,int max,String label){if(value!=null&&value.trim().length()>max)throw new IllegalArgumentException(label+" 값은 "+max+"자 이하여야 합니다.");}
    private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
    private void requireStatus(String value,Set<String> allowed){
        if(value==null||!allowed.contains(value))throw new IllegalArgumentException("올바르지 않은 상태입니다.");
    }
    private ResponseEntity<Resource> resource(String key,String name,String type) throws IOException{
        ObjectStorage.StoredObject stored=fileStorage.load(key);
        String encoded=URLEncoder.encode(name,StandardCharsets.UTF_8).replace("+","%20");
        MediaType mediaType;
        try{mediaType=MediaType.parseMediaType(type);}catch(InvalidMediaTypeException e){mediaType=MediaType.APPLICATION_OCTET_STREAM;}
        return ResponseEntity.ok().contentType(mediaType).contentLength(stored.contentLength())
            .cacheControl(CacheControl.noStore())
            .header("Pragma","no-cache")
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+encoded)
            .body(stored.resource());
    }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String,String>> notFound(Exception e){return ResponseEntity.status(404).body(Map.of("message",e.getMessage()));}
    @ExceptionHandler({IllegalArgumentException.class,IOException.class})
    ResponseEntity<Map<String,String>> bad(Exception e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
}
