const productHost=document.querySelector("#shopProducts");
const shopForm=document.querySelector("#shopInquiryForm");
const shopMessage=document.querySelector("#shopMessage");
const shopDialog=document.querySelector("#shopSuccess");
const shopQuickNav=document.querySelector("#shopQuickNav");
const shopSelectionBar=document.querySelector("#shopSelectionBar");
const shopSelectionCount=document.querySelector("#shopSelectionCount");
const checkoutDialog=document.querySelector("#shopCheckout");
const checkoutForm=document.querySelector("#shopCheckoutForm");
const checkoutMessage=document.querySelector("#checkoutMessage");
const checkoutProductName=document.querySelector("#checkoutProductName");
const checkoutAmount=document.querySelector("#checkoutAmount");
const checkoutPaymentMode=document.querySelector("#checkoutPaymentMode");
const checkoutPaymentNotice=document.querySelector("#checkoutPaymentNotice");
const paymentBadge=document.querySelector("#shopPaymentBadge");
const shopPaymentFooter=document.querySelector("#shopPaymentFooter");

const catalogSections=[
  {key:"standard",title:"표준 규격 제품",description:"별도 도면 없이 바로 주문 가능한 규격 제품이에요.",codes:["DRY_PD","FIRE_HYDRANT_BOX","WATERPROOF_EQUIPMENT_BOX","SEISMIC_FRAME"]},
  {key:"site",title:"현장별 개별 결제",description:"견적 확정 후 담당자가 등록한 현장별 금액을 바로 결제할 수 있어요.",codes:["SITE_GANGNAM","SITE_SEOCHO","SITE_BANPO"]},
  {key:"other",title:"기타",description:"부속·소모품 단가를 확인하고 바로 결제하거나, 필요한 규격을 문의하세요.",codes:["ACCESSORY_HANDLE","ACCESSORY_PIN","ACCESSORY_PUSH_BUTTON","OTHER_1","OTHER_2"]}
];
const standardIcons={DRY_PD:"▥",FIRE_HYDRANT_BOX:"▣",WATERPROOF_EQUIPMENT_BOX:"⌑",SEISMIC_FRAME:"△"};
let products=[];
let paymentConfig={ready:false,mode:"TEST",testMode:true};
const newShopSubmissionKey=()=>globalThis.crypto?.randomUUID?.()||"xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g,c=>{const r=Math.random()*16|0;return(c==="x"?r:(r&3|8)).toString(16)});
let shopSubmissionKey=newShopSubmissionKey();

function esc(value){return String(value??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));}
function won(value){return `₩${Number(value).toLocaleString("ko-KR")}`;}
function productMap(){return new Map(products.map(product=>[product.code,product]));}

function renderStandardItem(product,index){
  return `<article class="shop-catalog-item shop-standard-item">
    <div class="shop-item-icon icon-${index+1}" aria-hidden="true">${standardIcons[product.code]||"□"}</div>
    <div class="shop-item-copy"><strong>${esc(product.name)}</strong><small>${esc(product.description||"")}</small></div>
    <div class="shop-item-action"><strong class="shop-unit-price">${won(product.price)}<small>/ EA</small></strong><button type="button" data-buy="${product.id}" ${paymentConfig.ready?"":"disabled"} aria-label="${esc(product.name)} 결제하기">›</button></div>
  </article>`;
}

function renderSiteItem(product){
  return `<article class="shop-catalog-item shop-site-item">
    <div class="shop-item-copy"><strong>${esc(product.name)}</strong><small>${esc(product.description||"견적 확정 완료")}</small></div>
    <div class="shop-item-action"><strong class="shop-unit-price">${won(product.price)}</strong><button class="shop-pay-button" type="button" data-buy="${product.id}" ${paymentConfig.ready?"":"disabled"}>${paymentConfig.ready?"결제하기":"준비 중"}</button></div>
  </article>`;
}

