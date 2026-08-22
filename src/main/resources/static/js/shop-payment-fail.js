(async()=>{
  const params=new URLSearchParams(location.search);const orderId=(params.get("orderId")||"").slice(0,64);const code=(params.get("code")||"UNKNOWN").slice(0,100);const message=(params.get("message")||"결제가 취소되었거나 처리 중 문제가 발생했습니다.").slice(0,500);
  paymentFailCode.textContent=code;paymentFailMessage.textContent=message;
  if(orderId){paymentFailOrderId.textContent=orderId;paymentFailOrderRow.hidden=false}
  history.replaceState(null,"",location.pathname);
  try{
    const config=await fetch("/api/public/shop/payment/config").then(response=>response.json());paymentFailBadge.textContent=config.testMode?"TEST PAYMENT":"TOSS PAYMENT";paymentFailBadge.classList.toggle("test",Boolean(config.testMode));
    if(!orderId)return;
    const csrf=await fetch("/api/auth/csrf").then(response=>response.json());
    await fetch("/api/public/shop/payments/fail",{method:"POST",headers:{"Content-Type":"application/json",[csrf.headerName]:csrf.token},body:JSON.stringify({orderId,code,message})});
  }catch(_){/* 실패 화면은 후속 기록 API가 일시 실패해도 구매자에게 계속 표시합니다. */}
})();
