const titles={dashboard:"대시보드",quotes:"내 견적",shop:"SMART SHOP 문의",projects:"프로젝트 관리",contracts:"계약관리",deliveries:"납품현황",invoices:"세금계산서",service:"A/S 요청",notices:"공지사항",support:"고객센터",privacy:"개인정보 관리"};
const statusClasses={RECEIVED:"status-received",REVIEWING:"status-review",SUPPLEMENT_REQUIRED:"status-supplement",SUPPLEMENTED:"status-supplement",QUOTED:"status-quoted",APPROVED:"status-approved",COMPLETED:"status-complete",CANCELLED:"status-cancelled"};
const documentTypeNames={ESTIMATE:"견적서",CONTRACT:"계약서",OTHER:"기타 문서"};
const $=id=>document.getElementById(id);
let quotes=[];
let currentQuotePage={items:[],hasNext:false,offset:0};

function notify(message){const host=$("toast");host.textContent=message;host.classList.add("show");setTimeout(()=>host.classList.remove("show"),2500)}
function togglePortalMenu(open){document.body.classList.toggle("menu-open",open)}
function showView(id){
  if(!titles[id])return;
  document.querySelectorAll(".view").forEach(view=>view.classList.toggle("active",view.id===id));
  document.querySelectorAll(".side-nav button").forEach(button=>button.classList.toggle("active",button.dataset.view===id));
  $("viewTitle").textContent=titles[id];
  if(location.hash!==`#${id}`)history.replaceState(null,"",`#${id}`);
  togglePortalMenu(false);
  if(id==="dashboard")dashboard();else loadView(id);
  window.scrollTo({top:0,behavior:"smooth"});
}
document.querySelectorAll(".side-nav button").forEach(button=>button.addEventListener("click",()=>showView(button.dataset.view)));

const empty=text=>`<div class="empty"><strong>표시할 내용이 없습니다.</strong><p>${esc(text)}</p></div>`;
function table(rows,columns,extraClass=""){
  if(!rows.length)return empty("등록된 내역이 없습니다.");
  return `<table class="table responsive-table ${extraClass}"><thead><tr>${columns.map(column=>`<th scope="col">${column[0]}</th>`).join("")}</tr></thead><tbody>${rows.map(row=>`<tr>${columns.map(column=>`<td data-label="${esc(column[0])}">${typeof column[2]==="function"?column[2](row[column[1]],row):esc(row[column[1]])}</td>`).join("")}</tr>`).join("")}</tbody></table>`;
}
function statusBadge(status){return `<span class="badge ${statusClasses[status]||""}">${esc(statusName[status]||status)}</span>`}
function quoteRows(items){
  return table(items,[
    ["접수번호","receipt_number",value=>`<button class="receipt-button" type="button" onclick="openQuote('${esc(value)}')">${esc(value)}</button>`],
    ["견적명","subject",(value,row)=>`<span class="quote-title-cell"><strong>${esc(value)}</strong><small>${esc(row.site_name||row.product_type||"현장 미지정")}</small></span>`],
    ["제품/공종","product_type"],
    ["진행 상태","status",value=>statusBadge(value)],
    ["접수일","created_at",value=>fmtDate(value)],
    ["확인","receipt_number",(receipt,row)=>`<span class="row-actions"><button class="action detail-action" type="button" onclick="openQuote('${esc(receipt)}')">상세 보기</button>${row.latest_estimate_document_id?`<a class="action download-action" href="/api/portal/quotes/${encodeURIComponent(receipt)}/documents/${row.latest_estimate_document_id}" download>견적서 받기</a>`:""}</span>`]
  ],"quote-table");
}
function fmtDate(value){if(!value)return "-";return new Date(value).toLocaleDateString("ko-KR",{year:"numeric",month:"2-digit",day:"2-digit"})}

async function init(){
  const me=await api("/api/auth/me");
  if(me.role==="ADMIN"){location.replace(me.adminRole==="SHOP_ADMIN"?"/shop-admin-entry.html":"/admin.html");return}
  $("userName").textContent=me.name;
  $("companyName").textContent=me.companyName||"개인 고객";
  $("welcomeName").textContent=me.name;
  $("userAvatar").textContent=(me.name||"고객").trim().slice(0,1).toUpperCase();
  const requested=location.hash.slice(1);
  await dashboard();
  if(requested&&requested!=="dashboard"&&titles[requested])showView(requested);
}

