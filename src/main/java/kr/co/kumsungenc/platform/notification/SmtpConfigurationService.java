package kr.co.kumsungenc.platform.notification;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SmtpConfigurationService {
    private final String provider;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;
    private final String quoteRecipient;
    private final String supportRecipient;
    private final String supportEmail;
    private final boolean auth;
    private final boolean startTls;
    private final boolean ssl;

    public SmtpConfigurationService(
            @Value("${app.mail-provider:AUTO}") String provider,
            @Value("${spring.mail.host:localhost}") String host,
            @Value("${spring.mail.port:1025}") int port,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${app.mail-from:no-reply@localhost}") String from,
            @Value("${app.quote-recipient:}") String quoteRecipient,
            @Value("${app.support-recipient:}") String supportRecipient,
            @Value("${app.support-email:}") String supportEmail,
            @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean auth,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean startTls,
            @Value("${spring.mail.properties.mail.smtp.ssl.enable:false}") boolean ssl) {
        this.provider = provider;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.from = from;
        this.quoteRecipient = quoteRecipient;
        this.supportRecipient = supportRecipient;
        this.supportEmail = supportEmail;
        this.auth = auth;
        this.startTls = startTls;
        this.ssl = ssl;
    }

    public Map<String, Object> sanitizedView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("provider", resolvedProvider());
        view.put("host", host);
        view.put("port", port);
        view.put("username", maskEmail(username));
        view.put("from", from);
        view.put("quoteRecipient", quoteRecipient);
        view.put("supportRecipient", supportRecipient);
        view.put("supportEmail", supportEmail);
        view.put("auth", auth);
        view.put("startTls", startTls);
        view.put("ssl", ssl);
        view.put("passwordConfigured", StringUtils.hasText(password));
        view.put("configured", StringUtils.hasText(host)
                && StringUtils.hasText(from)
                && (!auth || (StringUtils.hasText(username) && StringUtils.hasText(password))));
        return view;
    }

    private String resolvedProvider() {
        if (StringUtils.hasText(provider) && !"AUTO".equalsIgnoreCase(provider)) {
            return provider.toUpperCase(Locale.ROOT);
        }
        if ("mailpit".equalsIgnoreCase(host) || ("localhost".equalsIgnoreCase(host) && port == 1025)) {
            return "MAILPIT";
        }
        if ("smtp.gmail.com".equalsIgnoreCase(host)) {
            return "GMAIL";
        }
        if ("smtp.daum.net".equalsIgnoreCase(host) || "smtp.hanmail.net".equalsIgnoreCase(host)) {
            return "DAUM";
        }
        return "CUSTOM";
    }

    private String maskEmail(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        int at = value.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? value.substring(at) : "");
        }
        return value.substring(0, 2) + "***" + value.substring(at);
    }
}
