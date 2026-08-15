(async()=>{
  const links=[...document.querySelectorAll("[data-auth-link]")];
  if(!links.length)return;
  try{
    const response=await fetch("/api/auth/me",{
      method:"GET",
      credentials:"same-origin",
      headers:{Accept:"application/json"},
      cache:"no-store"
    });
    if(!response.ok)return;
    const me=await response.json();
    const isAdmin=me.role==="ADMIN";
    const destination=isAdmin?"/admin.html":"/portal.html";
    const label=isAdmin?"관리자 콘솔":"고객 포털";
    links.forEach(link=>{
      link.href=destination;
      link.textContent=link.dataset.authLabel||label;
      link.setAttribute("aria-label",label);
    });
  }catch(_){
    // 공개 페이지는 인증 서버 확인에 실패해도 정상적으로 이용할 수 있어야 합니다.
  }
})();