async function dashboard(){
  try{
    const [summary,recent]=await Promise.all([api("/api/portal/summary"),api("/api/portal/quotes?limit=5")]);
    quotes=recent;
    $("sQuotes").textContent=summary.quotes;
    $("sActive").textContent=summary.active;
    $("sProjects").textContent=summary.projects;
    $("sMessages").textContent=summary.messages;
    $("recentQuotes").innerHTML=quoteRows(recent);
  }catch(error){notify(error.message)}
}

async function loadView(id){
  try{
    if(id==="quotes"){
      const page=await apiPage("/api/portal/quotes","portal-quotes");
      currentQuotePage=page;
      quotes=page.items;
      renderQuoteList(page);
    }else if(id==="shop"){
      const page=await apiPage("/api/portal/shop-inquiries","portal-shop");
      renderPage($("shopInquiryTable"),"portal-shop",page,table(page.items,[["접수번호","receipt_number"],["제품","items"],["문의내용","message"],["상태","status",value=>statusBadge(value)],["관리자 안내","admin_note"],["접수일","created_at",fmt]]),()=>loadView("shop"));
    }else if(id==="projects"){
      const page=await apiPage("/api/portal/projects","portal-projects");
      const html=page.items.length?page.items.map(item=>`<article class="data-card">${statusBadge(item.status)}<h3>${esc(item.name)}</h3><p>${esc(item.start_date)} ~ ${esc(item.end_date)}</p><div class="progress"><i style="width:${+item.progress||0}%"></i></div><p>진행률 ${+item.progress||0}%</p></article>`).join(""):empty("등록된 프로젝트가 없습니다.");
      renderPage($("projectCards"),"portal-projects",page,html,()=>loadView("projects"));
    }else if(id==="contracts"){
      const page=await apiPage("/api/portal/contracts","portal-contracts");
      renderPage($("contractTable"),"portal-contracts",page,table(page.items,[["계약번호","contract_number"],["계약명","title"],["금액","amount",value=>Number(value||0).toLocaleString()+"원"],["상태","status"],["계약일","contract_date"]]),()=>loadView("contracts"));
    }else if(id==="deliveries"){
      const page=await apiPage("/api/portal/deliveries","portal-deliveries");
      renderPage($("deliveryTable"),"portal-deliveries",page,table(page.items,[["품목","item_name"],["수량","quantity"],["예정일","expected_date"],["납품일","delivered_date"],["상태","status"]]),()=>loadView("deliveries"));
    }else if(id==="invoices"){
      const page=await apiPage("/api/portal/tax-invoices","portal-invoices");
      renderPage($("invoiceTable"),"portal-invoices",page,table(page.items,[["발행번호","issue_number"],["금액","amount",value=>Number(value||0).toLocaleString()+"원"],["발행일","issued_date"],["상태","status"]]),()=>loadView("invoices"));
    }else if(id==="service"){
      const page=await apiPage("/api/portal/service-requests","portal-service");
      renderPage($("serviceTable"),"portal-service",page,table(page.items,[["제목","title"],["상태","status"],["접수일","created_at",fmt],["수정일","updated_at",fmt]]),()=>loadView("service"));
    }else if(id==="notices"){
      const page=await apiPage("/api/public/notices","portal-notices");
      const html=page.items.length?page.items.map(item=>`<article class="notice"><h3>${item.pinned?"📌 ":""}${esc(item.title)}</h3><small>${fmt(item.created_at)}</small><p>${esc(item.content)}</p></article>`).join(""):empty("공지사항이 없습니다.");
      renderPage($("noticeList"),"portal-notices",page,html,()=>loadView("notices"));
    }else if(id==="support"){
      const page=await apiPage("/api/portal/support","portal-support");
      renderPage($("supportList"),"portal-support",page,table(page.items,[["제목","subject"],["상태","status"],["문의일","created_at",fmt],["답변","answer"]]),()=>loadView("support"));
    }
  }catch(error){notify(error.message)}
}

