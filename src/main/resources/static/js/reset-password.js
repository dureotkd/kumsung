const resetToken=new URLSearchParams(location.hash.slice(1)).get("token");
const forgotSection=document.querySelector("#forgotSection"),resetSection=document.querySelector("#resetSection"),resetMessage=document.querySelector("#resetMessage");
const forgotForm=document.querySelector("#forgotForm"),resetForm=document.querySelector("#resetForm");
forgotSection.hidden=Boolean(resetToken);resetSection.hidden=!resetToken;

forgotForm.onsubmit=async(event)=>{
  event.preventDefault();resetMessage.textContent="";const button=forgotForm.querySelector("button");button.disabled=true;
  try{const result=await api("/api/auth/password/forgot",{method:"POST",body:JSON.stringify({email:forgotForm.email.value})});resetMessage.textContent=result.message;forgotForm.reset();}
  catch(error){resetMessage.textContent=error.message}finally{button.disabled=false}
};

resetForm.onsubmit=async(event)=>{
  event.preventDefault();resetMessage.textContent="";
  if(resetForm.password.value!==resetForm.confirmPassword.value){resetMessage.textContent="비밀번호 확인 값이 일치하지 않습니다.";return}
  const button=resetForm.querySelector("button");button.disabled=true;
  try{const result=await api("/api/auth/password/reset",{method:"POST",body:JSON.stringify({token:resetToken,password:resetForm.password.value})});resetMessage.textContent=result.message;resetForm.hidden=true;history.replaceState(null,"",location.pathname);}
  catch(error){resetMessage.textContent=error.message}finally{button.disabled=false}
};
