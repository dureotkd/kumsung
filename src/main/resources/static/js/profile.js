const profileForm=document.getElementById("profileForm");
const profileFields=document.getElementById("profileFields");
const profileMessage=document.getElementById("profileMessage");
const profileSave=document.getElementById("profileSave");
const requestedProfileNext=new URLSearchParams(location.search).get("next");
const profileNext=["/quote.html","/portal.html","/portal.html#privacy"].includes(requestedProfileNext)?requestedProfileNext:"/portal.html";

(async()=>{
  try{
    const me=await api("/api/auth/me",{cache:"no-store"});
    if(me.role!=="CUSTOMER"){location.replace(me.adminRole==="SHOP_ADMIN"?"/shop-admin-entry.html":"/admin.html");return}
    for(const field of ["email","companyName","name","phone"]){
      profileForm.elements.namedItem(field).value=field==="name"&&me.name==="네이버 회원"?"":me[field]||"";
    }
    profileFields.disabled=false;profileMessage.textContent="";
  }catch(error){profileMessage.textContent=error.message}
})();

profileForm.addEventListener("submit",async event=>{
  event.preventDefault();
  if(!profileForm.reportValidity())return;
  const input=Object.fromEntries(new FormData(profileForm));
  const phone=input.phone.trim();
  if(!/^[0-9+()\- ]{7,30}$/.test(phone)||phone.replace(/\D/g,"").length<7||phone.replace(/\D/g,"").length>15){
    profileMessage.textContent="연락처는 숫자 7~15자리와 하이픈(-)으로 입력해 주세요.";return;
  }
  profileSave.disabled=true;profileMessage.textContent="회원정보를 저장하고 있습니다.";
  try{
    await api("/api/auth/profile",{method:"PUT",body:JSON.stringify({
      name:input.name.trim(),companyName:input.companyName.trim(),phone,
      privacyAgreed:profileForm.elements.namedItem("privacyAgreed").checked
    })});
    location.replace(profileNext);
  }catch(error){profileMessage.textContent=error.message;profileSave.disabled=false}
});
