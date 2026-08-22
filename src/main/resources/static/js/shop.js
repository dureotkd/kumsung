const productHost=document.querySelector("#shopProducts");
const shopForm=document.querySelector("#shopInquiryForm");
const shopMessage=document.querySelector("#shopMessage");
const shopDialog=document.querySelector("#shopSuccess");
const shopQuickNav=document.querySelector("#shopQuickNav");
const shopSelectionCount=document.querySelector("#shopSelectionCount");
const checkoutDialog=document.querySelector("#shopCheckout");
const checkoutForm=document.querySelector("#shopCheckoutForm");
const paymentBadge=document.querySelector("#shopPaymentBadge");
let products=[];
let paymentConfig={ready:false,mode:"TEST",testMode:true};
const newShopSubmissionKey=()=>globalThis.crypto?.randomUUID?.()||"xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g,c=>{const r=Math.random()*16|0;return(c==="x"?r:(r&3|8)).toString(16)});
let shopSubmissionKey=newShopSubmissionKey();

function esc(value){return String(value??"").replace(/[&<>"']/g,(c)=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));}

function renderProducts(){
  const requested=new URLSearchParams(location.search).get("product");
  const groups=[];const groupMap=new Map();
  products.forEach(product=>{const category=product.category||"주문제작";if(!groupMap.has(category)){const group={category,items:[]};groupMap.set(category,group);groups.push(group)}groupMap.get(category).items.push(product)});
  shopQuickNav.innerHTML=groups.map((group,index)=>`<a href="#shop-category-${index+1}">${esc(group.category)}</a>`).join("");
  let productIndex=0;
  productHost.innerHTML=groups.map((group,groupIndex)=>`<section id="shop-category-${groupIndex+1}" class="sync-category"><div class="sync-section-head"><div class="sync-section-title"><span class="sync-cat-num">${String(groupIndex+1).padStart(2,"0")}</span><h2>${esc(group.category)}</h2></div></div><p class="sync-section-desc">${esc(group.category)} 제품을 구매하거나 제작 문의로 선택해 주세요.</p><div class="sync-item-list">${group.items.map(product=>{productIndex++;const purchasable=Number(product.price)>0;return `<article class="sync-item">
    <label class="sync-product-summary"><span class="sync-thumb">${product.imageUrl?`<img src="${esc(product.imageUrl)}" alt="" loading="lazy">`:esc(product.name.slice(0,1))}</span><span class="sync-product-copy"><strong class="sync-product-name">${esc(product.name)}</strong><small class="sync-product-spec">${esc(product.description||product.code)}</small><strong class="sync-product-price">${product.price==null?"가격 문의":`${Number(product.price).toLocaleString("ko-KR")}원`}</strong></span><span class="sync-tag">${String(productIndex).padStart(2,"0")}</span><input type="checkbox" data-id="${product.id}" aria-label="${esc(product.name)} 선택" ${requested===product.code?"checked":""}></label>
    ${purchasable?`<div class="sync-product-buy"><span>${paymentConfig.testMode?"TEST · 실제 금액 미차감":"토스페이먼츠 결제"}</span><button type="button" data-buy="${product.id}" ${paymentConfig.ready?"":"disabled"}>${paymentConfig.ready?"구매하기":"결제 준비 중"}</button></div>`:""}
    <div class="sync-product-options" ${requested===product.code?"":"hidden"}>
      <label>도면·이미지 업로드 <small>선택, PDF·JPG·PNG·DWG·DXF</small><input type="file" multiple accept=".pdf,.jpg,.jpeg,.png,.dwg,.dxf" data-files></label>
      <label>수량<input type="number" min="1" max="999" value="1" data-quantity></label>
      <label class="sync-product-request">제품별 요청<textarea maxlength="1000" rows="3" data-specifications placeholder="규격·치수·재질·희망 납기 등을 적어 주세요."></textarea></label>
    </div>
  </article>`}).join("")}</div></section>`).join("");
  updateSelectionCount();
}

function updateSelectionCount(){shopSelectionCount.textContent=productHost.querySelectorAll('input[type="checkbox"][data-id]:checked').length}

productHost.addEventListener("change",(event)=>{
  if(!event.target.matches('input[type="checkbox"][data-id]'))return;
  event.target.closest("article").querySelector(".sync-product-options").hidden=!event.target.checked;updateSelectionCount();
});

async function loadProducts(){
  const response=await fetch("/api/public/shop/products");
  if(!response.ok)throw new Error("제품 정보를 불러오지 못했습니다.");
  products=await response.json();renderProducts();
}

async function loadPaymentConfig(){
  const response=await fetch("/api/public/shop/payment/config");if(!response.ok)throw new Error("결제 설정을 불러오지 못했습니다.");
  paymentConfig=await response.json();paymentBadge.textContent=paymentConfig.ready?(paymentConfig.testMode?"테스트 결제 가능":"결제 가능"):"결제 준비 중";paymentBadge.classList.toggle("test",Boolean(paymentConfig.testMode));
  shopPaymentEyebrow.textContent=`SMART SHOP · TOSS PAYMENTS ${paymentConfig.testMode?"TEST":""}`.trim();
  shopPaymentIntro.textContent=paymentConfig.testMode?"가격이 등록된 제품은 토스페이먼츠 테스트 결제로 바로 구매할 수 있습니다. 실제 금액은 차감되지 않습니다.":"가격이 등록된 제품은 토스페이먼츠로 바로 구매할 수 있습니다. 주문제작 제품은 제작 문의를 남겨 주세요.";
  shopPaymentFooter.textContent=paymentConfig.testMode?"© 2026 KUMSUNG ENC CO., LTD. · 현재 토스페이먼츠 테스트 결제로 실제 금액은 차감되지 않습니다.":"© 2026 KUMSUNG ENC CO., LTD. · 안전한 결제는 토스페이먼츠에서 처리됩니다.";
  checkoutPaymentMode.textContent=paymentConfig.testMode?"TEST PAYMENT":"TOSS PAYMENT";checkoutPaymentNotice.textContent=paymentConfig.testMode?"테스트 결제로 실제 금액은 차감되지 않습니다.":"토스페이먼츠 결제창에서 안전하게 결제합니다.";
}

productHost.addEventListener("click",event=>{
  const button=event.target.closest("[data-buy]");if(!button)return;
  const product=products.find(item=>Number(item.id)===Number(button.dataset.buy));if(!product||!paymentConfig.ready)return;
  checkoutForm.reset();checkoutForm.productId.value=product.id;checkoutForm.quantity.value=1;checkoutProductName.textContent=product.name;checkoutDialog.dataset.unitPrice=product.price;updateCheckoutAmount();checkoutMessage.textContent="";checkoutDialog.showModal();
});
function updateCheckoutAmount(){const quantity=Number(checkoutForm.quantity.value)||0;const unitPrice=Number(checkoutDialog.dataset.unitPrice)||0;checkoutAmount.textContent=`${(quantity*unitPrice).toLocaleString("ko-KR")}원`}
checkoutForm.quantity.addEventListener("input",updateCheckoutAmount);
checkoutDialog.querySelector("[data-checkout-close]").onclick=()=>checkoutDialog.close();
checkoutDialog.addEventListener("click",event=>{if(event.target===checkoutDialog)checkoutDialog.close()});

checkoutForm.addEventListener("submit",async event=>{
  event.preventDefault();checkoutMessage.textContent="";if(!checkoutForm.reportValidity())return;
  const button=checkoutForm.querySelector('button[type="submit"]');button.disabled=true;button.textContent="주문을 생성하고 있습니다...";
  let csrf=null;let createdOrder=null;
  try{
    if(typeof TossPayments!=="function")throw new Error("토스페이먼츠 결제 모듈을 불러오지 못했습니다.");
    const data=Object.fromEntries(new FormData(checkoutForm));data.productId=Number(data.productId);data.quantity=Number(data.quantity);data.privacyAgreed=checkoutForm.privacyAgreed.checked;
    csrf=await fetch("/api/auth/csrf").then(response=>response.json());
    const response=await fetch("/api/public/shop/orders",{method:"POST",headers:{"Content-Type":"application/json",[csrf.headerName]:csrf.token},body:JSON.stringify(data)});
    createdOrder=await response.json();if(!response.ok)throw new Error(createdOrder.message||"주문 생성에 실패했습니다.");
    checkoutMessage.textContent="토스페이먼츠 결제창을 여는 중입니다.";
    checkoutDialog.close();
    const tossPayments=TossPayments(createdOrder.clientKey);const payment=tossPayments.payment({customerKey:createdOrder.customerKey});
    await payment.requestPayment({method:"CARD",amount:{currency:"KRW",value:Number(createdOrder.amount)},orderId:createdOrder.orderId,orderName:createdOrder.orderName,customerName:createdOrder.customerName,customerEmail:createdOrder.customerEmail,customerMobilePhone:createdOrder.customerMobilePhone,successUrl:`${location.origin}/shop-payment-success.html`,failUrl:`${location.origin}/shop-payment-fail.html?orderId=${encodeURIComponent(createdOrder.orderId)}`});
  }catch(error){const code=String(error?.code||"PAYMENT_WINDOW_ERROR").slice(0,80);const message=String(error?.message||"결제창을 열지 못했습니다.").slice(0,300);if(createdOrder?.orderId&&csrf){try{await fetch("/api/public/shop/payments/fail",{method:"POST",headers:{"Content-Type":"application/json",[csrf.headerName]:csrf.token},body:JSON.stringify({orderId:createdOrder.orderId,code,message})})}catch(_){/* 구매자 안내를 우선하고 서버 기록은 실패 URL에서도 재시도합니다. */}}checkoutMessage.textContent=`[${code}] ${message}`;if(!checkoutDialog.open)checkoutDialog.showModal();button.disabled=false;button.textContent="토스페이먼츠로 결제하기"}
});

shopForm.addEventListener("submit",async(event)=>{
  event.preventDefault();shopMessage.textContent="";if(!shopForm.reportValidity())return;
  let values;
  try{values=KumsungMemo.parse(shopForm.shopMemo.value,[
    {key:"companyName",name:"회사명",labels:["회사명"],max:150},
    {key:"contactName",name:"담당자성함 및 직책",labels:["담당자성함 및 직책","담당자성함","담당자"],max:100},
    {key:"phone",name:"연락처",labels:["연락처"],max:30},
    {key:"email",name:"이메일",labels:["이메일"],max:120},
    {key:"message",name:"문의내용",labels:["문의내용","문의 내용"],max:5000}
  ]);}catch(error){shopMessage.textContent=error.message;return;}
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email)){shopMessage.textContent="이메일 형식을 확인해 주세요.";return;}

  const items=[];const uploadFiles=[];
  productHost.querySelectorAll('input[type="checkbox"][data-id]:checked').forEach((checkbox)=>{
    const card=checkbox.closest("article");const attachmentIndexes=[];
    Array.from(card.querySelector("[data-files]").files).forEach((file)=>{attachmentIndexes.push(uploadFiles.length);uploadFiles.push(file);});
    items.push({productId:Number(checkbox.dataset.id),quantity:Number(card.querySelector("[data-quantity]").value),specifications:card.querySelector("[data-specifications]").value.trim(),attachmentIndexes});
  });
  if(!items.length){shopMessage.textContent="문의할 제품을 1개 이상 선택해 주세요.";return;}
  if(uploadFiles.length>20){shopMessage.textContent="첨부파일은 전체 20개까지 등록할 수 있습니다.";return;}
  values.website=shopForm.website.value;values.privacyAgreed=shopForm.privacyAgreed.checked;values.items=items;values.submissionKey=shopSubmissionKey;
  const body=new FormData();body.append("request",new Blob([JSON.stringify(values)],{type:"application/json"}));uploadFiles.forEach((file)=>body.append("files",file));
  const button=shopForm.querySelector(".submit-shop");button.disabled=true;button.textContent="접수 중입니다...";
  try{
    const csrf=await fetch("/api/auth/csrf").then((r)=>r.json());
    const response=await fetch("/api/public/shop/inquiries",{method:"POST",headers:{[csrf.headerName]:csrf.token},body});
    const result=await response.json();if(!response.ok)throw new Error(result.message||"접수 중 오류가 발생했습니다.");
    document.querySelector("#shopReceipt").textContent=result.receiptNumber;shopDialog.showModal();shopForm.reset();shopForm.shopMemo.value="회사명:\n담당자성함 및 직책:\n연락처:\n이메일:\n문의내용:";shopSubmissionKey=newShopSubmissionKey();renderProducts();
  }catch(error){shopMessage.textContent=error.message;}finally{button.disabled=false;button.textContent="SMART SHOP 문의 접수 →";}
});

shopDialog.querySelector("button").addEventListener("click",()=>shopDialog.close());
Promise.all([loadPaymentConfig(),loadProducts()]).then(renderProducts).catch((error)=>{shopMessage.textContent=error.message;});
