package com.park.boatrental.web;

import com.park.boatrental.dto.ExportResult;
import com.park.boatrental.service.ExcelExportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExcelExportService excelExportService;

    public ExportController(ExcelExportService excelExportService) {
        this.excelExportService = excelExportService;
    }

    @PostMapping("/excel")
    public ExportResult appendToExcel() {
        try {
            return excelExportService.appendCompletedRentals();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not write Excel file: " + e.getMessage());
        }
    }
}
