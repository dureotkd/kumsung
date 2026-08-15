const form = document.querySelector("#quoteForm");
const files = document.querySelector("#files");
const list = document.querySelector("#fileList");
const drop = document.querySelector("#dropzone");
const message = document.querySelector("#message");
const dialog = document.querySelector("#success");
const quoteMemo = document.querySelector("#quoteMemo");
const productHint = document.querySelector("#productHint");

let selected = [];

const quoteFields = {
  "제품명": { key: "productType", label: "제품명", max: 80 },
  "회사명": { key: "companyName", label: "회사명", max: 150 },
  "담당자성함및직책": { key: "contactName", label: "담당자성함 및 직책", max: 60 },
  "담당자명및직책": { key: "contactName", label: "담당자성함 및 직책", max: 60 },
  "연락처": { key: "phone", label: "연락처", max: 30 },
  "이메일": { key: "email", label: "이메일", max: 120 },
  "전달내용": { key: "deliveryMessage", label: "전달내용", max: 1000 }
};

function normalizedLabel(value) {
  return value.replace(/\s/g, "");
}

function parseMemo(raw, definitions, sectionName) {
  const values = {};

  for (const line of raw.replace(/\r/g, "").split("\n")) {
    const separator = line.search(/[:：]/);
    if (separator < 0) continue;

    const definition = definitions[normalizedLabel(line.slice(0, separator))];
    if (!definition) continue;

    const value = line.slice(separator + 1).trim();
    if (Object.hasOwn(values, definition.key)) {
      throw new Error(`${sectionName}의 '${definition.label}' 항목은 한 번만 작성해 주세요.`);
    }
    values[definition.key] = value;
  }

  const uniqueDefinitions = [...new Map(
    Object.values(definitions).map((definition) => [definition.key, definition])
  ).values()];

  for (const definition of uniqueDefinitions) {
    const value = values[definition.key] ?? "";
    if (!value) throw new Error(`${sectionName}의 '${definition.label}' 뒤에 내용을 입력해 주세요.`);
    if (value.length > definition.max) {
      throw new Error(`${sectionName}의 '${definition.label}'은(는) ${definition.max}자 이내로 입력해 주세요.`);
    }
  }

  return values;
}

function validateContact(phone, email, sectionName) {
  if (!/^[0-9+()\-\s]+$/.test(phone) || phone.replace(/\D/g, "").length < 7) {
    throw new Error(`${sectionName}의 연락처는 숫자와 하이픈(-)을 사용해 입력해 주세요.`);
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw new Error(`${sectionName}의 이메일 형식을 확인해 주세요. 예: name@company.com`);
  }
}

function productValue() {
  const productLine = quoteMemo.value.replace(/\r/g, "").split("\n")
    .find((line) => /^\s*제품\s*명\s*[:：]/.test(line));
  if (!productLine) return "";
  return productLine.slice(productLine.search(/[:：]/) + 1).trim();
}

function updateProductHint() {
  productHint.hidden = Boolean(productValue());
}

function buildQuoteRequest() {
  const quote = parseMemo(quoteMemo.value, quoteFields, "견적의뢰정보");

  validateContact(quote.phone, quote.email, "견적의뢰정보");

  return {
    companyName: quote.companyName,
    businessNumber: null,
    contactName: quote.contactName,
    email: quote.email,
    phone: quote.phone,
    siteName: null,
    siteAddress: null,
    productType: quote.productType,
    subject: `${quote.productType} 견적 의뢰`,
    details: `[견적의뢰정보]\n${quoteMemo.value.trim()}`,
    webhardUrl: null,
    desiredDate: null,
    privacyAgreed: form.privacyAgreed.checked
  };
}

function render() {
  list.replaceChildren();
  selected.forEach((file, index) => {
    const item = document.createElement("li");
    const name = document.createElement("span");
    const meta = document.createElement("span");
    const button = document.createElement("button");
    name.textContent = file.name;
    meta.append(`${(file.size / 1024 / 1024).toFixed(2)} MB · `);
    button.type = "button";
    button.dataset.i = String(index);
    button.textContent = "삭제";
    meta.append(button);
    item.append(name, meta);
    list.append(item);
  });
}

function add(incoming) {
  for (const file of incoming) {
    if (file.size > 50 * 1024 * 1024) {
      message.textContent = `${file.name}: 파일당 50MB를 초과했습니다.`;
      continue;
    }
    if (selected.length >= 20) {
      message.textContent = "한 번에 최대 20개 파일까지 첨부할 수 있습니다.";
      break;
    }
    if (selected.reduce((sum, item) => sum + item.size, 0) + file.size > 200 * 1024 * 1024) {
      message.textContent = "첨부 파일 전체 용량은 200MB까지 가능합니다.";
      continue;
    }
    if (!selected.some((item) => item.name === file.name && item.size === file.size)) selected.push(file);
  }
  render();
}

quoteMemo.addEventListener("input", updateProductHint);
files.addEventListener("change", () => add(files.files));
list.addEventListener("click", (event) => {
  if (event.target.dataset.i !== undefined) {
    selected.splice(Number(event.target.dataset.i), 1);
    render();
  }
});
["dragenter", "dragover"].forEach((name) => drop.addEventListener(name, (event) => {
  event.preventDefault();
  drop.classList.add("drag");
}));
["dragleave", "drop"].forEach((name) => drop.addEventListener(name, (event) => {
  event.preventDefault();
  drop.classList.remove("drag");
}));
drop.addEventListener("drop", (event) => add(event.dataTransfer.files));

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  message.textContent = "";
  if (!form.reportValidity()) return;

  let values;
  try {
    values = buildQuoteRequest();
  } catch (error) {
    message.textContent = error.message;
    return;
  }

  if (!selected.length) {
    message.textContent = "도면 또는 견적 자료를 1개 이상 첨부해 주세요.";
    return;
  }

  const button = form.querySelector(".submit");
  button.disabled = true;
  button.firstChild.textContent = "접수 중입니다... ";
  const data = new FormData();
  data.append("request", new Blob([JSON.stringify(values)], { type: "application/json" }));
  selected.forEach((file) => data.append("files", file));

  try {
    const csrf = await fetch("/api/auth/csrf").then((response) => response.json());
    const response = await fetch("/api/quotes", {
      method: "POST",
      headers: { [csrf.headerName]: csrf.token },
      body: data
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.message || "접수 처리 중 오류가 발생했습니다.");
    document.querySelector("#receipt").textContent = result.receiptNumber;
    dialog.showModal();
    form.reset();
    selected = [];
    render();
    updateProductHint();
  } catch (error) {
    message.textContent = error.message;
  } finally {
    button.disabled = false;
    button.firstChild.textContent = "견적 요청 제출하기 ";
  }
});

dialog.querySelector("button").addEventListener("click", () => dialog.close());
updateProductHint();