function renderOtherItem(product,requested){
  const checked=requested===product.code;
  return `<article class="shop-catalog-item shop-inquiry-item">
    <div class="shop-inquiry-summary">
      <div class="shop-item-copy"><strong>${esc(product.name)}</strong></div>
      <div class="shop-item-action shop-other-actions">
        <strong class="shop-unit-price">${won(product.price)}<small>/ EA</small></strong>
        <button class="shop-pay-button" type="button" data-buy="${product.id}" ${paymentConfig.ready?"":"disabled"}>${paymentConfig.ready?"결제하기":"준비 중"}</button>
        <button class="shop-inquiry-button" type="button" data-inquire="${product.id}" aria-pressed="${checked}">${checked?"선택됨":"문의하기"}</button>
      </div>
      <input type="checkbox" data-id="${product.id}" aria-label="${esc(product.name)} 선택" ${checked?"checked":""}>
    </div>
    <div class="sync-product-options" ${checked?"":"hidden"}>
      <label>도면·이미지 업로드 <small>선택, PDF·JPG·PNG·DWG·DXF</small><input type="file" multiple accept=".pdf,.jpg,.jpeg,.png,.dwg,.dxf" data-files></label>
      <label>수량<input type="number" min="1" max="1000" value="1" data-quantity></label>
      <label class="sync-product-request">제품별 요청<textarea maxlength="1000" rows="3" data-specifications placeholder="규격·치수·재질·희망 납기 등을 적어 주세요."></textarea></label>
    </div>
  </article>`;
}

function renderProducts(){
  const byCode=productMap();
  const requested=new URLSearchParams(location.search).get("product");
  shopQuickNav.innerHTML=catalogSections.map((section,index)=>`<a href="#shop-category-${index+1}">${esc(section.title)}</a>`).join("");
  productHost.innerHTML=catalogSections.map((section,sectionIndex)=>{
    const items=section.codes.map(code=>byCode.get(code)).filter(Boolean);
    const rows=items.map((product,index)=>section.key==="standard"?renderStandardItem(product,index):section.key==="site"?renderSiteItem(product):renderOtherItem(product,requested)).join("");
    return `<section id="shop-category-${sectionIndex+1}" class="shop-catalog-section shop-section-${section.key}">
      <div class="sync-section-title"><span class="sync-cat-num">${String(sectionIndex+1).padStart(2,"0")}</span><h2>${esc(section.title)}</h2></div>
      <p class="sync-section-desc">${esc(section.description)}</p>
      <div class="shop-catalog-list">${rows||'<p class="sync-empty">상품 정보를 준비하고 있습니다.</p>'}</div>
    </section>`;
  }).join("");
  updateSelectionCount();
}

function updateSelectionCount(){
  const selected=productHost.querySelectorAll('input[type="checkbox"][data-id]:checked');
  shopSelectionCount.textContent=selected.length;
  shopSelectionBar.hidden=selected.length===0;
  productHost.querySelectorAll("[data-inquire]").forEach(button=>{
    const checkbox=button.closest("article").querySelector('input[type="checkbox"][data-id]');
    button.textContent=checkbox.checked?"선택됨":"문의하기";
    button.setAttribute("aria-pressed",String(checkbox.checked));
  });
}

productHost.addEventListener("change",event=>{
  if(!event.target.matches('input[type="checkbox"][data-id]'))return;
  event.target.closest("article").querySelector(".sync-product-options").hidden=!event.target.checked;
  updateSelectionCount();
});

productHost.addEventListener("click",event=>{
  const inquiryButton=event.target.closest("[data-inquire]");
  if(inquiryButton){
    const card=inquiryButton.closest("article");
    const checkbox=card.querySelector('input[type="checkbox"][data-id]');
    checkbox.checked=!checkbox.checked;
    card.querySelector(".sync-product-options").hidden=!checkbox.checked;
    updateSelectionCount();
    if(checkbox.checked)card.querySelector(".sync-product-options").scrollIntoView({behavior:"smooth",block:"nearest"});
    return;
  }
  const buyButton=event.target.closest("[data-buy]");
  if(!buyButton)return;
  const product=products.find(item=>Number(item.id)===Number(buyButton.dataset.buy));
  if(!product||!paymentConfig.ready)return;
  checkoutForm.reset();
  checkoutForm.productId.value=product.id;
  checkoutForm.quantity.value=1;
  checkoutForm.quantity.readOnly=false;
  checkoutForm.quantity.max="1000";
  checkoutForm.classList.remove("site-payment");
  checkoutForm.querySelector(".sync-checkout-grid label").childNodes[0].textContent="수량";
  checkoutProductName.textContent=product.name;
  checkoutDialog.dataset.unitPrice=product.price;
  checkoutDialog.dataset.productCode=product.code;
  updateCheckoutAmount();
  checkoutMessage.textContent="";
  checkoutDialog.showModal();
});

