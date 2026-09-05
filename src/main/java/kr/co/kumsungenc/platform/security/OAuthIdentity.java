package kr.co.kumsungenc.platform.security;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="oauth_identities",uniqueConstraints={
    @UniqueConstraint(name="uq_oauth_identity",columnNames={"provider","provider_user_id"}),
    @UniqueConstraint(name="uq_oauth_user_provider",columnNames={"user_id","provider"})})
public class OAuthIdentity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY)
    @JoinColumn(name="user_id",nullable=false)
    private AppUser user;
    @Column(nullable=false,length=20) private String provider;
    @Column(name="provider_user_id",nullable=false,length=100) private String providerUserId;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="last_login_at",nullable=false) private LocalDateTime lastLoginAt;

    @PrePersist void create(){
        LocalDateTime now=LocalDateTime.now();
        createdAt=now;lastLoginAt=now;
    }
    public Long getId(){return id;}
    public AppUser getUser(){return user;} public void setUser(AppUser value){user=value;}
    public String getProvider(){return provider;} public void setProvider(String value){provider=value;}
    public String getProviderUserId(){return providerUserId;} public void setProviderUserId(String value){providerUserId=value;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public LocalDateTime getLastLoginAt(){return lastLoginAt;}
    public void setLastLoginAt(LocalDateTime value){lastLoginAt=value;}
}
