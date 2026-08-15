package kr.co.kumsungenc.platform.quote;

import jakarta.persistence.*;

@Entity
@Table(name = "quote_attachments")
public class QuoteAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_request_id")
    private QuoteRequest quoteRequest;
    @Column(nullable = false, length = 255)
    private String originalName;
    @Column(nullable = false, length = 255)
    private String storedName;
    @Column(nullable = false, length = 120)
    private String contentType;
    @Column(nullable = false)
    private long fileSize;

    protected QuoteAttachment() {}
    public QuoteAttachment(String originalName, String storedName, String contentType, long fileSize) {
        this.originalName = originalName; this.storedName = storedName;
        this.contentType = contentType; this.fileSize = fileSize;
    }
    void setQuoteRequest(QuoteRequest v) { quoteRequest = v; }
    public Long getId() { return id; }
    public String getOriginalName() { return originalName; }
    public String getStoredName() { return storedName; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
}
