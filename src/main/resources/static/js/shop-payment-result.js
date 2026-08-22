(async()=>{
  const params=new URLSearchParams(location.search);const paymentKey=params.get("paymentKey");const orderId=params.get("orderId");const amount=Number(params.get("amount"));
  if(!paymentKey||!orderId||!Number.isSafeInteger(amount)||amount<=0){showError("결제 승인 정보가 올바르지 않습니다.");return}
  try{
    const csrf=await fetch("/api/auth/csrf").then(response=>response.json());
    const response=await fetch("/api/public/shop/payments/confirm",{method:"POST",headers:{"Content-Type":"application/json",[csrf.headerName]:csrf.token},body:JSON.stringify({paymentKey,orderId,amount})});
    const result=await response.json();if(!response.ok)throw new Error(result.message||"결제 승인에 실패했습니다.");
    history.replaceState(null,"",location.pathname);
    paymentResultBadge.textContent=result.testMode?"TEST PAYMENT":"TOSS PAYMENT";paymentResultBadge.classList.toggle("test",Boolean(result.testMode));
    paymentResultIcon.textContent="✓";paymentResultIcon.classList.add("ok");paymentResultTitle.textContent=result.testMode?"테스트 결제가 완료되었습니다.":"결제가 완료되었습니다.";paymentResultMessage.textContent=result.testMode?"실제 금액은 차감되지 않았습니다.":"주문이 정상적으로 접수되었습니다. 담당자가 후속 절차를 안내드립니다.";
    resultOrderId.textContent=result.orderId;resultProduct.textContent=result.productName;resultAmount.textContent=`${Number(result.amount).toLocaleString("ko-KR")}원`;resultMethod.textContent=result.method||"카드·간편결제";paymentResultDetails.hidden=false;
    if(result.receiptUrl){resultReceipt.href=result.receiptUrl;resultReceipt.hidden=false}
  }catch(error){showError(error.message)}
})();
function showError(message){paymentResultIcon.textContent="!";paymentResultIcon.classList.add("fail");paymentResultTitle.textContent="결제 승인에 실패했습니다.";paymentResultMessage.textContent=message}
