(function(global){
  "use strict";
  function parse(text,fields){
    const byLabel=new Map();
    fields.forEach((field)=>field.labels.forEach((label)=>byLabel.set(label.replace(/\s+/g,""),field)));
    const values={};let current=null;
    String(text||"").replace(/\r/g,"").split("\n").forEach((raw)=>{
      const match=raw.match(/^\s*([^:：]+)\s*[:：]\s*(.*)$/);
      if(match){
        const field=byLabel.get(match[1].replace(/\s+/g,""));
        if(field){current=field.key;values[current]=match[2].trim();return;}
      }
      if(current&&raw.trim())values[current]=`${values[current]?values[current]+"\n":""}${raw.trim()}`;
    });
    for(const field of fields){
      const value=(values[field.key]||"").trim();
      if(field.required!==false&&!value)throw new Error(`${field.name} 항목을 입력해 주세요.`);
      if(field.max&&value.length>field.max)throw new Error(`${field.name} 항목은 ${field.max}자 이하로 입력해 주세요.`);
      values[field.key]=value;
    }
    return values;
  }
  global.KumsungMemo={parse};
})(window);
