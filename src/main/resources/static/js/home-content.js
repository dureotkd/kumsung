const homeCompanyNews=document.querySelector("#homeCompanyNews");
const homeEsc=value=>String(value??"").replace(/[&<>"']/g,character=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[character]));
const homeDate=value=>value?new Date(value).toLocaleDateString("ko-KR",{year:"2-digit",month:"2-digit",day:"2-digit"}):"";

fetch("/api/public/content/posts?type=COMPANY_NEWS")
  .then(response=>{if(!response.ok)throw new Error("회사소식을 불러오지 못했습니다.");return response.json()})
  .then(rows=>{
    const visible=rows.slice(0,4);
    homeCompanyNews.innerHTML=visible.length?`<ul>${visible.map(row=>`<li><a href="/support.html?post=${encodeURIComponent(row.id)}#company-news">${homeEsc(row.title)}</a><time>${homeDate(row.created_at)}</time></li>`).join("")}</ul>`:'<p class="home-content-state">등록된 회사소식이 없습니다.</p>';
  })
  .catch(error=>{homeCompanyNews.innerHTML=`<p class="home-content-state">${homeEsc(error.message)}</p>`});
