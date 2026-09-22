package kr.co.kumsungenc.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerLoginSuccessHandlerTest {
    @Test void incompleteMemberIsSentToProfileAndCompletedMemberToPortal(){
        AppUserRepository users=mock(AppUserRepository.class);
        AppUser member=new AppUser();member.setName("네이버 회원");
        when(users.findByEmailIgnoreCase("naver@example.com")).thenReturn(Optional.of(member));
        CustomerLoginSuccessHandler handler=new CustomerLoginSuccessHandler(users);
        var authentication=new UsernamePasswordAuthenticationToken("naver@example.com","unused",List.of());
        var response=new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(),response,authentication);
        assertEquals(302,response.getStatus());assertEquals("/profile.html",response.getHeader("Location"));
        member.setName("담당자");member.setCompanyName("테스트 회사");member.setPhone("010-1234-5678");
        response=new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(),response,authentication);
        assertEquals("/portal.html",response.getHeader("Location"));
    }

    @Test void profileRequiresCompanyContactNameAndUsablePhone(){
        AppUser member=new AppUser();member.setName("담당자");member.setPhone("010-1234-5678");
        assertFalse(member.hasCompleteProfile());
        member.setCompanyName(" ");assertFalse(member.hasCompleteProfile());
        member.setCompanyName("회사");member.setPhone("-------");assertFalse(member.hasCompleteProfile());
        member.setPhone("010-1234-5678");assertTrue(member.hasCompleteProfile());
        member.setName("네이버 회원");assertFalse(member.hasCompleteProfile());
    }
}
