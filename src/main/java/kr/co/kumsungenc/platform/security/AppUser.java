package kr.co.kumsungenc.platform.security;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false, unique=true, length=120) private String email;
    @Column(name="password_hash", nullable=false) private String passwordHash;
    @Column(nullable=false, length=60) private String name;
    @Column(name="company_name", length=150) private String companyName;
    @Column(length=30) private String phone;
    @Column(nullable=false, length=20) private String role = "CUSTOMER";
    @Column(name="admin_role", length=20) private String adminRole;
    @Column(nullable=false) private boolean enabled = true;
    @Column(name="email_verified",nullable=false) private boolean emailVerified;
    @Column(name="verified_at") private LocalDateTime verifiedAt;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    @PrePersist void create(){ createdAt=LocalDateTime.now(); }
    public Long getId(){return id;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getCompanyName(){return companyName;} public void setCompanyName(String v){companyName=v;}
    public String getPhone(){return phone;} public void setPhone(String v){phone=v;}
    public String getRole(){return role;} public void setRole(String v){role=v;}
    public String getAdminRole(){return adminRole;} public void setAdminRole(String v){adminRole=v;}
    public boolean isEnabled(){return enabled;}
    public void setEnabled(boolean v){enabled=v;}
    public boolean isEmailVerified(){return emailVerified;}
    public void setEmailVerified(boolean v){emailVerified=v;}
    public LocalDateTime getVerifiedAt(){return verifiedAt;}
    public void setVerifiedAt(LocalDateTime v){verifiedAt=v;}
}
