// Only empty template values are filled; a customer's draft is never overwritten.
function fillQuoteMemoFromProfile(memo,profile){
  const fields={"회사명":profile.companyName,"담당자성함및직책":profile.name,
    "담당자명및직책":profile.name,"연락처":profile.phone,"이메일":profile.email};
  return memo.split("\n").map(line=>{
    const separator=line.search(/[:：]/);
    if(separator<0||line.slice(separator+1).trim())return line;
    const value=fields[line.slice(0,separator).replace(/\s/g,"")];
    if(!value||value==="네이버 회원")return line;
    return `${line.slice(0,separator+1)} ${String(value).replace(/[\r\n]+/g," ").trim()}`;
  }).join("\n");
}
if(typeof module!=="undefined")module.exports={fillQuoteMemoFromProfile};
