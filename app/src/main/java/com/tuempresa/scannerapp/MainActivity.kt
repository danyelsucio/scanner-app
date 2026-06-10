package com.tuempresa.scannerapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import cz.adaptech.tesseract4android.Tesseract
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var btnTakePhoto: Button
    private lateinit var tvScannedText: TextView
    private lateinit var btnSelectAndSave: Button
    
    private var currentPhotoPath: String = ""
    private var lastScannedText: String = ""
    private var photoUri: Uri? = null

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
        private const val REQUEST_IMAGE_CAPTURE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        tvScannedText = findViewById(R.id.tvScannedText)
        btnSelectAndSave = findViewById(R.id.btnSelectAndSave)

        btnTakePhoto.setOnClickListener {
            if (checkCameraPermission()) {
                dispatchTakePictureIntent()
            } else {
                requestCameraPermission()
            }
        }

        btnSelectAndSave.setOnClickListener {
            if (lastScannedText.isNotEmpty()) {
                showSelectionDialog(lastScannedText)
            }
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent()
            } else {
                Toast.makeText(this, "Se necesita permiso de cámara", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile = createImageFile()
                photoFile?.also {
                    photoUri = FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile(imageFileName, ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            photoUri?.let { uri ->
                Toast.makeText(this, "Foto tomada, escaneando texto...", Toast.LENGTH_SHORT).show()
                recognizeTextWithTesseract(uri)
            }
        }
    }

    private fun recognizeTextWithTesseract(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Inicializar Tesseract
            val tesseract = Tesseract(this)
            tesseract.init("eng")  // Idioma inglés
            
            // Escanear texto
            val recognizedText = tesseract.ocr(bitmap)
            lastScannedText = recognizedText ?: ""
            
            if (lastScannedText.isNotEmpty()) {
                tvScannedText.text = lastScannedText
                btnSelectAndSave.isEnabled = true
                Toast.makeText(this, "Texto encontrado: ${lastScannedText.length} caracteres", Toast.LENGTH_SHORT).show()
            } else {
                tvScannedText.text = "No se encontró texto en la imagen"
                btnSelectAndSave.isEnabled = false
                Toast.makeText(this, "No se encontró texto", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            tvScannedText.text = "Error OCR: ${e.message}"
            btnSelectAndSave.isEnabled = false
            Toast.makeText(this, "Error en OCR: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSelectionDialog(fullText: String) {
        val words = fullText.split(Regex("\\s+"))
        val options = arrayOf("Guardar TODO el texto", "Seleccionar palabra o frase")
        
        AlertDialog.Builder(this)
            .setTitle("Texto escaneado:")
            .setMessage(fullText.take(200) + if (fullText.length > 200) "..." else "")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> saveToExcel(fullText)
                    1 -> showWordSelection(words, fullText)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showWordSelection(words: List<String>, originalText: String) {
        AlertDialog.Builder(this)
            .setTitle("Selecciona la palabra o frase")
            .setItems(words.toTypedArray()) { _, which ->
                val selected = words[which]
                AlertDialog.Builder(this)
                    .setTitle("¿Guardar esta frase?")
                    .setMessage("Texto seleccionado: \"$selected\"")
                    .setPositiveButton("Guardar") { _, _ ->
                        saveToExcel(selected)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNeutralButton("Guardar frase personalizada") { _, _ ->
                showCustomPhraseDialog(originalText)
            }
            .show()
    }

    private fun showCustomPhraseDialog(fullText: String) {
        val input = android.widget.EditText(this)
        input.setText(fullText)
        input.setSelection(fullText.length)
        
        AlertDialog.Builder(this)
            .setTitle("Escribe o modifica la frase a guardar")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val customText = input.text.toString()
                if (customText.isNotEmpty()) {
                    saveToExcel(customText)
                } else {
                    Toast.makeText(this, "No se guardó nada", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun saveToExcel(textToSave: String) {
        try {
            val excelFile = File(getExternalFilesDir(null), "datos_escaneo.xlsx")
            val workbook: Workbook
            val sheet: org.apache.poi.ss.usermodel.Sheet

            if (excelFile.exists()) {
                val fis = FileInputStream(excelFile)
                workbook = XSSFWorkbook(fis)
                sheet = workbook.getSheetAt(0)
                fis.close()
            } else {
                workbook = XSSFWorkbook()
                sheet = workbook.createSheet("Escaneos")
                val headerRow = sheet.createRow(0)
                headerRow.createCell(0).setCellValue("Fecha")
                headerRow.createCell(1).setCellValue("Texto")
            }

            val nextRow = sheet.lastRowNum + 1
            val row = sheet.createRow(nextRow)
            row.createCell(0).setCellValue(SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()))
            row.createCell(1).setCellValue(textToSave)

            val fos = FileOutputStream(excelFile)
            workbook.write(fos)
            fos.close()
            workbook.close()

            Toast.makeText(this, "✓ Guardado en Excel correctamente", Toast.LENGTH_LONG).show()

            AlertDialog.Builder(this)
                .setTitle("Éxito")
                .setMessage("Texto guardado en Excel\n📁 ${excelFile.absolutePath}")
                .setPositiveButton("OK", null)
                .show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
