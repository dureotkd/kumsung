const constructionCaseGrid=document.querySelector("#constructionCaseGrid");
const companyNewsList=document.querySelector("#companyNewsList");
const snsChannelContent=document.querySelector("#snsChannelContent");
const newProgramContent=document.querySelector("#newProgramContent");
const otherInquiryContent=document.querySelector("#otherInquiryContent");
const contentDetailDialog=document.querySelector("#contentDetailDialog");
const contentTypeName={SNS_CHANNEL:"SNS채널",NEW_DEVELOPMENT_PROGRAM:"신개발 프로그램",COMPANY_NEWS:"회사소식",CONSTRUCTION_CASE:"시공사례",OTHER_INQUIRY:"그외 문의"};
let supportContentRows=new Map();
const contentEsc=value=>String(value??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const contentDate=value=>value?new Date(value).toLocaleDateString("ko-KR"):"";

async function fetchContent(type){const response=await fetch(`/api/public/content/posts?type=${encodeURIComponent(type)}`);if(!response.ok)throw new Error("콘텐츠를 불러오지 못했습니다.");return response.json()}
function managedCards(rows,emptyMessage){
  if(!rows.length)return `<p class="sync-empty">${contentEsc(emptyMessage)}</p>`;
  return rows.map(row=>`<article class="sync-managed-card">${row.imageUrl?`<img src="${contentEsc(row.imageUrl)}" alt="${contentEsc(row.title)}" loading="lazy">`:'<span class="sync-managed-placeholder" aria-hidden="true">K</span>'}<div class="sync-managed-copy"><span>${contentEsc(contentTypeName[row.post_type])}</span><strong>${contentEsc(row.title)}</strong>${row.content?`<p>${contentEsc(row.content)}</p>`:""}<div class="sync-managed-actions"><button type="button" data-content-id="${row.id}">내용 보기</button>${row.link_url?`<a href="${contentEsc(row.link_url)}" target="_blank" rel="noopener noreferrer">외부 링크 ↗</a>`:""}${row.fileUrl?`<a href="${contentEsc(row.fileUrl)}">파일 다운로드</a>`:""}</div></div></article>`).join("");
}
async function loadSupportContent(){
  const [social,programs,news,cases,other]=await Promise.all([fetchContent("SNS_CHANNEL"),fetchContent("NEW_DEVELOPMENT_PROGRAM"),fetchContent("COMPANY_NEWS"),fetchContent("CONSTRUCTION_CASE"),fetchContent("OTHER_INQUIRY")]);
  supportContentRows=new Map([...social,...programs,...news,...cases,...other].map(row=>[Number(row.id),row]));
  snsChannelContent.innerHTML=managedCards(social,"추가 등록된 SNS채널 자료가 없습니다.");
  newProgramContent.innerHTML=managedCards(programs,"추가 등록된 신개발 프로그램이 없습니다.");
  otherInquiryContent.innerHTML=managedCards(other,"등록된 그외 문의 안내자료가 없습니다.");
  companyNewsList.innerHTML=news.length?news.map(row=>`<button class="sync-news-item" type="button" data-content-id="${row.id}">${row.imageUrl?`<img class="sync-news-thumb" src="${contentEsc(row.imageUrl)}" alt="" loading="lazy">`:'<span class="sync-news-thumb sync-content-placeholder" aria-hidden="true">K</span>'}<span class="sync-news-tag">NEWS</span><strong class="sync-news-title">${contentEsc(row.title)}</strong><time class="sync-news-date">${contentDate(row.created_at)}</time></button>`).join(""):'<p class="sync-empty">등록된 회사소식이 없습니다.</p>';
  constructionCaseGrid.innerHTML=cases.length?cases.map(row=>`<button class="sync-case-card" type="button" data-content-id="${row.id}">${row.imageUrl?`<img src="${contentEsc(row.imageUrl)}" alt="${contentEsc(row.title)}" loading="lazy">`:'<span class="sync-case-placeholder sync-content-placeholder" aria-hidden="true">K</span>'}<strong>${contentEsc(row.title)}</strong></button>`).join(""):'<p class="sync-empty">등록된 시공사례가 없습니다.</p>';
}
function openContentDetail(id){
  const row=supportContentRows.get(Number(id));if(!row)return;
  const image=document.querySelector("#contentDetailImage");image.hidden=!row.imageUrl;image.src=row.imageUrl||"";image.alt=row.imageUrl?row.title:"";
  document.querySelector("#contentDetailType").textContent=contentTypeName[row.post_type]||"홍보센터";document.querySelector("#contentDetailTitle").textContent=row.title;
  document.querySelector("#contentDetailText").textContent=row.content||"";document.querySelector("#contentDetailDate").textContent=contentDate(row.created_at);
  const link=document.querySelector("#contentDetailLink");link.hidden=!row.link_url;link.href=row.link_url||"#";
  const file=document.querySelector("#contentDetailFile");file.hidden=!row.fileUrl;file.href=row.fileUrl||"#";file.textContent=row.file_original_name?`${row.file_original_name} 다운로드`:"첨부파일 다운로드";
  contentDetailDialog.showModal();
}
document.addEventListener("click",event=>{const button=event.target.closest("[data-content-id]");if(button)openContentDetail(button.dataset.contentId)});
contentDetailDialog.querySelector(".sync-dialog-close").onclick=()=>contentDetailDialog.close();
loadSupportContent().then(()=>{const requested=new URLSearchParams(location.search).get("post");if(requested)openContentDetail(requested)}).catch(error=>{const message=`<p class="sync-empty">${contentEsc(error.message)}</p>`;companyNewsList.innerHTML=message;constructionCaseGrid.innerHTML=message;snsChannelContent.innerHTML=message;newProgramContent.innerHTML=message;otherInquiryContent.innerHTML=message});
