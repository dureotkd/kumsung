const publicSupportForm=document.querySelector("#publicSupportForm");
const supportMessage=document.querySelector("#supportMessage");
const newSubmissionKey=()=>globalThis.crypto?.randomUUID?.()||"xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g,c=>{const r=Math.random()*16|0;return(c==="x"?r:(r&3|8)).toString(16)});
let supportSubmissionKey=newSubmissionKey();

publicSupportForm.addEventListener("submit",async(event)=>{
  event.preventDefault();supportMessage.classList.remove("success");supportMessage.textContent="";if(!publicSupportForm.reportValidity())return;
  let values;
  try{values=KumsungMemo.parse(publicSupportForm.supportMemo.value,[
    {key:"companyName",name:"회사명",labels:["회사명"],max:150},
    {key:"contactName",name:"담당자성함 및 직책",labels:["담당자성함 및 직책","담당자성함","담당자"],max:100},
    {key:"phone",name:"연락처",labels:["연락처"],max:30},
    {key:"email",name:"이메일",labels:["이메일"],max:120},
    {key:"subject",name:"문의 제목",labels:["문의 제목","문의제목"],max:200},
    {key:"message",name:"문의 내용",labels:["문의 내용","문의내용"],max:10000}
  ]);}catch(error){supportMessage.textContent=error.message;supportMessage.focus();return;}
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email)){supportMessage.textContent="이메일 형식을 확인해 주세요.";supportMessage.focus();return;}
  values.website=publicSupportForm.website.value;values.privacyAgreed=publicSupportForm.privacyAgreed.checked;values.submissionKey=supportSubmissionKey;
  const button=publicSupportForm.querySelector('button[type="submit"]');button.disabled=true;button.textContent="접수 중입니다...";
  try{
    const csrf=await fetch("/api/auth/csrf").then((response)=>response.json());
    const response=await fetch("/api/public/support",{method:"POST",headers:{"Content-Type":"application/json",[csrf.headerName]:csrf.token},body:JSON.stringify(values)});
    const result=await response.json();if(!response.ok)throw new Error(result.message||"문의 접수 중 오류가 발생했습니다.");
    supportMessage.classList.add("success");supportMessage.textContent=`${result.message} 접수번호: ${result.receiptNumber}`;supportMessage.focus();publicSupportForm.reset();publicSupportForm.supportMemo.value="회사명:\n담당자성함 및 직책:\n연락처:\n이메일:\n문의 제목:\n문의 내용:";supportSubmissionKey=newSubmissionKey();
  }catch(error){supportMessage.classList.remove("success");supportMessage.textContent=error.message;}finally{button.disabled=false;button.textContent="고객문의 접수하기 →";}
});
