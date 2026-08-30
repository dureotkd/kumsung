package kr.co.kumsungenc.platform.portal;

import kr.co.kumsungenc.platform.file.FileValidationService;
import kr.co.kumsungenc.platform.file.FileStorageService;
import kr.co.kumsungenc.platform.file.ObjectStorage;
import kr.co.kumsungenc.platform.file.StorageKeys;
import kr.co.kumsungenc.platform.security.*;
import kr.co.kumsungenc.platform.notification.EmailOutboxService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/portal")
public class PortalController {
    private final JdbcTemplate jdbc; private final FileValidationService fileValidation;
    private final FileStorageService fileStorage;
    private final AppUserRepository users;private final PasswordEncoder passwordEncoder;
    private final EmailOutboxService outbox; private final String quoteRecipient; private final String supportRecipient;
    private final TransactionTemplate transactions;
    private final ClientIpResolver clientIpResolver;
    public PortalController(JdbcTemplate jdbc,FileValidationService fileValidation,
        FileStorageService fileStorage,AppUserRepository users,PasswordEncoder passwordEncoder,
        EmailOutboxService outbox,@Value("${app.quote-recipient}") String quoteRecipient,
        @Value("${app.support-recipient}") String supportRecipient,TransactionTemplate transactions,
        ClientIpResolver clientIpResolver){
        this.jdbc=jdbc;this.fileValidation=fileValidation;
        this.fileStorage=fileStorage;this.users=users;this.passwordEncoder=passwordEncoder;
        this.outbox=outbox;this.quoteRecipient=quoteRecipient;this.supportRecipient=supportRecipient;
        this.transactions=transactions;
        this.clientIpResolver=clientIpResolver;
    }
    private String email(Principal p){return p.getName().toLowerCase();}
    private long userId(Principal p){
        AppUser user=users.findByEmailIgnoreCase(p.getName()).orElseThrow();
        if(!user.isEmailVerified())throw new IllegalStateException("이메일 인증이 필요합니다.");
        return user.getId();
    }
    private long quoteId(String receipt,Principal p){
        List<Long> ids=jdbc.query("select id from quote_requests where receipt_number=? and owner_user_id=?",
            (rs,n)->rs.getLong(1),receipt,userId(p));
        if(ids.isEmpty())throw new NoSuchElementException("견적을 찾을 수 없습니다.");
        return ids.getFirst();
    }
    @GetMapping("/summary") public Map<String,Object> summary(Principal p){
        long uid=userId(p);
        Integer quotes=jdbc.queryForObject("select count(*) from quote_requests where owner_user_id=?",Integer.class,uid);
        Integer active=jdbc.queryForObject("select count(*) from quote_requests where owner_user_id=? and status not in ('COMPLETED','CANCELLED')",Integer.class,uid);
        Integer projects=jdbc.queryForObject("select count(*) from projects where customer_user_id=?",Integer.class,uid);
        Integer messages=jdbc.queryForObject("select count(*) from quote_messages m join quote_requests q on q.id=m.quote_request_id where q.owner_user_id=? and m.sender_role='ADMIN'",Integer.class,uid);
        return Map.of("quotes",quotes,"active",active,"projects",projects,"messages",messages);
    }
    @GetMapping("/quotes") public List<Map<String,Object>> quotes(Principal p,@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){
        return jdbc.queryForList("""
          select q.receipt_number,q.company_name,q.site_name,q.product_type,q.subject,q.status,
          q.assigned_to,q.created_at,q.updated_at,
          (select d.id from quote_documents d
             where d.quote_request_id=q.id and d.document_type='ESTIMATE'
             order by d.created_at desc,d.id desc limit 1) as latest_estimate_document_id
          from quote_requests q where q.owner_user_id=? order by q.created_at desc,q.id desc limit ? offset ?
          """,userId(p),pageSize(limit),pageOffset(offset));
    }
    @GetMapping("/quotes/{receipt}") public Map<String,Object> quote(@PathVariable String receipt,Principal p){
        long id=quoteId(receipt,p);
        Map<String,Object> result=new LinkedHashMap<>(jdbc.queryForMap("""
          select receipt_number,company_name,business_number,contact_name,email,phone,site_name,site_address,
          product_type,subject,details,desired_date,status,assigned_to,webhard_url,customer_webhard_url,
          estimate_amount,estimate_notes,created_at,updated_at
          from quote_requests where id=?
          """,id));
        result.put("history",jdbc.queryForList("select status,note,changed_by,created_at from quote_status_history where quote_request_id=? order by created_at",id));
        result.put("supplementalRequests",jdbc.queryForList("select id,request_text,status,requested_at,completed_at from supplemental_requests where quote_request_id=? order by requested_at desc",id));
        result.put("attachments",jdbc.queryForList("select id,original_name,content_type,file_size from quote_attachments where quote_request_id=? order by id",id));
        result.put("documents",jdbc.queryForList("""
            select id,document_type,title,original_name,file_size,approval_status,approved_at,
            contract_decision,contract_decision_note,contract_decided_at,created_at
            from quote_documents where quote_request_id=? order by created_at desc
            """,id));
        return result;
    }
    public record WebhardRequest(String url){}
    @PutMapping("/quotes/{receipt}/webhard")
    public Map<String,String> webhard(@PathVariable String receipt,@RequestBody WebhardRequest request,Principal p){
        if(request.url()!=null&&!request.url().isBlank()&&!request.url().matches("^https?://.+"))
            throw new IllegalArgumentException("http 또는 https 웹하드 주소를 입력해 주세요.");
        jdbc.update("update quote_requests set customer_webhard_url=?,updated_at=current_timestamp where id=?",
            blankToNull(request.url()),quoteId(receipt,p));
        return Map.of("message","웹하드 공유 주소가 저장되었습니다.");
    }
    @PostMapping(value="/quotes/{receipt}/files",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,String> addFiles(@PathVariable String receipt,@RequestPart("files") List<MultipartFile> files,Principal p) throws IOException{
        long id=quoteId(receipt,p);fileValidation.validateBatch(files);
        record Stored(String original,String stored,String contentType,long size,String key){}
        List<Stored> staged=new ArrayList<>();
        try{
            for(MultipartFile file:files){
                if(file.isEmpty())continue;
                String original=Objects.requireNonNullElse(file.getOriginalFilename(),"file");
                String ext=fileValidation.validateQuoteFile(file);
                String stored=UUID.randomUUID()+(ext.isBlank()?"":"."+ext);String key=StorageKeys.quoteAttachment(receipt,stored);
                fileStorage.store(file,key);
                staged.add(new Stored(original,stored,Objects.requireNonNullElse(file.getContentType(),"application/octet-stream"),file.getSize(),key));
            }
            if(staged.isEmpty())throw new IllegalArgumentException("유효한 파일을 선택해 주세요.");
            transactions.executeWithoutResult(status -> {
                for(Stored item:staged)jdbc.update("insert into quote_attachments(quote_request_id,original_name,stored_name,content_type,file_size) values (?,?,?,?,?)",
                    id,item.original(),item.stored(),item.contentType(),item.size());
                Integer pending=jdbc.queryForObject("select count(*) from supplemental_requests where quote_request_id=? and status='REQUESTED'",Integer.class,id);
                if(pending!=null&&pending>0){
                    jdbc.update("update supplemental_requests set status='COMPLETED',completed_at=current_timestamp where quote_request_id=? and status='REQUESTED'",id);
                    jdbc.update("insert into quote_status_history(quote_request_id,status,note,changed_by) values (?,'SUPPLEMENTED','고객 보완자료 업로드',?)",id,email(p));
                    jdbc.update("update quote_requests set status='SUPPLEMENTED',updated_at=current_timestamp where id=?",id);
                }else{
                    String current=jdbc.queryForObject("select status from quote_requests where id=?",String.class,id);
                    jdbc.update("insert into quote_status_history(quote_request_id,status,note,changed_by) values (?,?,?,?)",id,current,"고객 추가자료 업로드",email(p));
                    jdbc.update("update quote_requests set updated_at=current_timestamp where id=?",id);
                }
                outbox.enqueue(id,quoteRecipient,"[고객 추가자료] "+receipt,
                    email(p)+" 고객이 견적 "+receipt+"에 추가자료를 업로드했습니다.");
            });
            return Map.of("message","추가자료가 업로드되었습니다.");
        }catch(IOException|RuntimeException e){
            for(Stored item:staged)try{fileStorage.delete(item.key());}catch(IOException ignored){}
            throw e;
        }
    }
    @GetMapping("/quotes/{receipt}/files/{fileId}")
    public ResponseEntity<Resource> file(@PathVariable String receipt,@PathVariable long fileId,Principal p) throws IOException{
        long id=quoteId(receipt,p);
        Map<String,Object> row=jdbc.queryForMap("select original_name,stored_name,content_type from quote_attachments where id=? and quote_request_id=?",fileId,id);
        return resource(StorageKeys.quoteAttachment(receipt,(String)row.get("stored_name")),(String)row.get("original_name"),(String)row.get("content_type"));
    }
    @GetMapping("/quotes/{receipt}/documents/{documentId}")
    public ResponseEntity<Resource> document(@PathVariable String receipt,@PathVariable long documentId,Principal p) throws IOException{
        long id=quoteId(receipt,p);
        Map<String,Object> row=jdbc.queryForMap("select original_name,stored_name,content_type from quote_documents where id=? and quote_request_id=?",documentId,id);
        return resource(StorageKeys.quoteDocument(receipt,(String)row.get("stored_name")),(String)row.get("original_name"),(String)row.get("content_type"));
    }
    @PostMapping("/quotes/{receipt}/documents/{documentId}/approve")
    @Transactional
    public Map<String,String> approve(@PathVariable String receipt,@PathVariable long documentId,Principal p,HttpServletRequest request){
        long id=quoteId(receipt,p);
        String ip=clientIpResolver.resolve(request);
        int changed=jdbc.update("""
            update quote_documents set approval_status='APPROVED',approved_at=current_timestamp,
            approved_by_email=?,approved_ip=?,approval_user_agent=?
            where id=? and quote_request_id=? and document_type<>'CONTRACT' and approval_status='PENDING'
            """,email(p),ip,request.getHeader("User-Agent"),documentId,id);
        if(changed==0)throw new IllegalArgumentException("이미 승인했거나 전자승인 대상이 아닌 문서입니다.");
        jdbc.update("insert into quote_status_history(quote_request_id,status,note,changed_by) values (?,'APPROVED','고객 전자승인',?)",id,email(p));
        jdbc.update("insert into audit_logs(actor_email,action,target_type,target_id,details,ip_address) values (?,'DOCUMENT_APPROVE','QUOTE_DOCUMENT',?,'전자승인 및 문서 해시 보존',?)",
            email(p),String.valueOf(documentId),ip);
        outbox.enqueue(id,quoteRecipient,"[전자승인 완료] "+receipt,
            email(p)+" 고객이 견적 문서를 전자승인했습니다.\n문서 ID: "+documentId);
        return Map.of("message","전자승인이 완료되었습니다.");
    }
    public record ContractDecision(String decision,String note){}
    @PostMapping("/quotes/{receipt}/documents/{documentId}/contract-decision")
    @Transactional
    public Map<String,String> contractDecision(@PathVariable String receipt,@PathVariable long documentId,
        @RequestBody ContractDecision body,Principal p,HttpServletRequest request){
        if(!Set.of("ACCEPTED","REJECTED").contains(body.decision()))
            throw new IllegalArgumentException("계약 수락 또는 거절을 선택해 주세요.");
        if("REJECTED".equals(body.decision())&&(body.note()==null||body.note().isBlank()))
            throw new IllegalArgumentException("계약 거절 사유를 입력해 주세요.");
        long quoteId=quoteId(receipt,p);long uid=userId(p);String ip=clientIpResolver.resolve(request);
        int changed=jdbc.update("""
            update quote_documents set contract_decision=?,contract_decision_note=?,contract_decided_at=current_timestamp,
              contract_decided_by=?,contract_decision_ip=?
            where id=? and quote_request_id=? and document_type='CONTRACT' and contract_decision='PENDING'
            """,body.decision(),blankToNull(body.note()),uid,ip,documentId,quoteId);
        if(changed==0)throw new IllegalArgumentException("이미 처리되었거나 계약서가 아닌 문서입니다.");
        jdbc.update("insert into quote_status_history(quote_request_id,status,note,changed_by) values (?,?,?,?)",
            quoteId,"ACCEPTED".equals(body.decision())?"APPROVED":"REVIEWING",
            "계약서 "+("ACCEPTED".equals(body.decision())?"수락":"거절")+": "+Objects.requireNonNullElse(body.note(),""),email(p));
        jdbc.update("insert into audit_logs(actor_email,action,target_type,target_id,details,ip_address) values (?,?,?,?,?,?)",
            email(p),"CONTRACT_"+body.decision(),"QUOTE_DOCUMENT",String.valueOf(documentId),Objects.requireNonNullElse(body.note(),""),ip);
        outbox.enqueue(quoteId,quoteRecipient,"[계약서 "+("ACCEPTED".equals(body.decision())?"수락":"거절")+"] "+receipt,
            email(p)+" 고객이 계약서를 "+("ACCEPTED".equals(body.decision())?"수락":"거절")+"했습니다.\n"+Objects.requireNonNullElse(body.note(),""));
        return Map.of("message","계약서 응답이 기록되었습니다.");
    }
    @GetMapping("/quotes/{receipt}/messages") public List<Map<String,Object>> messages(@PathVariable String receipt,Principal p){
        return jdbc.queryForList("select id,sender_email,sender_role,message,created_at from quote_messages where quote_request_id=? order by created_at",quoteId(receipt,p));
    }
    public record MessageRequest(String message){}
    @PostMapping("/quotes/{receipt}/messages") @Transactional
    public Map<String,String> message(@PathVariable String receipt,@RequestBody MessageRequest body,Principal p){
        if(body.message()==null||body.message().isBlank()||body.message().length()>5000)throw new IllegalArgumentException("메시지 내용을 확인해 주세요.");
        long id=quoteId(receipt,p);
        jdbc.update("insert into quote_messages(quote_request_id,sender_email,sender_role,message) values (?,?,?,?)",id,email(p),"CUSTOMER",body.message());
        outbox.enqueue(id,quoteRecipient,"[고객 메시지] "+receipt,body.message()+"\n\n보낸 고객: "+email(p));
        return Map.of("message","메시지가 전송되었습니다.");
    }
    @GetMapping("/projects") public List<Map<String,Object>> projects(Principal p,@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){return ownedRows("projects",p,limit,offset);}
    @GetMapping("/contracts") public List<Map<String,Object>> contracts(Principal p,@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){return ownedRows("contracts",p,limit,offset);}
    @GetMapping("/deliveries") public List<Map<String,Object>> deliveries(Principal p,@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){return ownedRows("deliveries",p,limit,offset);}
    @GetMapping("/tax-invoices") public List<Map<String,Object>> invoices(Principal p,@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){return ownedRows("tax_invoices",p,limit,offset);}
    @GetMapping("/service-requests") public List<Map<String,Object>> services(Principal p,@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){return ownedRows("service_requests",p,limit,offset);}
    public record ServiceRequest(Long projectId,String title,String details){}
    @PostMapping("/service-requests") @Transactional public Map<String,String> service(@RequestBody ServiceRequest r,Principal p){
        requireText(r.title(),200,"제목");requireText(r.details(),10000,"내용");
        long uid=userId(p);
        if(r.projectId()!=null){
            Integer owned=jdbc.queryForObject("select count(*) from projects where id=? and customer_user_id=?",Integer.class,r.projectId(),uid);
            if(owned==null||owned!=1)throw new IllegalArgumentException("본인 소유의 프로젝트만 선택할 수 있습니다.");
        }
        jdbc.update("insert into service_requests(customer_email,customer_user_id,project_id,title,details) values (?,?,?,?,?)",email(p),uid,r.projectId(),r.title(),r.details());
        outbox.enqueue(null,supportRecipient,"[A/S 접수] "+r.title(),r.details()+"\n\n고객: "+email(p));
        return Map.of("message","A/S 요청이 접수되었습니다.");
    }
    @GetMapping("/support") public List<Map<String,Object>> support(Principal p,@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){return ownedRows("support_inquiries",p,limit,offset);}
    public record SupportRequest(String subject,String message){}
    @PostMapping("/support") @Transactional public Map<String,String> support(@RequestBody SupportRequest r,Principal p){
        requireText(r.subject(),200,"제목");requireText(r.message(),10000,"내용");
        String receipt="QNA-"+java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
        Long id=jdbc.queryForObject("insert into support_inquiries(customer_email,customer_user_id,receipt_number,source,subject,message) values (?,?,?,'PORTAL',?,?) returning id",
            Long.class,email(p),userId(p),receipt,r.subject(),r.message());
        if(id==null)throw new IllegalStateException("고객센터 문의 저장에 실패했습니다.");
        String body="고객센터 문의가 접수되었습니다.\n접수번호: "+receipt+"\n제목: "+r.subject()+"\n\n"+r.message();
        outbox.enqueue("SUPPORT_INQUIRY",id,supportRecipient,"[고객센터 문의] "+receipt+" "+r.subject(),body+"\n\n고객: "+email(p));
        outbox.enqueue("SUPPORT_INQUIRY",id,email(p),"[(주)금성이엔씨] 고객문의 접수 - "+receipt,body+"\n\n담당자 확인 후 연락드리겠습니다.");
        return Map.of("message","고객센터 문의가 접수되었습니다.","receiptNumber",receipt);
    }
    @GetMapping("/shop-inquiries") public List<Map<String,Object>> shopInquiries(Principal p,@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset){
        return jdbc.queryForList("""
            select s.receipt_number,s.company_name,s.contact_name,s.email,s.phone,s.message,s.status,s.admin_note,s.created_at,s.updated_at,
              coalesce(string_agg(i.product_name||' × '||i.quantity,', ' order by i.id),'') as items
            from shop_inquiries s left join shop_inquiry_items i on i.shop_inquiry_id=s.id
            where s.customer_user_id=? group by s.id order by s.created_at desc,s.id desc limit ? offset ?
            """,userId(p),pageSize(limit),pageOffset(offset));
    }
    @GetMapping("/privacy/export") public Map<String,Object> exportData(Principal p){
        long uid=userId(p);String e=email(p);Map<String,Object> result=new LinkedHashMap<>();
        result.put("profile",jdbc.queryForMap("select email,name,company_name,phone,created_at,verified_at from app_users where id=?",uid));
        result.put("quotes",jdbc.queryForList("select * from quote_requests where owner_user_id=? order by created_at",uid));
        result.put("projects",exportOwnedRows("projects",uid));
        result.put("contracts",exportOwnedRows("contracts",uid));
        result.put("deliveries",exportOwnedRows("deliveries",uid));
        result.put("taxInvoices",exportOwnedRows("tax_invoices",uid));
        result.put("serviceRequests",exportOwnedRows("service_requests",uid));
        result.put("support",exportOwnedRows("support_inquiries",uid));
        result.put("shopInquiries",jdbc.queryForList("select * from shop_inquiries where customer_user_id=? order by created_at,id",uid));
        result.put("shopOrders",jdbc.queryForList("select * from shop_orders where customer_user_id=? order by created_at,id",uid));
        result.put("shopInquiryItems",jdbc.queryForList("""
            select i.* from shop_inquiry_items i join shop_inquiries s on s.id=i.shop_inquiry_id
            where s.customer_user_id=? order by i.created_at,i.id
            """,uid));
        result.put("shopInquiryAttachments",jdbc.queryForList("""
            select a.id,a.shop_inquiry_id,a.shop_inquiry_item_id,a.original_name,a.content_type,a.file_size,a.sha256,a.created_at
            from shop_inquiry_attachments a join shop_inquiries s on s.id=a.shop_inquiry_id
            where s.customer_user_id=? order by a.created_at,a.id
            """,uid));
        result.put("quoteAttachments",jdbc.queryForList("""
            select a.id,a.quote_request_id,a.original_name,a.content_type,a.file_size
            from quote_attachments a join quote_requests q on q.id=a.quote_request_id
            where q.owner_user_id=? order by a.id
            """,uid));
        result.put("consents",jdbc.queryForList("select consent_version,purpose,agreed_at from privacy_consents where lower(email)=?",e));
        return result;
    }
    public record DeleteAccount(String password){}
    @DeleteMapping("/privacy/account") @Transactional
    public Map<String,String> deleteAccount(@RequestBody DeleteAccount r,Principal p,HttpServletRequest request){
        AppUser user=users.findByEmailIgnoreCase(p.getName()).orElseThrow();
        if(r.password()==null||!passwordEncoder.matches(r.password(),user.getPasswordHash()))
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        long uid=user.getId();String old=user.getEmail();String alias="deleted-"+uid+"@invalid.local";
        List<String> customerFiles=new ArrayList<>(jdbc.query("""
            select q.receipt_number,a.stored_name from quote_attachments a
            join quote_requests q on q.id=a.quote_request_id where q.owner_user_id=?
            """,(rs,n)->StorageKeys.quoteAttachment(rs.getString(1),rs.getString(2)),uid));
        customerFiles.addAll(jdbc.query("""
            select s.receipt_number,a.stored_name from shop_inquiry_attachments a
            join shop_inquiries s on s.id=a.shop_inquiry_id where s.customer_user_id=?
            """,(rs,n)->StorageKeys.shopAttachment(rs.getString(1),rs.getString(2)),uid));
        jdbc.update("delete from email_outbox where quote_request_id in (select id from quote_requests where owner_user_id=?)",uid);
        jdbc.update("""
            delete from email_outbox where
            (reference_type='SUPPORT_INQUIRY' and reference_id in (select id from support_inquiries where customer_user_id=?))
            or (reference_type='SHOP_INQUIRY' and reference_id in (select id from shop_inquiries where customer_user_id=?))
            """,uid,uid);
        jdbc.update("""
            delete from sheet_outbox where
            (reference_type='QUOTE' and reference_id in (select id from quote_requests where owner_user_id=?))
            or (reference_type='SHOP_INQUIRY' and reference_id in (select id from shop_inquiries where customer_user_id=?))
            """,uid,uid);
        jdbc.update("delete from quote_attachments where quote_request_id in (select id from quote_requests where owner_user_id=?)",uid);
        jdbc.update("delete from shop_inquiry_attachments where shop_inquiry_id in (select id from shop_inquiries where customer_user_id=?)",uid);
        jdbc.update("""
            update quote_requests set owner_user_id=null,company_name='탈퇴 회원',business_number=null,
            contact_name='탈퇴회원',email=?,phone='-',site_name=null,site_address=null,
            details='계정 삭제 요청에 따라 개인정보가 제거되었습니다.',customer_webhard_url=null
            where owner_user_id=?
            """,alias,uid);
        jdbc.update("update quote_messages set sender_email=? where lower(sender_email)=lower(?)",alias,old);
        for(String table:List.of("projects","contracts","deliveries","tax_invoices","service_requests"))
            jdbc.update("update "+table+" set customer_email=?,customer_user_id=null where customer_user_id=? or lower(customer_email)=lower(?)",alias,uid,old);
        jdbc.update("""
            update support_inquiries set customer_email=?,customer_user_id=null,company_name='탈퇴 회원',
            contact_name='탈퇴 회원',phone='-',subject='삭제된 고객 문의',
            message='계정 삭제 요청에 따라 개인정보가 제거되었습니다.',answer=null,internal_note=null,
            assigned_to=null,answered_by=null,submission_key=null
            where customer_user_id=? or lower(customer_email)=lower(?)
            """,alias,uid,old);
        jdbc.update("""
            update shop_inquiries set customer_user_id=null,company_name='탈퇴 회원',contact_name='탈퇴 회원',
            phone='-',email=?,message='계정 삭제 요청에 따라 개인정보가 제거되었습니다.',
            admin_note=null,submission_key=null where customer_user_id=? or lower(email)=lower(?)
            """,alias,uid,old);
        jdbc.update("update shop_inquiry_items set specifications=null where shop_inquiry_id in (select id from shop_inquiries where email=?)",alias);
        jdbc.update("""
            update shop_orders set customer_user_id=null,buyer_name='탈퇴 회원',buyer_email=?,buyer_phone='-',
            delivery_address='계정 삭제 요청에 따라 개인정보가 제거되었습니다.',updated_at=current_timestamp
            where customer_user_id=? or lower(buyer_email)=lower(?)
            """,alias,uid,old);
        jdbc.update("delete from email_outbox where lower(recipient)=lower(?) or position(lower(?) in lower(body))>0",old,old);
        jdbc.update("delete from email_logs where lower(recipient)=lower(?)",old);
        jdbc.update("update audit_logs set actor_email=? where lower(actor_email)=lower(?)",alias,old);
        jdbc.update("update privacy_consents set email=? where lower(email)=lower(?)",alias,old);
        jdbc.update("insert into audit_logs(actor_email,action,target_type,target_id,details,ip_address) values (?,'ACCOUNT_DELETE','USER',?,'고객 요청에 의한 계정 삭제',?)",
            alias,String.valueOf(uid),request.getRemoteAddr());
        if(TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){customerFiles.forEach(key->{try{fileStorage.delete(key);}catch(IOException ignored){}});}
            });
        users.delete(user);request.getSession().invalidate();
        return Map.of("message","계정과 개인정보가 삭제 또는 익명화되었습니다.");
    }
    private List<Map<String,Object>> ownedRows(String table,Principal p,int limit,int offset){
        if(!Set.of("projects","contracts","deliveries","tax_invoices","service_requests","support_inquiries").contains(table))
            throw new IllegalArgumentException("올바르지 않은 메뉴입니다.");
        return jdbc.queryForList("select * from "+table+" where customer_user_id=? order by created_at desc,id desc limit ? offset ?",
            userId(p),pageSize(limit),pageOffset(offset));
    }
    private List<Map<String,Object>> exportOwnedRows(String table,long userId){
        if(!Set.of("projects","contracts","deliveries","tax_invoices","service_requests","support_inquiries").contains(table))
            throw new IllegalArgumentException("올바르지 않은 내보내기 항목입니다.");
        return jdbc.queryForList("select * from "+table+" where customer_user_id=? order by created_at,id",userId);
    }
    private int pageSize(int limit){return Math.max(1,Math.min(limit,200));}
    private int pageOffset(int offset){return Math.max(0,Math.min(offset,100000));}
    private void requireText(String value,int max,String label){
        if(value==null||value.isBlank()||value.trim().length()>max)
            throw new IllegalArgumentException(label+" 값을 확인해 주세요.");
    }
    private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
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
    @ExceptionHandler(NoSuchElementException.class) ResponseEntity<Map<String,String>> notFound(Exception e){return ResponseEntity.status(404).body(Map.of("message",e.getMessage()));}
    @ExceptionHandler({IllegalArgumentException.class,IOException.class}) ResponseEntity<Map<String,String>> bad(Exception e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
}
