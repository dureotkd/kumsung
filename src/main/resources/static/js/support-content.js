const constructionCaseGrid=document.querySelector("#constructionCaseGrid");
const companyNewsList=document.querySelector("#companyNewsList");
const contentDetailDialog=document.querySelector("#contentDetailDialog");
let supportContentRows=new Map();
const contentEsc=value=>String(value??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const contentDate=value=>value?new Date(value).toLocaleDateString("ko-KR"):"";

async function fetchContent(type){const response=await fetch(`/api/public/content/posts?type=${encodeURIComponent(type)}`);if(!response.ok)throw new Error("콘텐츠를 불러오지 못했습니다.");return response.json()}
async function loadSupportContent(){
  const [news,cases]=await Promise.all([fetchContent("COMPANY_NEWS"),fetchContent("CONSTRUCTION_CASE")]);
  supportContentRows=new Map([...news,...cases].map(row=>[Number(row.id),row]));
  companyNewsList.innerHTML=news.length?news.map(row=>`<button class="sync-news-item" type="button" data-content-id="${row.id}"><img class="sync-news-thumb" src="${contentEsc(row.imageUrl)}" alt="" loading="lazy"><span class="sync-news-tag">NEWS</span><strong class="sync-news-title">${contentEsc(row.title)}</strong><time class="sync-news-date">${contentDate(row.created_at)}</time></button>`).join(""):'<p class="sync-empty">등록된 회사소식이 없습니다.</p>';
  constructionCaseGrid.innerHTML=cases.length?cases.map(row=>`<button class="sync-case-card" type="button" data-content-id="${row.id}"><img src="${contentEsc(row.imageUrl)}" alt="${contentEsc(row.title)}" loading="lazy"><strong>${contentEsc(row.title)}</strong></button>`).join(""):'<p class="sync-empty">등록된 시공사례가 없습니다.</p>';
}
function openContentDetail(id){
  const row=supportContentRows.get(Number(id));if(!row)return;
  document.querySelector("#contentDetailImage").src=row.imageUrl;document.querySelector("#contentDetailImage").alt=row.title;
  document.querySelector("#contentDetailType").textContent=row.post_type==="COMPANY_NEWS"?"회사소식":"시공사례";document.querySelector("#contentDetailTitle").textContent=row.title;
  document.querySelector("#contentDetailText").textContent=row.content||"";document.querySelector("#contentDetailDate").textContent=contentDate(row.created_at);contentDetailDialog.showModal();
}
constructionCaseGrid.addEventListener("click",event=>{const button=event.target.closest("[data-content-id]");if(button)openContentDetail(button.dataset.contentId)});
companyNewsList.addEventListener("click",event=>{const button=event.target.closest("[data-content-id]");if(button)openContentDetail(button.dataset.contentId)});
contentDetailDialog.querySelector(".sync-dialog-close").onclick=()=>contentDetailDialog.close();
loadSupportContent().then(()=>{const requested=new URLSearchParams(location.search).get("post");if(requested)openContentDetail(requested)}).catch(error=>{const message=`<p class="sync-empty">${contentEsc(error.message)}</p>`;companyNewsList.innerHTML=message;constructionCaseGrid.innerHTML=message});
