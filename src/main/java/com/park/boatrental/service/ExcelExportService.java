package com.park.boatrental.service;

import com.park.boatrental.dto.ExportResult;
import com.park.boatrental.model.Rental;
import com.park.boatrental.repository.RentalRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ExcelExportService {

    private static final String[] HEADERS = {
            "Renter name",
            "Boat number",
            "Date",
            "Time assigned",
            "Time returned",
            "Rental # that day"
    };

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    private final RentalRepository rentalRepository;
    private final DailyRentalNumberService dailyRentalNumberService;
    private final Path exportPath;
    private final ZoneId zoneId;

    public ExcelExportService(
            RentalRepository rentalRepository,
            DailyRentalNumberService dailyRentalNumberService,
            @Value("${boatrental.export.path}") String exportPath,
            @Value("${boatrental.timezone}") String timezone) {
        this.rentalRepository = rentalRepository;
        this.dailyRentalNumberService = dailyRentalNumberService;
        this.exportPath = Path.of(exportPath);
        this.zoneId = ZoneId.of(timezone);
    }

    @Transactional
    public ExportResult appendCompletedRentals() throws IOException {
        List<Rental> rentals = rentalRepository.findCompletedNotExported();
        if (rentals.isEmpty()) {
            return new ExportResult(0, exportPath.toAbsolutePath().toString());
        }

        Files.createDirectories(exportPath.getParent());

        Workbook workbook;
        if (Files.exists(exportPath)) {
            try (InputStream in = Files.newInputStream(exportPath)) {
                workbook = new XSSFWorkbook(in);
            }
        } else {
            workbook = new XSSFWorkbook();
        }

        try {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : workbook.createSheet("Rentals");
            int nextRowIndex = sheet.getLastRowNum() + 1;
            if (nextRowIndex == 0 || sheet.getRow(0) == null || !hasHeader(sheet.getRow(0))) {
                writeHeader(sheet.createRow(0));
                nextRowIndex = 1;
            }

            Instant exportedAt = Instant.now();
            for (Rental rental : rentals) {
                writeRentalRow(sheet.createRow(nextRowIndex++), rental);
                rental.setExportedAt(exportedAt);
            }
            rentalRepository.saveAll(rentals);

            try (OutputStream out = Files.newOutputStream(exportPath)) {
                workbook.write(out);
            }
        } finally {
            workbook.close();
        }

        return new ExportResult(rentals.size(), exportPath.toAbsolutePath().toString());
    }

    private void writeHeader(Row row) {
        for (int i = 0; i < HEADERS.length; i++) {
            row.createCell(i).setCellValue(HEADERS[i]);
        }
    }

    private static boolean hasHeader(Row row) {
        Cell first = row.getCell(0);
        return first != null && "Renter name".equalsIgnoreCase(first.getStringCellValue().trim());
    }

    private void writeRentalRow(Row row, Rental rental) {
        Instant checkout = rental.getSentAt() != null ? rental.getSentAt() : rental.getAssignedAt();
        var checkoutLocal = checkout.atZone(zoneId);
        var returned = rental.getReturnedAt().atZone(zoneId);

        setCell(row, 0, rental.getCustomerName());
        setCell(row, 1, rental.getBoat().getBoatNumber());
        setCell(row, 2, checkoutLocal.format(DATE_FMT));
        setCell(row, 3, checkoutLocal.format(TIME_FMT));
        setCell(row, 4, returned.format(TIME_FMT));
        setCell(row, 5, dailyRentalNumberService.numberForRental(rental));
    }

    private static void setCell(Row row, int column, String value) {
        row.createCell(column).setCellValue(value);
    }

    private static void setCell(Row row, int column, int value) {
        row.createCell(column).setCellValue(value);
    }
}