async function loadProducts(){
  const response=await fetch("/api/public/shop/products");
  if(!response.ok)throw new Error("제품 정보를 불러오지 못했습니다.");
  products=await response.json();
}

async function loadPaymentConfig(){
  const response=await fetch("/api/public/shop/payment/config");
  if(!response.ok)throw new Error("결제 설정을 불러오지 못했습니다.");
  paymentConfig=await response.json();
  paymentBadge.textContent=paymentConfig.ready?(paymentConfig.testMode?"테스트 결제 가능":"결제 가능"):"결제 준비 중";
  paymentBadge.classList.toggle("test",Boolean(paymentConfig.testMode));
  shopPaymentFooter.textContent=paymentConfig.testMode?"© 2026 KUMSUNG ENC CO., LTD. · 현재 토스페이먼츠 테스트 결제로 실제 금액은 차감되지 않습니다.":"© 2026 KUMSUNG ENC CO., LTD. · 안전한 결제는 토스페이먼츠에서 처리됩니다.";
  checkoutPaymentMode.textContent=paymentConfig.testMode?"TEST PAYMENT":"TOSS PAYMENT";
  checkoutPaymentNotice.textContent=paymentConfig.testMode?"테스트 결제로 실제 금액은 차감되지 않습니다.":"토스페이먼츠 결제창에서 안전하게 결제합니다.";
}

function updateCheckoutAmount(){
  const quantity=Number(checkoutForm.quantity.value)||0;
  const unitPrice=Number(checkoutDialog.dataset.unitPrice)||0;
  checkoutAmount.textContent=`${(quantity*unitPrice).toLocaleString("ko-KR")}원`;
}
checkoutForm.quantity.addEventListener("input",updateCheckoutAmount);
checkoutDialog.querySelector("[data-checkout-close]").onclick=()=>checkoutDialog.close();
checkoutDialog.addEventListener("click",event=>{if(event.target===checkoutDialog)checkoutDialog.close();});

checkoutForm.addEventListener("submit",async event=>{
  event.preventDefault();
  checkoutMessage.textContent="";
  if(!checkoutForm.reportValidity())return;
  const button=checkoutForm.querySelector('button[type="submit"]');
  button.disabled=true;
  button.textContent="주문을 생성하고 있습니다...";
  let csrf=null;
  let createdOrder=null;
  try{
    if(typeof TossPayments!=="function")throw new Error("토스페이먼츠 결제 모듈을 불러오지 못했습니다.");
    const data=Object.fromEntries(new FormData(checkoutForm));
    data.productId=Number(data.productId);
    data.quantity=Number(data.quantity);
    data.privacyAgreed=checkoutForm.privacyAgreed.checked;
    csrf=await fetch("/api/auth/csrf").then(response=>response.json());
    const response=await fetch("/api/public/shop/orders",{method:"POST",headers:{"Content-Type":"application/json",[csrf.headerName]:csrf.token},body:JSON.stringify(data)});
    createdOrder=await response.json();
    if(!response.ok)throw new Error(createdOrder.message||"주문 생성에 실패했습니다.");
    checkoutMessage.textContent="토스페이먼츠 결제창을 여는 중입니다.";
    checkoutDialog.close();
    const tossPayments=TossPayments(createdOrder.clientKey);
    const payment=tossPayments.payment({customerKey:createdOrder.customerKey});
    await payment.requestPayment({method:"CARD",amount:{currency:"KRW",value:Number(createdOrder.amount)},orderId:createdOrder.orderId,orderName:createdOrder.orderName,customerName:createdOrder.customerName,customerEmail:createdOrder.customerEmail,customerMobilePhone:createdOrder.customerMobilePhone,successUrl:`${location.origin}/shop-payment-success.html`,failUrl:`${location.origin}/shop-payment-fail.html?orderId=${encodeURIComponent(createdOrder.orderId)}`});
  }catch(error){
    const code=String(error?.code||"PAYMENT_WINDOW_ERROR").slice(0,80);
    const message=String(error?.message||"결제창을 열지 못했습니다.").slice(0,300);
    if(createdOrder?.orderId&&csrf){try{await fetch("/api/public/shop/payments/fail",{method:"POST",headers:{"Content-Type":"application/json",[csrf.headerName]:csrf.token},body:JSON.stringify({orderId:createdOrder.orderId,code,message})});}catch(_){/* 구매자 안내를 우선합니다. */}}
    checkoutMessage.textContent=`[${code}] ${message}`;
    if(!checkoutDialog.open)checkoutDialog.showModal();
  }finally{
    button.disabled=false;
    button.textContent="토스페이먼츠로 결제하기";
  }
});