function renderQuoteList(page){
  const search=$("quoteSearch").value.trim().toLowerCase();
  const status=$("quoteStatusFilter").value;
  const filtered=quotes.filter(item=>{
    const haystack=[item.receipt_number,item.subject,item.site_name,item.product_type].join(" ").toLowerCase();
    return (!search||haystack.includes(search))&&(!status||item.status===status);
  });
  $("quoteResultCount").textContent=`현재 페이지 ${filtered.length}건`;
  renderPage($("quoteTable"),"portal-quotes",page,filtered.length?quoteRows(filtered):empty("검색 조건에 맞는 견적이 없습니다."),()=>loadView("quotes"));
}
$("quoteSearch").addEventListener("input",()=>renderQuoteList(currentQuotePage));
$("quoteStatusFilter").addEventListener("change",()=>renderQuoteList(currentQuotePage));

function quoteStage(status){
  const level={RECEIVED:1,REVIEWING:2,SUPPLEMENT_REQUIRED:2,SUPPLEMENTED:2,QUOTED:3,APPROVED:3,COMPLETED:4}[status]||0;
  return `<ol class="quote-stage" aria-label="견적 진행 단계">${[[1,"접수"],[2,"검토"],[3,"견적 완료"],[4,"완료"]].map(([step,label])=>`<li class="${level>=step?"done":""}"><i>${level>=step?"✓":step}</i><span>${label}</span></li>`).join("")}</ol>`;
}
function renderDocuments(receipt,documents){
  if(!documents.length)return empty("담당자가 문서를 등록하면 이곳에서 다운로드할 수 있습니다.");
  return `<div class="document-list">${documents.map(document=>{
    const type=documentTypeNames[document.document_type]||document.document_type;
    const fileSize=document.file_size?`${(document.file_size/1048576).toFixed(2)}MB`:"파일";
    const approval=document.approval_status==="APPROVED"?"승인 완료":"승인 대기";
    return `<article class="document-card"><span class="document-mark">${document.document_type==="ESTIMATE"?"PDF":"DOC"}</span><div class="document-copy"><strong>${esc(document.title)}</strong><small>${esc(type)} · ${fileSize} · ${esc(approval)} · ${fmt(document.created_at)}</small></div><div class="document-actions"><a class="action download-action" href="/api/portal/quotes/${encodeURIComponent(receipt)}/documents/${document.id}" download>다운로드</a>${document.document_type!=="CONTRACT"&&document.approval_status!=="APPROVED"?`<button class="action detail-action" type="button" onclick="approveDoc('${esc(receipt)}',${document.id})">전자승인</button>`:""}${document.document_type==="CONTRACT"&&document.contract_decision==="PENDING"?`<button class="action detail-action" type="button" onclick="decideContract('${esc(receipt)}',${document.id},'ACCEPTED')">계약 수락</button><button class="action danger" type="button" onclick="decideContract('${esc(receipt)}',${document.id},'REJECTED')">거절</button>`:""}</div></article>`;
  }).join("")}</div>`;
}

