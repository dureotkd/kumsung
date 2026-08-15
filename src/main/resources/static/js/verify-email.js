const verificationParams=new URLSearchParams(location.hash.slice(1));
const verificationToken=verificationParams.get("token")||"";
history.replaceState({},"","/verify-email.html");

const verifyButton=document.querySelector("#verifyButton");
const verificationMessage=document.querySelector("#verificationMessage");

if(!verificationToken){
  verifyButton.disabled=true;
  verificationMessage.textContent="인증 주소가 만료되었거나 올바르지 않습니다.";
}

verifyButton.onclick=async()=>{
  verifyButton.disabled=true;
  verificationMessage.textContent="이메일 주소를 인증하고 있습니다.";
  try{
    await api("/api/auth/verify",{method:"POST",body:JSON.stringify({token:verificationToken})});
    location.replace("/login.html?verified=true");
  }catch(error){
    verificationMessage.textContent=error.message;
  }
};
