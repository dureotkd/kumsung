let csrfCache;
async function csrf(){if(!csrfCache)csrfCache=await fetch("/api/auth/csrf").then(r=>r.json());return csrfCache}
async function api(url,options={}){
  options.headers=options.headers||{};
  if(options.body&&!((options.body) instanceof FormData))options.headers["Content-Type"]="application/json";
  if(options.method&&options.method!=="GET"){const c=await csrf();options.headers[c.headerName]=c.token}
  const r=await fetch(url,options);
  if(r.status===401){
    const next=`${location.pathname}${location.search}${location.hash}`;
    location.href=location.pathname==="/login.html"?"/login.html":`/login.html?next=${encodeURIComponent(next)}`;
    throw new Error("로그인이 필요합니다.")
  }
  const type=r.headers.get("content-type")||"";
  const data=type.includes("json")?await r.json():await r.text();
  if(!r.ok)throw new Error(data.message||(r.status===403?"이 작업을 수행할 권한이 없습니다.":"처리 중 오류가 발생했습니다."));
  return data;
}
const statusName={RECEIVED:"접수",REVIEWING:"검토 중",SUPPLEMENT_REQUIRED:"보완 요청",SUPPLEMENTED:"보완 완료",QUOTED:"견적 완료",APPROVED:"승인",COMPLETED:"완료",CANCELLED:"취소",PLANNING:"준비",IN_PROGRESS:"진행 중",PENDING:"대기"};
const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const fmt=v=>v?new Date(v).toLocaleString("ko-KR"):"-";
const PAGE_SIZE=25,pageOffsets=new Map();
function resetPage(key){pageOffsets.set(key,0)}
async function apiPage(url,key){
  const offset=pageOffsets.get(key)||0,separator=url.includes("?")?"&":"?";
  const rows=await api(`${url}${separator}limit=${PAGE_SIZE+1}&offset=${offset}`);
  return {items:rows.slice(0,PAGE_SIZE),hasNext:rows.length>PAGE_SIZE,offset};
}
function renderPage(host,key,page,content,reload){
  host.innerHTML=content;
  const nav=document.createElement("nav");nav.className="pager";nav.setAttribute("aria-label","목록 페이지");
  const previous=document.createElement("button");previous.type="button";previous.className="action";previous.textContent="이전";previous.disabled=page.offset===0;
  const label=document.createElement("span");label.textContent=`${Math.floor(page.offset/PAGE_SIZE)+1} 페이지`;
  const next=document.createElement("button");next.type="button";next.className="action";next.textContent="다음";next.disabled=!page.hasNext;
  previous.onclick=()=>{pageOffsets.set(key,Math.max(0,page.offset-PAGE_SIZE));reload()};
  next.onclick=()=>{pageOffsets.set(key,page.offset+PAGE_SIZE);reload()};
  nav.append(previous,label,next);host.append(nav);
}
async function logout(){try{await api("/logout",{method:"POST"})}finally{location.href="/"}}