async function openQuote(receipt){
  const dialog=$("detailDialog");
  $("quoteDetail").innerHTML='<p class="loading-state">견적 상세 정보를 불러오는 중입니다.</p>';
  if(!dialog.open)dialog.showModal();
  try{
    const quote=await api(`/api/portal/quotes/${encodeURIComponent(receipt)}`);
    const pending=quote.supplementalRequests.filter(item=>item.status==="REQUESTED");
    $("quoteDetail").innerHTML=`
      <div class="quote-detail-header"><div><small>${esc(quote.receipt_number)}</small><h2>${esc(quote.subject)}</h2></div><button type="button" aria-label="상세 닫기" onclick="detailDialog.close()">×</button></div>
      <div class="quote-detail-body">
        <div class="detail-status-band"><div><small>CURRENT STATUS</small><strong>${statusName[quote.status]||esc(quote.status)}</strong></div><p>최종 업데이트 ${fmt(quote.updated_at)} · 담당자 ${esc(quote.assigned_to||"배정 중")}</p></div>
        ${quoteStage(quote.status)}
        <section class="detail-section"><div class="detail-section-title"><h3>견적 요청 정보</h3><small>접수일 ${fmtDate(quote.created_at)}</small></div><div class="detail-grid"><div><span>현장명</span>${esc(quote.site_name||"-")}</div><div><span>제품/공종</span>${esc(quote.product_type)}</div><div><span>희망 납기</span>${fmtDate(quote.desired_date)}</div><div><span>담당자</span>${esc(quote.assigned_to||"배정 중")}</div><div class="detail-copy"><span>요청사항</span>${esc(quote.details)}</div></div></section>
        ${quote.estimate_amount!=null?`<section class="detail-section"><div class="estimate-summary"><b>견적금액 ${Number(quote.estimate_amount).toLocaleString()}원</b><br>${esc(quote.estimate_notes||"")}</div></section>`:""}
        <section class="detail-section"><div class="detail-section-title"><h3>견적서·계약서</h3><small>관리자 등록 문서</small></div>${renderDocuments(receipt,quote.documents)}</section>
        <section class="detail-section"><div class="detail-section-title"><h3>진행 이력</h3><small>관리자 상태 변경 실시간 반영</small></div><ul class="timeline">${quote.history.map(item=>`<li><b>${statusName[item.status]||esc(item.status)}</b> ${esc(item.note||"")}<small>${fmt(item.created_at)}</small></li>`).join("")}</ul></section>
        ${pending.length?`<section class="detail-section"><div class="detail-section-title"><h3>보완 요청</h3></div>${pending.map(item=>`<p class="supplement-alert">${esc(item.request_text)}</p>`).join("")}</section>`:""}
        <section class="detail-section"><div class="detail-section-title"><h3>첨부파일</h3><small>접수 및 추가 제출 자료</small></div>${quote.attachments.length?quote.attachments.map(file=>`<div class="file-row"><span>${esc(file.original_name)} (${(file.file_size/1048576).toFixed(2)}MB)</span><a class="action download-action" href="/api/portal/quotes/${encodeURIComponent(receipt)}/files/${file.id}" download>다운로드</a></div>`).join(""):empty("첨부파일이 없습니다.")}</section>
        <section class="detail-section"><div class="detail-section-title"><h3>추가자료 업로드</h3></div><form id="suppForm" class="upload-box"><label>PDF·이미지·CAD·엑셀 자료<input type="file" name="files" multiple required accept=".pdf,.jpg,.jpeg,.png,.dwg,.dxf,.xls,.xlsx,.zip"></label><div class="compact-submit-row">${compactSubmit("추가자료 업로드","upload")}</div></form></section>
        <section class="detail-section"><div class="detail-section-title"><h3>웹하드 공유</h3></div>${quote.webhard_url?`<p><a class="action download-action" href="${esc(quote.webhard_url)}" target="_blank" rel="noopener">담당자 공유 자료 열기 ↗</a></p>`:""}<form id="customerWebhardForm" class="form-inline"><input name="url" type="url" value="${esc(quote.customer_webhard_url||"")}" placeholder="고객 웹하드 공유 주소 https://..."><button class="action">주소 저장</button></form></section>
        <section class="detail-section"><div class="detail-section-title"><h3>담당자 메시지</h3></div><div id="messageList"></div><form id="messageForm" class="form-inline"><input name="message" placeholder="문의 또는 회신 내용을 입력하세요" required><button class="action">전송</button></form></section>
      </div>`;
    loadMessages(receipt);
    $("suppForm").onsubmit=async event=>{event.preventDefault();const button=$("suppForm").querySelector("button");button.disabled=true;try{notify((await api(`/api/portal/quotes/${encodeURIComponent(receipt)}/files`,{method:"POST",body:new FormData($("suppForm"))})).message);openQuote(receipt)}catch(error){notify(error.message)}finally{button.disabled=false}};
    $("customerWebhardForm").onsubmit=async event=>{event.preventDefault();try{notify((await api(`/api/portal/quotes/${encodeURIComponent(receipt)}/webhard`,{method:"PUT",body:JSON.stringify({url:event.currentTarget.url.value})})).message);openQuote(receipt)}catch(error){notify(error.message)}};
    $("messageForm").onsubmit=async event=>{event.preventDefault();try{await api(`/api/portal/quotes/${encodeURIComponent(receipt)}/messages`,{method:"POST",body:JSON.stringify({message:event.currentTarget.message.value})});event.currentTarget.reset();loadMessages(receipt)}catch(error){notify(error.message)}};
  }catch(error){dialog.close();notify(error.message)}
}

