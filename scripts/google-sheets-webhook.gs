const HEADERS = [
  '이벤트ID','접수시각','구분','접수번호','회사명','담당자','연락처','이메일',
  '제품/공종','문의제목','문의내용','품목상세','유입경로'
];

function doPost(e) {
  const data = JSON.parse(e.postData.contents || '{}');
  const expectedSecret = PropertiesService.getScriptProperties().getProperty('WEBHOOK_SECRET');
  if (!expectedSecret || data.secret !== expectedSecret) throw new Error('Unauthorized webhook request');

  const spreadsheetId = PropertiesService.getScriptProperties().getProperty('SPREADSHEET_ID');
  if (!spreadsheetId) throw new Error('SPREADSHEET_ID is not configured');

  const sheetName = data.eventType === 'SHOP_INQUIRY' ? 'SMART SHOP 문의'
    : data.eventType === 'QUOTE_INQUIRY' ? '온라인 견적의뢰' : '고객문의';
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const book = SpreadsheetApp.openById(spreadsheetId);
    const sheet = book.getSheetByName(sheetName) || book.insertSheet(sheetName);
    if (sheet.getLastRow() === 0) sheet.appendRow(HEADERS);
    else if (sheet.getRange(1, 1).getValue() !== '이벤트ID') {
      sheet.insertColumnBefore(1);
      sheet.getRange(1, 1).setValue('이벤트ID');
    }
    const eventId = String(data.outboxId || '');
    if (eventId && sheet.getLastRow() > 1) {
      const found = sheet.getRange(2, 1, sheet.getLastRow() - 1, 1)
        .createTextFinder(eventId).matchEntireCell(true).findNext();
      if (found) return ContentService.createTextOutput(JSON.stringify({ok:true,duplicate:true})).setMimeType(ContentService.MimeType.JSON);
    }
    sheet.appendRow([
      eventId, new Date(), data.eventType || '', data.receiptNumber || '', data.companyName || '',
      data.contactName || '', data.phone || '', data.email || '',
      data.productType || '', data.subject || '', data.message || data.details || '',
      JSON.stringify(data.items || []), data.source || 'PUBLIC'
    ]);
  } finally {
    lock.releaseLock();
  }
  return ContentService.createTextOutput(JSON.stringify({ok:true})).setMimeType(ContentService.MimeType.JSON);
}
