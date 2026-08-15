const innovationSections=document.querySelector("#innovationSections");
const downloadDialog=document.querySelector("#downloadDialog");
const downloadForm=document.querySelector("#downloadForm");
const innovationCategories=[
  {key:"RND",slug:"rnd",number:"01",name:"연구전담부서",description:"회사의 연구개발 방향과 성과를 소개합니다."},
  {key:"PATENT_CERT",slug:"patent-cert",number:"02",name:"특허·인증",description:"제품별 보유 인증서와 특허 자료를 확인합니다."},
  {key:"TECHNICAL",slug:"technical",number:"03",name:"기술자료",description:"제품 시공·설계에 필요한 실무 자료입니다."},
  {key:"KNOWLEDGE",slug:"knowledge",number:"04",name:"Knowledge Library",description:"기술 지식과 교육 콘텐츠를 모았습니다."},
  {key:"SMART_FACTORY",slug:"smart-factory",number:"05",name:"스마트공장",description:"생산 자동화 설비와 관련 자료를 소개합니다."}
];
let innovationRows=new Map(),selectedInnovationId=null;
const innovationEsc=value=>String(value??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const innovationDate=value=>value?new Date(value).toLocaleDateString("ko-KR"):"";

async function loadInnovationResources(){
  const response=await fetch("/api/public/content/innovation");
  if(!response.ok)throw new Error("기술자료를 불러오지 못했습니다.");
  const rows=await response.json();innovationRows=new Map(rows.map(row=>[Number(row.id),row]));
  innovationSections.innerHTML=innovationCategories.map(category=>{
    const items=rows.filter(row=>(row.category||"TECHNICAL")===category.key);
    return `<section id="innovation-${category.slug}" class="sync-section"><div class="sync-section-head"><div class="sync-section-title"><span class="sync-cat-num">${category.number}</span><h2>${category.name}</h2></div></div><p class="sync-section-desc">${category.description}</p><div class="sync-item-list">${items.length?items.map(row=>`<button class="sync-resource" type="button" data-download-id="${row.id}"><span class="sync-thumb"><img src="${innovationEsc(row.imageUrl)}" alt="" loading="lazy"></span><span class="sync-resource-copy"><strong>${innovationEsc(row.title)}</strong><small>${innovationDate(row.created_at)} · ${innovationEsc(row.file_original_name)}</small></span><span class="go">↓</span></button>`).join(""):'<p class="sync-empty">등록된 자료가 없습니다.</p>'}</div></section>`
  }).join("");
}

innovationSections.addEventListener("click",event=>{
  const button=event.target.closest("[data-download-id]");if(!button)return;
  const row=innovationRows.get(Number(button.dataset.downloadId));if(!row)return;
  selectedInnovationId=Number(row.id);
  document.querySelector("#downloadImage").src=row.imageUrl;document.querySelector("#downloadImage").alt=row.title;
  document.querySelector("#downloadTitle").textContent=row.title;
  document.querySelector("#downloadDescription").textContent=row.description||`${row.file_original_name} 파일을 다운로드합니다.`;
  downloadForm.reset();document.querySelector("#downloadMessage").textContent="";downloadDialog.showModal();
});
downloadDialog.querySelector(".sync-dialog-close").onclick=()=>downloadDialog.close();
downloadForm.onsubmit=async event=>{
  event.preventDefault();const button=downloadForm.querySelector('button[type="submit"]');const message=document.querySelector("#downloadMessage");button.disabled=true;message.textContent="비밀번호를 확인하고 있습니다.";
  try{
    const csrf=await fetch("/api/auth/csrf").then(r=>r.json());
    const response=await fetch(`/api/public/content/innovation/${selectedInnovationId}/download`,{method:"POST",headers:{"Content-Type":"application/json",[csrf.headerName]:csrf.token},body:JSON.stringify({password:downloadForm.password.value})});
    if(!response.ok){const error=await response.json().catch(()=>({}));throw new Error(error.message||"다운로드할 수 없습니다.")}
    const blob=await response.blob();const disposition=response.headers.get("content-disposition")||"";const encoded=disposition.match(/filename\*=UTF-8''([^;]+)/i);const plain=disposition.match(/filename="?([^";]+)"?/i);const filename=encoded?decodeURIComponent(encoded[1]):plain?plain[1]:"technical-resource";
    const url=URL.createObjectURL(blob);const link=document.createElement("a");link.href=url;link.download=filename;document.body.append(link);link.click();link.remove();URL.revokeObjectURL(url);message.textContent="다운로드를 시작했습니다.";setTimeout(()=>downloadDialog.close(),700);
  }catch(error){message.textContent=error.message}finally{button.disabled=false}
};
loadInnovationResources().catch(error=>innovationSections.innerHTML=`<p class="sync-empty">${innovationEsc(error.message)}</p>`);
