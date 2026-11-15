package com.inventory.service;

import com.inventory.dto.ImportResult;
import com.inventory.dto.ProductRequest;
import com.inventory.repository.ProductRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ProductExcelService {
    private static final int MAX_ROWS = 5000;
    private final InventoryService inventory;
    private final ProductRepository products;
    public ProductExcelService(InventoryService inventory, ProductRepository products) {
        this.inventory = inventory; this.products = products;
    }

    public ImportResult importProducts(MultipartFile file, boolean debitCash) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Please choose an Excel file.");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx")) throw new IllegalArgumentException("Only .xlsx Excel files are supported.");
        int imported = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("The workbook has no worksheet.");
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() < 2) throw new IllegalArgumentException("The worksheet has no product rows.");
            if (sheet.getLastRowNum() > MAX_ROWS) throw new IllegalArgumentException("Too many rows. Import at most " + MAX_ROWS + " products at a time.");
            Row header = sheet.getRow(0);
            Map<String,Integer> columns = new HashMap<>();
            if (header != null) for (Cell cell : header) columns.put(normalize(text(cell)), cell.getColumnIndex());
            String[] required = {"name", "sku", "category", "quantity", "costprice", "sellingprice"};
            for (String key : required) if (!columns.containsKey(key)) throw new IllegalArgumentException("Missing required Excel column: " + key + ". Download the template for the correct format.");
            DataFormatter formatter = new DataFormatter();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r); if (row == null || isBlank(row, formatter)) continue;
                try {
                    String productName = value(row, columns, "name", formatter);
                    String sku = value(row, columns, "sku", formatter);
                    String category = value(row, columns, "category", formatter);
                    if (productName.isBlank() || sku.isBlank() || category.isBlank()) throw new IllegalArgumentException("Name, SKU and category are required.");
                    if (products.existsBySku(sku)) throw new IllegalArgumentException("SKU already exists: " + sku);
                    int quantity = integer(value(row, columns, "quantity", formatter), "quantity");
                    int reorder = columns.containsKey("reorderlevel") && !value(row, columns, "reorderlevel", formatter).isBlank()
                        ? integer(value(row, columns, "reorderlevel", formatter), "reorder level") : 5;
                    BigDecimal cost = decimal(value(row, columns, "costprice", formatter), "cost price");
                    BigDecimal selling = decimal(value(row, columns, "sellingprice", formatter), "selling price");
                    if (quantity < 0 || reorder < 0 || cost.signum() < 0 || selling.signum() < 0) throw new IllegalArgumentException("Quantity, reorder level and prices cannot be negative.");
                    inventory.createProduct(new ProductRequest(productName, sku, category, quantity, reorder, cost, selling), debitCash);
                    imported++;
                } catch (Exception ex) { skipped++; errors.add("Row " + (r + 1) + ": " + safeMessage(ex)); }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("The Excel file is damaged or is not a valid .xlsx workbook.");
        }
        return new ImportResult(imported, skipped, errors);
    }

    public byte[] createTemplate() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Products");
            String[] headers = {"Name", "SKU", "Category", "Quantity", "Cost Price", "Selling Price", "Reorder Level"};
            Row header = sheet.createRow(0);
            CellStyle style = wb.createCellStyle(); Font font = wb.createFont(); font.setBold(true); style.setFont(font);
            for (int i=0;i<headers.length;i++) { Cell c=header.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(style); sheet.setColumnWidth(i, 20*256); }
            Row sample=sheet.createRow(1); String[] values={"Example Product","SKU-001","Groceries","20","1500","2000","5"};
            for(int i=0;i<values.length;i++) sample.createCell(i).setCellValue(values[i]);
            wb.write(out); return out.toByteArray();
        }
    }
    private static String normalize(String s) { return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", ""); }
    private static String text(Cell c) { return new DataFormatter().formatCellValue(c).trim(); }
    private static String value(Row row, Map<String,Integer> cols, String key, DataFormatter f) { Cell c=row.getCell(cols.get(key)); return c==null?"":f.formatCellValue(c).trim(); }
    private static boolean isBlank(Row row, DataFormatter f) { for(Cell c:row) if(!f.formatCellValue(c).isBlank()) return false; return true; }
    private static int integer(String s,String label) { try { BigDecimal n=new BigDecimal(s.replace(",", "")); return n.intValueExact(); } catch(Exception e) { throw new IllegalArgumentException(label + " must be a whole number."); } }
    private static BigDecimal decimal(String s,String label) { try { return new BigDecimal(s.replace(",", "").replace("₦", "").trim()); } catch(Exception e) { throw new IllegalArgumentException(label + " must be a valid number."); } }
    private static String safeMessage(Exception e) { return e.getMessage()==null?"Invalid product data.":e.getMessage(); }
}
