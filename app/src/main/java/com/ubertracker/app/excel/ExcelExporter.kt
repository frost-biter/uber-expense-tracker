package com.ubertracker.app.excel

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.ubertracker.app.data.Ride
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ExcelExporter(private val context: Context) {

    fun generateExcel(rides: List<Ride>): File {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Uber Expenses")
        val currencyStyle = createCurrencyStyle(workbook)

        // Add data rows
        rides.forEachIndexed { index, ride ->
            val row = sheet.createRow(index) // Start from the first row
            val serialNumber = index + 1
            val formattedDate = reformatDate(ride.date)
            val company = "Mathlogic"
            val natureOfExpenses = "To ${ride.toAddress} from ${ride.fromAddress}"
            val amount = ride.fare

            row.createCell(0).setCellValue(serialNumber.toDouble())
            row.createCell(1).setCellValue(formattedDate)
            row.createCell(2).setCellValue(company)
            row.createCell(3).setCellValue(natureOfExpenses)

            val amountCell = row.createCell(4)
            amountCell.setCellValue(amount)
            amountCell.cellStyle = currencyStyle
        }

        // Set column widths manually (autoSizeColumn is not available on Android)
        // Width is in units of 1/256th of a character width
        sheet.setColumnWidth(0, 1500) // S. No.
        sheet.setColumnWidth(1, 3000) // Date
        sheet.setColumnWidth(2, 3000) // Company
        sheet.setColumnWidth(3, 10000) // Nature of Expenses
        sheet.setColumnWidth(4, 2500) // Amount

        // Save to file
        val fileName = generateFileName()
        val file = File(context.cacheDir, fileName)

        FileOutputStream(file).use {
            workbook.write(it)
        }
        workbook.close()

        return file
    }

    private fun reformatDate(dateStr: String): String {
        return try {
            val dbFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val excelFormat = SimpleDateFormat("d/M/yyyy", Locale.getDefault())
            val date = dbFormat.parse(dateStr)
            if (date != null) excelFormat.format(date) else dateStr
        } catch (e: Exception) {
            dateStr // Return original string if parsing fails
        }
    }

    private fun createCurrencyStyle(workbook: Workbook): XSSFCellStyle {
        val style = workbook.createCellStyle() as XSSFCellStyle
        val format = workbook.createDataFormat()
        style.dataFormat = format.getFormat("0.00") // Simple number format
        style.alignment = HorizontalAlignment.RIGHT
        return style
    }

    private fun generateFileName(): String {
        val calendar = Calendar.getInstance()
        val month = SimpleDateFormat("MMMM_yyyy", Locale.getDefault()).format(calendar.time)
        return "Uber_Expenses_$month.xlsx"
    }

    private fun getExportDirectory(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "UberExpenses"
        )

        if (!dir.exists()) {
            dir.mkdirs()
        }

        return dir
    }

    fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share Claim Report")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}