shopForm.addEventListener("submit",async event=>{
  event.preventDefault();
  shopMessage.textContent="";
  if(!shopForm.reportValidity())return;
  let values;
  try{values=KumsungMemo.parse(shopForm.shopMemo.value,[
    {key:"companyName",name:"회사명",labels:["회사명"],max:150},
    {key:"contactName",name:"담당자성함 및 직책",labels:["담당자성함 및 직책","담당자성함","담당자"],max:100},
    {key:"phone",name:"연락처",labels:["연락처"],max:30},
    {key:"email",name:"이메일",labels:["이메일"],max:120},
    {key:"message",name:"문의내용",labels:["문의내용","문의 내용"],max:5000}
  ]);}catch(error){shopMessage.textContent=error.message;return;}
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email)){shopMessage.textContent="이메일 형식을 확인해 주세요.";return;}

  const items=[];
  const uploadFiles=[];
  productHost.querySelectorAll('input[type="checkbox"][data-id]:checked').forEach(checkbox=>{
    const card=checkbox.closest("article");
    const attachmentIndexes=[];
    Array.from(card.querySelector("[data-files]").files).forEach(file=>{attachmentIndexes.push(uploadFiles.length);uploadFiles.push(file);});
    items.push({productId:Number(checkbox.dataset.id),quantity:Number(card.querySelector("[data-quantity]").value),specifications:card.querySelector("[data-specifications]").value.trim(),attachmentIndexes});
  });
  if(!items.length){shopMessage.textContent="문의할 제품을 1개 이상 선택해 주세요.";return;}
  if(uploadFiles.length>20){shopMessage.textContent="첨부파일은 전체 20개까지 등록할 수 있습니다.";return;}
  values.website=shopForm.website.value;
  values.privacyAgreed=shopForm.privacyAgreed.checked;
  values.items=items;
  values.submissionKey=shopSubmissionKey;
  const body=new FormData();
  body.append("request",new Blob([JSON.stringify(values)],{type:"application/json"}));
  uploadFiles.forEach(file=>body.append("files",file));
  const button=shopForm.querySelector(".submit-shop");
  button.disabled=true;
  button.textContent="접수 중입니다...";
  try{
    const csrf=await fetch("/api/auth/csrf").then(response=>response.json());
    const response=await fetch("/api/public/shop/inquiries",{method:"POST",headers:{[csrf.headerName]:csrf.token},body});
    const result=await response.json();
    if(!response.ok)throw new Error(result.message||"접수 중 오류가 발생했습니다.");
    document.querySelector("#shopReceipt").textContent=result.receiptNumber;
    shopDialog.showModal();
    shopForm.reset();
    shopForm.shopMemo.value="회사명:\n담당자성함 및 직책:\n연락처:\n이메일:\n문의내용:";
    shopSubmissionKey=newShopSubmissionKey();
    renderProducts();
  }catch(error){shopMessage.textContent=error.message;}
  finally{button.disabled=false;button.textContent="SMART SHOP 문의 접수 →";}
});

shopDialog.querySelector("button").addEventListener("click",()=>shopDialog.close());
Promise.all([loadPaymentConfig(),loadProducts()]).then(renderProducts).catch(error=>{shopMessage.textContent=error.message;});
