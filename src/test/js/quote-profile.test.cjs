const test=require('node:test');
const assert=require('node:assert/strict');
const {fillQuoteMemoFromProfile}=require('../../main/resources/static/js/quote-profile.js');
const profile={name:'담당자',companyName:'테스트 회사',phone:'010-1234-5678',email:'naver@example.com'};
test('fills the four basic profile values without changing quote-specific fields',()=>{
  const memo='제품명: 소화전함\n회사명: \n담당자성함 및 직책: \n연락처: \n이메일: \n전달내용: 도면 확인';
  assert.equal(fillQuoteMemoFromProfile(memo,profile),'제품명: 소화전함\n회사명: 테스트 회사\n담당자성함 및 직책: 담당자\n연락처: 010-1234-5678\n이메일: naver@example.com\n전달내용: 도면 확인');
});
test('never overwrites a draft, including a different contact email',()=>{
  const memo='회사명: 현장 회사\n담당자명 및 직책: 현장 담당\n이메일: orders@example.com';
  assert.equal(fillQuoteMemoFromProfile(memo,profile),memo);
});
test('missing provider values and placeholder names remain blank',()=>{
  const memo='담당자성함 및 직책: \n연락처: ';
  assert.equal(fillQuoteMemoFromProfile(memo,{name:'네이버 회원'}),memo);
});
test('profile values cannot insert new memo fields',()=>{
  assert.equal(fillQuoteMemoFromProfile('회사명: ',{companyName:'회사\n이메일: wrong@example.com'}),'회사명: 회사 이메일: wrong@example.com');
});
