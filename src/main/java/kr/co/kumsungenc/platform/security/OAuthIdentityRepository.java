package kr.co.kumsungenc.platform.security;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity,Long> {
    Optional<OAuthIdentity> findByProviderAndProviderUserId(String provider,String providerUserId);
}
