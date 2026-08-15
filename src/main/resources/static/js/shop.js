const productHost=document.querySelector("#shopProducts");
const shopForm=document.querySelector("#shopInquiryForm");
const shopMessage=document.querySelector("#shopMessage");
const shopDialog=document.querySelector("#shopSuccess");
const shopQuickNav=document.querySelector("#shopQuickNav");
const shopSelectionCount=document.querySelector("#shopSelectionCount");
let products=[];
const newShopSubmissionKey=()=>globalThis.crypto?.randomUUID?.()||"xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g,c=>{const r=Math.random()*16|0;return(c==="x"?r:(r&3|8)).toString(16)});
let shopSubmissionKey=newShopSubmissionKey();

function esc(value){return String(value??"").replace(/[&<>"']/g,(c)=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));}

function renderProducts(){
  const requested=new URLSearchParams(location.search).get("product");
  const groups=[];const groupMap=new Map();
  products.forEach(product=>{const category=product.category||"주문제작";if(!groupMap.has(category)){const group={category,items:[]};groupMap.set(category,group);groups.push(group)}groupMap.get(category).items.push(product)});
  shopQuickNav.innerHTML=groups.map((group,index)=>`<a href="#shop-category-${index+1}">${esc(group.category)}</a>`).join("");
  let productIndex=0;
  productHost.innerHTML=groups.map((group,groupIndex)=>`<section id="shop-category-${groupIndex+1}" class="sync-category"><div class="sync-section-head"><div class="sync-section-title"><span class="sync-cat-num">${String(groupIndex+1).padStart(2,"0")}</span><h2>${esc(group.category)}</h2></div></div><p class="sync-section-desc">${esc(group.category)} 제품을 선택해 제작 문의를 남겨 주세요.</p><div class="sync-item-list">${group.items.map(product=>{productIndex++;return `<article class="sync-item">
    <label class="sync-product-summary"><span class="sync-thumb">${product.imageUrl?`<img src="${esc(product.imageUrl)}" alt="" loading="lazy">`:esc(product.name.slice(0,1))}</span><span class="sync-product-copy"><strong class="sync-product-name">${esc(product.name)}</strong><small class="sync-product-spec">${esc(product.description||product.code)}</small></span><span class="sync-tag">${String(productIndex).padStart(2,"0")}</span><input type="checkbox" data-id="${product.id}" aria-label="${esc(product.name)} 선택" ${requested===product.code?"checked":""}></label>
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
loadProducts().catch((error)=>{shopMessage.textContent=error.message;});
