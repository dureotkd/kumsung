package kr.co.kumsungenc.platform.document;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import kr.co.kumsungenc.platform.file.FileStorageService;
import kr.co.kumsungenc.platform.file.StorageKeys;
import kr.co.kumsungenc.platform.notification.EmailOutboxService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;

@Service
public class EstimateDocumentService {
    private final JdbcTemplate jdbc;
    private final FileStorageService storage;
    private final EmailOutboxService outbox;
    private final String configuredFont;
    private final TransactionTemplate transactions;

    public EstimateDocumentService(JdbcTemplate jdbc,FileStorageService storage,EmailOutboxService outbox,
        @Value("${app.pdf-font:}") String configuredFont,
        TransactionTemplate transactions){
        this.jdbc=jdbc;this.storage=storage;this.outbox=outbox;
        this.configuredFont=configuredFont;
        this.transactions=transactions;
    }

    public record GenerateRequest(BigDecimal amount,String notes){}

    public Map<String,Object> generate(long quoteId,GenerateRequest request,Principal principal) throws IOException{
        if(request.amount()==null||request.amount().signum()<0)
            throw new IllegalArgumentException("견적 금액은 0 이상의 값으로 입력해 주세요.");
        Map<String,Object> quote=jdbc.queryForMap("select * from quote_requests where id=?",quoteId);
        String receipt=(String)quote.get("receipt_number");
        Path directory=Files.createTempDirectory("kumsung-estimate-");
        String base="estimate-"+receipt+"-"+System.currentTimeMillis();
        Path pdf=directory.resolve(base+".pdf");
        Path xlsx=directory.resolve(base+".xlsx");
        String pdfKey=StorageKeys.quoteDocument(receipt,pdf.getFileName().toString());
        String xlsxKey=StorageKeys.quoteDocument(receipt,xlsx.getFileName().toString());
        boolean uploaded=false;
        try{
            createPdf(pdf,quote,request);
            createWorkbook(xlsx,quote,request);
            String pdfHash=storage.store(pdf,pdfKey,"application/pdf");
            String xlsxHash=storage.store(xlsx,xlsxKey,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            uploaded=true;
            transactions.executeWithoutResult(status -> {
                try{
                    register(quoteId,pdf,"견적서 PDF","application/pdf",pdfHash);
                    register(quoteId,xlsx,"견적서 엑셀","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",xlsxHash);
                }catch(IOException e){throw new UncheckedIOException(e);}
                jdbc.update("update quote_requests set estimate_amount=?,estimate_notes=?,status='QUOTED',updated_at=current_timestamp where id=?",
                    request.amount(),clean(request.notes()),quoteId);
                jdbc.update("insert into quote_status_history(quote_request_id,status,note,changed_by) values (?,'QUOTED','견적서 PDF·엑셀 발행',?)",
                    quoteId,principal.getName());
                String body="""
                    요청하신 견적서가 발행되었습니다.
                    접수번호: %s
                    견적금액: %s원
                    고객 포털에서 PDF와 엑셀 견적서를 확인해 주세요.
                    """.formatted(receipt,request.amount().toPlainString());
                outbox.enqueue(quoteId,(String)quote.get("email"),"[(주)금성이엔씨] 견적서 발행 - "+receipt,body);
            });
            return Map.of("message","견적서 PDF와 엑셀이 생성되었습니다.","formats",List.of("PDF","XLSX"));
        }catch(UncheckedIOException e){
            if(uploaded){deleteQuietly(pdfKey);deleteQuietly(xlsxKey);}throw e.getCause();
        }catch(IOException|RuntimeException e){
            if(uploaded){deleteQuietly(pdfKey);deleteQuietly(xlsxKey);}throw e;
        }finally{
            Files.deleteIfExists(pdf);Files.deleteIfExists(xlsx);Files.deleteIfExists(directory);
        }
    }

    private void register(long quoteId,Path path,String title,String contentType,String checksum) throws IOException{
        jdbc.update("""
            insert into quote_documents(quote_request_id,document_type,title,original_name,stored_name,
              content_type,file_size,content_sha256)
            values (?,'ESTIMATE',?,?,?,?,?,?)
            """,quoteId,title,path.getFileName().toString(),path.getFileName().toString(),contentType,
            Files.size(path),checksum);
    }

    private void deleteQuietly(String key){try{storage.delete(key);}catch(IOException ignored){}}

    private void createPdf(Path target,Map<String,Object> quote,GenerateRequest request) throws IOException{
        Document document=new Document(PageSize.A4,44,44,44,44);
        OutputStream output=Files.newOutputStream(target);
        try{
            PdfWriter.getInstance(document,output);
            document.open();
            BaseFont base=loadKoreanFont();
            Font title=new Font(base,22,Font.BOLD,new Color(7,26,44));
            Font heading=new Font(base,11,Font.BOLD,new Color(18,104,232));
            Font normal=new Font(base,10,Font.NORMAL,Color.DARK_GRAY);
            Font amount=new Font(base,14,Font.BOLD,new Color(18,104,232));
            Paragraph h=new Paragraph("견 적 서",title);h.setAlignment(Element.ALIGN_CENTER);h.setSpacingAfter(24);document.add(h);
            document.add(paragraph("발행일  "+LocalDate.now(),normal,Element.ALIGN_RIGHT));
            document.add(paragraph("접수번호  "+value(quote,"receipt_number"),heading,Element.ALIGN_LEFT));
            PdfPTable table=new PdfPTable(new float[]{1.2f,2.8f});table.setWidthPercentage(100);table.setSpacingBefore(10);table.setSpacingAfter(18);
            row(table,"회사명",value(quote,"company_name"),base);
            row(table,"담당자",value(quote,"contact_name")+" / "+value(quote,"phone"),base);
            row(table,"현장",value(quote,"site_name")+"  "+value(quote,"site_address"),base);
            row(table,"제품·공종",value(quote,"product_type"),base);
            row(table,"견적 제목",value(quote,"subject"),base);
            row(table,"요청사항",value(quote,"details"),base);
            document.add(table);
            document.add(paragraph("견적 금액  "+String.format("%,.0f",request.amount())+" 원",amount,Element.ALIGN_RIGHT));
            if(request.notes()!=null&&!request.notes().isBlank()){
                Paragraph notes=new Paragraph("견적 비고\n"+request.notes().trim(),normal);notes.setSpacingBefore(18);notes.setLeading(16);document.add(notes);
            }
            Paragraph footer=new Paragraph("\n(주)금성이엔씨\n본 견적서는 고객 포털에서 발행된 전자문서입니다.",normal);
            footer.setAlignment(Element.ALIGN_CENTER);footer.setSpacingBefore(35);document.add(footer);
            document.close();
        }finally{
            if(document.isOpen())document.close();
            output.close();
        }
    }

    private void createWorkbook(Path target,Map<String,Object> quote,GenerateRequest request) throws IOException{
        try(Workbook workbook=new XSSFWorkbook();OutputStream output=Files.newOutputStream(target)){
            Sheet sheet=workbook.createSheet("견적서");sheet.setColumnWidth(0,5200);sheet.setColumnWidth(1,15000);
            CellStyle title=workbook.createCellStyle();org.apache.poi.ss.usermodel.Font tf=workbook.createFont();tf.setBold(true);tf.setFontHeightInPoints((short)20);title.setFont(tf);title.setAlignment(HorizontalAlignment.CENTER);
            Row titleRow=sheet.createRow(0);Cell titleCell=titleRow.createCell(0);titleCell.setCellValue("(주)금성이엔씨 견적서");titleCell.setCellStyle(title);sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0,0,0,1));
            int row=2;
            row=sheetRow(sheet,row,"발행일",LocalDate.now().toString());
            row=sheetRow(sheet,row,"접수번호",value(quote,"receipt_number"));
            row=sheetRow(sheet,row,"회사명",value(quote,"company_name"));
            row=sheetRow(sheet,row,"담당자",value(quote,"contact_name")+" / "+value(quote,"phone"));
            row=sheetRow(sheet,row,"현장",value(quote,"site_name")+" "+value(quote,"site_address"));
            row=sheetRow(sheet,row,"제품·공종",value(quote,"product_type"));
            row=sheetRow(sheet,row,"견적 제목",value(quote,"subject"));
            row=sheetRow(sheet,row,"요청사항",value(quote,"details"));
            Row amount=sheet.createRow(row++);amount.createCell(0).setCellValue("견적 금액");Cell amountCell=amount.createCell(1);amountCell.setCellValue(request.amount().doubleValue());
            CellStyle currency=workbook.createCellStyle();currency.setDataFormat(workbook.createDataFormat().getFormat("#,##0\" 원\""));amountCell.setCellStyle(currency);
            sheetRow(sheet,row,"견적 비고",Objects.requireNonNullElse(request.notes(),""));
            workbook.write(output);
        }
    }

    private int sheetRow(Sheet sheet,int index,String label,String value){
        Row row=sheet.createRow(index);row.createCell(0).setCellValue(label);row.createCell(1).setCellValue(value);return index+1;
    }
    private Paragraph paragraph(String value,Font font,int alignment){Paragraph p=new Paragraph(value,font);p.setAlignment(alignment);return p;}
    private void row(PdfPTable table,String label,String value,BaseFont base){
        PdfPCell left=new PdfPCell(new Phrase(label,new Font(base,9,Font.BOLD,Color.WHITE)));left.setBackgroundColor(new Color(7,26,44));left.setPadding(8);
        PdfPCell right=new PdfPCell(new Phrase(value,new Font(base,9)));right.setPadding(8);table.addCell(left);table.addCell(right);
    }
    private BaseFont loadKoreanFont() throws IOException{
        Path font=findFont();
        String name=font.toString();
        if(name.toLowerCase(Locale.ROOT).endsWith(".ttc"))name+=",0";
        return BaseFont.createFont(name,BaseFont.IDENTITY_H,BaseFont.EMBEDDED);
    }
    private Path findFont() throws IOException{
        List<Path> candidates=new ArrayList<>();
        if(configuredFont!=null&&!configuredFont.isBlank())candidates.add(Paths.get(configuredFont));
        candidates.add(Paths.get("C:/Windows/Fonts/malgun.ttf"));
        candidates.add(Paths.get("/usr/share/fonts/noto/NotoSansCJK-Regular.ttc"));
        candidates.add(Paths.get("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"));
        for(Path candidate:candidates)if(Files.isRegularFile(candidate))return candidate;
        Path root=Paths.get("/usr/share/fonts");
        if(Files.isDirectory(root))try(var paths=Files.walk(root)){
            return paths.filter(Files::isRegularFile).filter(p->{
                String n=p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".ttf")||n.endsWith(".otf")||n.endsWith(".ttc");
            }).findFirst().orElseThrow(()->new IOException("PDF 한글 글꼴을 찾을 수 없습니다. PDF_FONT를 설정해 주세요."));
        }
        throw new IOException("PDF 한글 글꼴을 찾을 수 없습니다. PDF_FONT를 설정해 주세요.");
    }
    private String value(Map<String,Object> row,String key){return Objects.toString(row.get(key),"");}
    private String clean(String value){return value==null||value.isBlank()?null:value.trim();}
}
