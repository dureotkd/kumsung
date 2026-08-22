const shopAdminTitles={products:"제품 관리",payments:"토스페이먼츠"};
let shopProductRows=new Map();
const shopEmpty=text=>`<p class="empty">${text}</p>`;
const shopTable=(rows,columns)=>rows.length?`<div class="table-scroll"><table class="table"><thead><tr>${columns.map(column=>`<th>${column[0]}</th>`).join("")}</tr></thead><tbody>${rows.map(row=>`<tr>${columns.map(column=>`<td>${typeof column[2]==="function"?column[2](row[column[1]],row):esc(row[column[1]])}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`:shopEmpty("등록된 제품이 없습니다.");
const won=value=>value==null?"가격 문의":`${Number(value).toLocaleString("ko-KR")}원`;
function shopNotify(message){toast.textContent=message;toast.classList.add("show");setTimeout(()=>toast.classList.remove("show"),2600)}
function showShopAdminView(view){if(!shopAdminTitles[view])return;document.querySelectorAll(".view").forEach(section=>section.classList.toggle("active",section.id===view));document.querySelectorAll(".side-nav button").forEach(button=>button.classList.toggle("active",button.dataset.view===view));shopAdminTitle.textContent=shopAdminTitles[view];if(view==="products"){loadShopSummary();loadShopProducts()}else Promise.all([loadTossSettings(),loadShopOrders()])}
document.querySelectorAll(".side-nav button").forEach(button=>button.onclick=()=>showShopAdminView(button.dataset.view));

async function initShopAdmin(){
  const me=await api("/api/auth/me");
  if(me.role!=="ADMIN"){location.replace("/portal.html");return}
  if(!["SHOP_ADMIN","SUPER_ADMIN"].includes(me.adminRole)){location.replace("/admin.html");return}
  shopAdminName.textContent=me.name;mainAdminLink.hidden=me.adminRole!=="SUPER_ADMIN";
  await Promise.all([loadShopSummary(),loadShopProducts()]);
}

async function loadShopSummary(){
  const summary=await api("/api/shop-admin/summary");
  shopProductCount.textContent=summary.products;shopActiveCount.textContent=summary.active_products;shopPricedCount.textContent=summary.priced_products;
  shopPaymentState.textContent=summary.paymentReady?(summary.paymentMode==="LIVE"?"LIVE":"TEST"):summary.paymentEnabled?"설정 확인":"비활성";
}

async function loadShopProducts(){
  try{
    const page=await apiPage("/api/shop-admin/products","shop-admin-products");shopProductRows=new Map(page.items.map(product=>[Number(product.id),product]));
    const html=shopTable(page.items,[["이미지","imageUrl",value=>value?`<img class="admin-thumb" src="${esc(value)}" alt="">`:"-"],["순서","display_order"],["분류","category"],["제품","name",(value,row)=>`<strong>${esc(value)}</strong><br><small>${esc(row.code)}</small>`],["가격","price",value=>`<strong class="price-cell">${won(value)}</strong>`],["공개","active",(value,row)=>`<button class="action" onclick="toggleShopProduct(${row.id},${!value})">${value?"공개":"비공개"}</button>`],["관리","id",value=>`<button class="action" onclick="editShopProduct(${value})">수정</button>`]]);
    renderPage(shopAdminProductTable,"shop-admin-products",page,html,loadShopProducts);
  }catch(error){shopNotify(error.message)}
}

function editShopProduct(id){
  const product=shopProductRows.get(Number(id));if(!product)return;
  const form=shopAdminProductForm;form.id.value=product.id;form.code.value=product.code||"";form.name.value=product.name||"";form.category.value=product.category||"";form.price.value=product.price??"";form.description.value=product.description||"";form.displayOrder.value=product.display_order??0;form.active.checked=Boolean(product.active);form.image.value="";form.scrollIntoView({behavior:"smooth",block:"center"});
}
function resetShopProductForm(){shopAdminProductForm.reset();shopAdminProductForm.id.value="";shopAdminProductForm.displayOrder.value=0;shopAdminProductForm.active.checked=true}
async function toggleShopProduct(id,active){try{await api(`/api/shop-admin/products/${id}/status`,{method:"PUT",body:JSON.stringify({active})});shopNotify("제품 공개 상태를 변경했습니다.");await Promise.all([loadShopSummary(),loadShopProducts()])}catch(error){shopNotify(error.message)}}

shopAdminProductReset.onclick=resetShopProductForm;
shopAdminProductForm.onsubmit=async event=>{
  event.preventDefault();const id=shopAdminProductForm.id.value;const body=new FormData(shopAdminProductForm);body.delete("id");body.set("active",String(shopAdminProductForm.active.checked));if(!String(body.get("price")||"").trim())body.delete("price");
  const button=shopAdminProductForm.querySelector('button[type="submit"]');button.disabled=true;
  try{await api(id?`/api/shop-admin/products/${id}`:"/api/shop-admin/products",{method:id?"PUT":"POST",body});shopNotify(id?"제품 정보를 수정했습니다.":"제품을 등록했습니다.");resetShopProductForm();resetPage("shop-admin-products");await Promise.all([loadShopSummary(),loadShopProducts()])}catch(error){shopNotify(error.message)}finally{button.disabled=false}
};

async function loadTossSettings(){
  try{
    const settings=await api("/api/shop-admin/toss-payments");tossSettingsForm.mode.value=settings.mode;tossSettingsForm.clientKey.value=settings.clientKey||"";tossSettingsForm.clientKey.readOnly=Boolean(settings.clientKeyManagedByEnvironment);tossSettingsForm.enabled.checked=Boolean(settings.enabled);
    tossReadyBadge.textContent=settings.ready?"사용 준비 완료":settings.enabled?"설정 확인 필요":"비활성";tossReadyBadge.className=`badge ${settings.ready?"ok":"warn"}`;
    tossStatusGrid.innerHTML=[
      ["운영 모드",settings.mode==="LIVE"?"라이브":"테스트",settings.mode==="LIVE"?"실결제용 설정":"가상 결제 검증용"],
      ["클라이언트 키",settings.clientKeyConfigured?"설정됨":"미설정","브라우저 SDK 초기화 키"],
      ["서버 시크릿 키",settings.secretKeyConfigured?`${settings.secretKeyMode} 키 설정됨`:"미설정","서버 환경변수로만 관리"],
      ["키 형식 조합",settings.keyPairMatches?(settings.keyFamily==="WIDGET"?"결제위젯 키":"API 개별 연동 키"):"확인 필요","모드·키 종류 일치, MID는 개발자센터 확인"]
    ].map(item=>`<div class="payment-status"><small>${item[0]}</small><strong>${esc(item[1])}</strong><span>${item[2]}</span></div>`).join("");
  }catch(error){shopNotify(error.message)}
}

async function loadShopOrders(){
  try{
    const page=await apiPage("/api/shop-admin/orders","shop-admin-orders");
    const status=value=>({READY:"결제 대기",PAID:"결제 완료",FAILED:"결제 실패",CANCELED:"취소"}[value]||value);
    const html=shopTable(page.items,[["주문일","created_at",value=>fmt(value)],["주문번호","order_id",value=>`<small>${esc(value)}</small>`],["제품","product_name",(value,row)=>`<strong>${esc(value)}</strong><br><small>${esc(row.product_code)} · ${row.quantity}개</small>`],["결제금액","amount",value=>`<strong class="price-cell">${won(value)}</strong>`],["구매자·배송","buyer_name",(value,row)=>`${esc(value)}<br><small>${esc(row.buyer_phone)}<br>${esc(row.buyer_email)}<br>${esc(row.delivery_address)}</small>`],["상태","status",(value,row)=>`<span class="badge ${value==="PAID"?"ok":"warn"}" title="${esc(row.failure_message||row.cancel_reason||"")}">${esc(status(value))}</span>`],["영수증","receipt_url",value=>value?`<a class="action" href="${esc(value)}" target="_blank" rel="noopener">보기</a>`:"-"],["관리","id",(value,row)=>row.status==="PAID"?`<button class="action" onclick="cancelShopOrder(${value})">결제 취소</button>`:"-"]]);
    renderPage(shopOrderTable,"shop-admin-orders",page,html,loadShopOrders);
  }catch(error){shopNotify(error.message)}
}

async function cancelShopOrder(id){
  const reason=prompt("구매자와 결제 내역에 남길 전액 취소 사유를 입력해 주세요.");if(reason===null)return;if(!reason.trim()){shopNotify("취소 사유를 입력해 주세요.");return}
  if(!confirm("이 결제를 토스페이먼츠에서 전액 취소하시겠습니까?"))return;
  try{await api(`/api/shop-admin/orders/${id}/cancel`,{method:"POST",body:JSON.stringify({reason:reason.trim()})});shopNotify("결제를 취소했습니다.");await loadShopOrders()}catch(error){shopNotify(error.message)}
}

tossSettingsForm.onsubmit=async event=>{
  event.preventDefault();const data=Object.fromEntries(new FormData(tossSettingsForm));data.enabled=tossSettingsForm.enabled.checked;
  if(data.enabled&&!confirm("현재 키 설정으로 토스페이먼츠 모듈을 활성화하시겠습니까?"))return;
  const button=tossSettingsForm.querySelector('button[type="submit"]');button.disabled=true;
  try{await api("/api/shop-admin/toss-payments",{method:"PUT",body:JSON.stringify(data)});shopNotify("토스페이먼츠 설정을 저장했습니다.");await Promise.all([loadTossSettings(),loadShopSummary()])}catch(error){shopNotify(error.message)}finally{button.disabled=false}
};

initShopAdmin().catch(error=>shopNotify(error.message));