async function loadMessages(receipt){
  try{const messages=await api(`/api/portal/quotes/${encodeURIComponent(receipt)}/messages`);$("messageList").innerHTML=messages.length?messages.map(item=>`<div class="message-row ${item.sender_role==="ADMIN"?"admin":""}"><small>${item.sender_role==="ADMIN"?"(주)금성이엔씨":esc(item.sender_email)} · ${fmt(item.created_at)}</small><p>${esc(item.message)}</p></div>`).join(""):empty("메시지가 없습니다.")}catch(error){notify(error.message)}
}
async function approveDoc(receipt,id){if(!confirm("이 문서를 전자승인하시겠습니까?"))return;try{notify((await api(`/api/portal/quotes/${encodeURIComponent(receipt)}/documents/${id}/approve`,{method:"POST"})).message);openQuote(receipt)}catch(error){notify(error.message)}}
async function decideContract(receipt,id,decision){const accepted=decision==="ACCEPTED";const note=accepted?prompt("계약 수락 메모(선택)","계약 내용을 확인하고 수락합니다."):prompt("계약 거절 사유를 입력하세요.");if(note===null||(!accepted&&!note.trim()))return;if(!confirm(`계약서를 ${accepted?"수락":"거절"}하시겠습니까? 이 기록은 변경할 수 없습니다.`))return;try{notify((await api(`/api/portal/quotes/${encodeURIComponent(receipt)}/documents/${id}/contract-decision`,{method:"POST",body:JSON.stringify({decision,note})})).message);openQuote(receipt)}catch(error){notify(error.message)}}
async function exportPrivacy(){try{const data=await api("/api/portal/privacy/export");const url=URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:"application/json"}));const anchor=document.createElement("a");anchor.href=url;anchor.download="kumsung-my-data.json";anchor.click();URL.revokeObjectURL(url)}catch(error){notify(error.message)}}
async function deleteAccount(){const password=prompt("계정 삭제를 위해 현재 비밀번호를 입력하세요.");if(!password)return;if(!confirm("계정과 개인정보를 삭제합니다. 계속하시겠습니까?"))return;try{await api("/api/portal/privacy/account",{method:"DELETE",body:JSON.stringify({password})});location.href="/"}catch(error){notify(error.message)}}
async function openServiceDialog(){try{const projects=await api("/api/portal/projects?limit=200");$("serviceProject").replaceChildren(new Option("프로젝트 미지정",""));projects.forEach(project=>$("serviceProject").add(new Option(project.name,String(project.id))));$("serviceDialog").showModal()}catch(error){notify(error.message)}}

$("serviceForm").onsubmit=async event=>{event.preventDefault();const data=Object.fromEntries(new FormData(event.currentTarget));data.projectId=data.projectId?Number(data.projectId):null;try{await api("/api/portal/service-requests",{method:"POST",body:JSON.stringify(data)});$("serviceDialog").close();event.currentTarget.reset();resetPage("portal-service");loadView("service");notify("A/S 요청이 접수되었습니다.")}catch(error){notify(error.message)}};
$("supportForm").onsubmit=async event=>{event.preventDefault();try{await api("/api/portal/support",{method:"POST",body:JSON.stringify(Object.fromEntries(new FormData(event.currentTarget)))});$("supportDialog").close();event.currentTarget.reset();loadView("support");notify("문의가 접수되었습니다.")}catch(error){notify(error.message)}};
$("detailDialog").addEventListener("click",event=>{if(event.target===$("detailDialog"))$("detailDialog").close()});
document.addEventListener("keydown",event=>{if(event.key==="Escape")togglePortalMenu(false)});
init().catch(error=>notify(error.message));
