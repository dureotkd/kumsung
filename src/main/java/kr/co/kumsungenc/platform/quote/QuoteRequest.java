package kr.co.kumsungenc.platform.quote;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quote_requests")
public class QuoteRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    private String receiptNumber;
    @Column(nullable = false, length = 150)
    private String companyName;
    @Column(length = 30)
    private String businessNumber;
    @Column(nullable = false, length = 60)
    private String contactName;
    @Column(nullable = false, length = 120)
    private String email;
    @Column(nullable = false, length = 30)
    private String phone;
    @Column(length = 200)
    private String siteName;
    @Column(length = 300)
    private String siteAddress;
    @Column(nullable = false, length = 80)
    private String productType;
    @Column(nullable = false, length = 200)
    private String subject;
    @Column(nullable = false, columnDefinition = "text")
    private String details;
    @Column(name="customer_webhard_url", length=500)
    private String customerWebhardUrl;
    private LocalDateTime desiredDate;
    @Column(nullable = false, length = 20)
    private String status = "RECEIVED";
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(name="owner_user_id")
    private Long ownerUserId;
    @OneToMany(mappedBy = "quoteRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuoteAttachment> attachments = new ArrayList<>();

    @PrePersist void prePersist() { createdAt = LocalDateTime.now(); }
    public void addAttachment(QuoteAttachment attachment) {
        attachments.add(attachment);
        attachment.setQuoteRequest(this);
    }
    public Long getId() { return id; }
    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String v) { receiptNumber = v; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String v) { companyName = v; }
    public String getBusinessNumber() { return businessNumber; }
    public void setBusinessNumber(String v) { businessNumber = v; }
    public String getContactName() { return contactName; }
    public void setContactName(String v) { contactName = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { phone = v; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String v) { siteName = v; }
    public String getSiteAddress() { return siteAddress; }
    public void setSiteAddress(String v) { siteAddress = v; }
    public String getProductType() { return productType; }
    public void setProductType(String v) { productType = v; }
    public String getSubject() { return subject; }
    public void setSubject(String v) { subject = v; }
    public String getDetails() { return details; }
    public void setDetails(String v) { details = v; }
    public String getCustomerWebhardUrl() { return customerWebhardUrl; }
    public void setCustomerWebhardUrl(String v) { customerWebhardUrl = v; }
    public LocalDateTime getDesiredDate() { return desiredDate; }
    public void setDesiredDate(LocalDateTime v) { desiredDate = v; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<QuoteAttachment> getAttachments() { return attachments; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long v) { ownerUserId = v; }
}
