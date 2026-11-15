package com.inventory.service;

import com.inventory.dto.ReportResponse;
import com.inventory.dto.ReportResponse.ProductRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

@Service
public class ReportExcelService {

    public byte[] export(ReportResponse r, String businessName) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font bold = wb.createFont(); bold.setBold(true);
            CellStyle head = wb.createCellStyle(); head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle boldStyle = wb.createCellStyle(); boldStyle.setFont(bold);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            CellStyle moneyBold = wb.createCellStyle();
            moneyBold.setDataFormat(wb.createDataFormat().getFormat("#,##0.00")); moneyBold.setFont(bold);

            Sheet sum = wb.createSheet("Summary");
            int i = 0;
            Row t = sum.createRow(i++); Cell tc = t.createCell(0); tc.setCellValue(businessName + " - Inventory Report"); tc.setCellStyle(boldStyle);
            sum.createRow(i++).createCell(0).setCellValue("Sales period: " + r.label() + " (" + r.from() + " to " + r.to() + ")");
            i++;
            i = line(sum, i, "Number of products", r.productCount(), null);
            i = line(sum, i, "Units in stock", r.unitsInStock(), null);
            i = line(sum, i, "Total products value (stock at cost price)", r.totalProductsValue(), money);
            i = line(sum, i, "Expected product value (stock at selling price)", r.expectedProductValue(), money);
            i = line(sum, i, "Expected profit from current stock", r.expectedProfit(), money);
            i++;
            i = line(sum, i, "Number of sales", r.salesCount(), null);
            i = line(sum, i, "Units sold", r.unitsSold(), null);
            i = line(sum, i, "Sales revenue", r.salesRevenue(), money);
            i = line(sum, i, "Cost of goods sold", r.salesCost(), money);
            line(sum, i, "Profit made from sales", r.salesProfit(), moneyBold);
            sum.setColumnWidth(0, 52 * 256); sum.setColumnWidth(1, 20 * 256);

            Sheet sh = wb.createSheet("Products");
            String[] heads = {"Product", "SKU", "Category", "Quantity", "Reorder level", "Cost price", "Selling price",
                "Stock value (cost)", "Expected value (selling)", "Expected profit", "Units sold", "Sales revenue", "Sales profit"};
            Row hr = sh.createRow(0);
            for (int c = 0; c < heads.length; c++) { Cell cell = hr.createCell(c); cell.setCellValue(heads[c]); cell.setCellStyle(head); }
            int rowNum = 1;
            for (ProductRow p : r.products()) {
                Row row = sh.createRow(rowNum++);
                row.createCell(0).setCellValue(p.name());
                row.createCell(1).setCellValue(p.sku());
                row.createCell(2).setCellValue(p.category());
                row.createCell(3).setCellValue(p.quantity());
                row.createCell(4).setCellValue(p.reorderLevel());
                num(row, 5, p.costPrice(), money); num(row, 6, p.sellingPrice(), money);
                num(row, 7, p.stockValue(), money); num(row, 8, p.expectedValue(), money); num(row, 9, p.expectedProfit(), money);
                row.createCell(10).setCellValue(p.unitsSold());
                num(row, 11, p.revenue(), money); num(row, 12, p.profit(), money);
            }
            Row tot = sh.createRow(rowNum);
            Cell label = tot.createCell(0); label.setCellValue("TOTAL"); label.setCellStyle(boldStyle);
            Cell q = tot.createCell(3); q.setCellValue(r.unitsInStock()); q.setCellStyle(boldStyle);
            num(tot, 7, r.totalProductsValue(), moneyBold); num(tot, 8, r.expectedProductValue(), moneyBold);
            num(tot, 9, r.expectedProfit(), moneyBold);
            Cell us = tot.createCell(10); us.setCellValue(r.unitsSold()); us.setCellStyle(boldStyle);
            num(tot, 11, r.salesRevenue(), moneyBold); num(tot, 12, r.salesProfit(), moneyBold);
            for (int c = 0; c < heads.length; c++) sh.setColumnWidth(c, (c == 0 ? 30 : 18) * 256);
            sh.createFreezePane(0, 1);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private static int line(Sheet sheet, int rowIndex, String label, Number value, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        Cell c = row.createCell(1);
        c.setCellValue(value.doubleValue());
        if (style != null) c.setCellStyle(style);
        return rowIndex + 1;
    }

    private static void num(Row row, int col, BigDecimal v, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(v.doubleValue());
        c.setCellStyle(style);
    }
}
