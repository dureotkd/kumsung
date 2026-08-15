package kr.co.kumsungenc.platform.quote;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record QuoteForm(
    @NotBlank @Size(max=150) String companyName,
    @Size(max=30) String businessNumber,
    @NotBlank @Size(max=60) String contactName,
    @NotBlank @Email @Size(max=120) String email,
    @NotBlank @Size(max=30) String phone,
    @Size(max=200) String siteName,
    @Size(max=300) String siteAddress,
    @NotBlank @Size(max=80) String productType,
    @NotBlank @Size(max=200) String subject,
    @NotBlank @Size(max=5000) String details,
    @Pattern(regexp="^$|https?://.+", message="웹하드 주소는 http 또는 https 주소여야 합니다.")
    @Size(max=500) String webhardUrl,
    LocalDateTime desiredDate,
    @AssertTrue(message="개인정보 수집 및 이용에 동의해 주세요.") boolean privacyAgreed
) {}
