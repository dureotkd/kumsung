package kr.co.kumsungenc.platform.file;

public final class StorageKeys {
    private StorageKeys() {}

    public static String quoteAttachment(String receipt, String storedName) {
        return safe(receipt) + "/" + safe(storedName);
    }

    public static String quoteDocument(String receipt, String storedName) {
        return "documents/" + safe(receipt) + "/" + safe(storedName);
    }

    public static String shopAttachment(String receipt, String storedName) {
        return "shop/" + safe(receipt) + "/" + safe(storedName);
    }

    public static String shopProduct(String reference, String storedName) {
        return "content/shop/" + safe(reference) + "/" + safe(storedName);
    }

    public static String innovationImage(String reference, String storedName) {
        return "content/innovation/" + safe(reference) + "/image/" + safe(storedName);
    }

    public static String innovationFile(String reference, String storedName) {
        return "content/innovation/" + safe(reference) + "/file/" + safe(storedName);
    }

    public static String customerPostImage(String reference, String storedName) {
        return "content/customer/" + safe(reference) + "/" + safe(storedName);
    }

    public static String normalize(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.startsWith("\\") || key.contains("\\")) {
            throw new IllegalArgumentException("올바르지 않은 파일 저장 키입니다.");
        }
        String[] parts = key.split("/", -1);
        for (String part : parts) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)) {
                throw new IllegalArgumentException("올바르지 않은 파일 저장 키입니다.");
            }
        }
        return key;
    }

    private static String safe(String segment) {
        if (segment == null || segment.isBlank() || segment.contains("/") || segment.contains("\\") ||
            ".".equals(segment) || "..".equals(segment)) {
            throw new IllegalArgumentException("올바르지 않은 파일 저장 경로입니다.");
        }
        return segment;
    }
}
