package kr.co.kumsungenc.platform.shop;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class TossPaymentsSettingsService {
    private final JdbcTemplate jdbc;
    private final String configuredClientKey;
    private final String secretKey;

    public TossPaymentsSettingsService(JdbcTemplate jdbc,
            @Value("${app.toss-payments.client-key:}") String configuredClientKey,
            @Value("${app.toss-payments.secret-key:}") String secretKey) {
        this.jdbc=jdbc;
        this.configuredClientKey=configuredClientKey==null?"":configuredClientKey.trim();
        this.secretKey=secretKey==null?"":secretKey.trim();
    }

    public Map<String,Object> settings(){
        Map<String,Object> row=new LinkedHashMap<>(jdbc.queryForMap("""
            select enabled,mode,client_key,updated_by,created_at,updated_at
            from shop_payment_settings where id=1
            """));
        String mode=(String)row.get("mode");
        String storedClientKey=(String)row.get("client_key");
        String clientKey=configuredClientKey.isBlank()?storedClientKey:configuredClientKey;
        String clientMode=keyMode(clientKey,"ck");
        String secretMode=keyMode(secretKey,"sk");
        String clientFamily=keyFamily(clientKey,"ck");
        String secretFamily=keyFamily(secretKey,"sk");
        boolean clientKeyConfigured=clientMode!=null;
        boolean secretKeyConfigured=secretMode!=null;
        boolean keyPairMatches=clientKeyConfigured&&secretKeyConfigured
            &&mode.equals(clientMode)&&mode.equals(secretMode)&&clientFamily.equals(secretFamily);
        row.put("clientKey",clientKey==null?"":clientKey);
        row.put("clientKeyManagedByEnvironment",!configuredClientKey.isBlank());
        row.put("clientKeyConfigured",clientKeyConfigured);
        row.put("secretKeyConfigured",secretKeyConfigured);
        row.put("secretKeyMode",secretMode==null?"":secretMode);
        row.put("keyFamily",keyPairMatches?clientFamily:"");
        row.put("keyPairMatches",keyPairMatches);
        row.put("ready",Boolean.TRUE.equals(row.get("enabled"))&&keyPairMatches);
        row.remove("client_key");
        return row;
    }

    @Transactional
    public Map<String,Object> update(boolean enabled,String mode,String clientKey,String actor,String ip){
        String cleanMode=mode==null?"":mode.trim().toUpperCase(Locale.ROOT);
        if(!cleanMode.equals("TEST")&&!cleanMode.equals("LIVE"))
            throw new IllegalArgumentException("결제 모드는 테스트 또는 라이브만 선택할 수 있습니다.");
        String cleanClient=clientKey==null?"":clientKey.trim();
        if(!configuredClientKey.isBlank()&&!configuredClientKey.equals(cleanClient))
            throw new IllegalArgumentException("클라이언트 키는 서버 환경변수에서 관리되고 있습니다.");
        if(cleanClient.length()>200)throw new IllegalArgumentException("클라이언트 키가 너무 깁니다.");
        String clientMode=keyMode(cleanClient,"ck");
        if(!cleanClient.isEmpty()&&clientMode==null)
            throw new IllegalArgumentException("올바른 토스페이먼츠 클라이언트 키를 입력해 주세요.");
        if(clientMode!=null&&!cleanMode.equals(clientMode))
            throw new IllegalArgumentException("선택한 모드와 클라이언트 키의 테스트·라이브 구분이 다릅니다.");
        String secretMode=keyMode(secretKey,"sk");
        String clientFamily=keyFamily(cleanClient,"ck"),secretFamily=keyFamily(secretKey,"sk");
        if(enabled&&(clientMode==null||secretMode==null||!cleanMode.equals(secretMode)
                ||clientFamily==null||!clientFamily.equals(secretFamily)))
            throw new IllegalArgumentException("같은 모드·키 종류의 클라이언트 키와 서버 시크릿 키를 먼저 설정해 주세요.");
        jdbc.update("""
            update shop_payment_settings
            set enabled=?,mode=?,client_key=?,updated_by=?,updated_at=current_timestamp
            where id=1
            """,enabled,cleanMode,cleanClient.isEmpty()?null:cleanClient,actor);
        jdbc.update("""
            insert into audit_logs(actor_email,action,target_type,target_id,details,ip_address)
            values (?,?,?,?,?,?)
            """,actor,"SHOP_PAYMENT_SETTINGS_UPDATE","SHOP_PAYMENT_SETTINGS","1",
            "mode="+cleanMode+", enabled="+enabled,ip);
        return settings();
    }

    public Map<String,Object> summary(){
        Map<String,Object> result=new LinkedHashMap<>(jdbc.queryForMap("""
            select count(*) as products,
                   count(*) filter(where active=true) as active_products,
                   count(*) filter(where price is not null) as priced_products
            from shop_products
            """));
        Map<String,Object> payment=settings();
        result.put("paymentEnabled",payment.get("enabled"));
        result.put("paymentReady",payment.get("ready"));
        result.put("paymentMode",payment.get("mode"));
        return result;
    }

    private String keyMode(String value,String type){
        if(value==null)return null;
        String key=value.trim().toLowerCase(Locale.ROOT);
        if(key.matches("test_g?"+type+"_.+"))return "TEST";
        if(key.matches("live_g?"+type+"_.+"))return "LIVE";
        return null;
    }
    private String keyFamily(String value,String type){
        String mode=keyMode(value,type);if(mode==null)return null;
        return value.toLowerCase(Locale.ROOT).startsWith(mode.toLowerCase(Locale.ROOT)+"_g")?"WIDGET":"API";
    }
}
