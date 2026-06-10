package com.tuempresa.scannerapp

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: Button
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var ultimaImagenUri: Uri? = null

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        btnCapture = findViewById(R.id.btnCapture)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
        }

        btnCapture.setOnClickListener {
            takePhotoAndScan()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permiso de cámara necesario", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error al iniciar cámara", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhotoAndScan() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "temp_$name.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScannerApp")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    ultimaImagenUri = outputFileResults.savedUri
                    Toast.makeText(this@MainActivity, "Foto tomada, escaneando texto...", Toast.LENGTH_SHORT).show()
                    reconocerTexto()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Error al tomar foto", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun reconocerTexto() {
        val uri = ultimaImagenUri ?: return

        val inputStream = contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { result ->
                val textoCompleto = result.text
                if (textoCompleto.isNotEmpty()) {
                    mostrarDialogoSeleccion(textoCompleto)
                } else {
                    Toast.makeText(this, "No se encontró texto en la imagen", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error OCR: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun mostrarDialogoSeleccion(textoCompleto: String) {
        val opciones = arrayOf("Seleccionar parte del texto", "Guardar todo el texto")

        AlertDialog.Builder(this)
            .setTitle("Texto encontrado:")
            .setMessage(textoCompleto)
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> seleccionarParte(textoCompleto)
                    1 -> guardarEnExcel(textoCompleto)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun seleccionarParte(textoCompleto: String) {
        val palabras = textoCompleto.split(Regex("\\s+"))
        AlertDialog.Builder(this)
            .setTitle("Selecciona lo que quieres guardar")
            .setItems(palabras.toTypedArray()) { _, which ->
                val seleccionado = palabras[which]
                guardarEnExcel(seleccionado)
            }
            .show()
    }

    private fun guardarEnExcel(textoAGuardar: String) {
        try {
            val excelFile = java.io.File(getExternalFilesDir(null), "datos_escaneo.xlsx")
            val workbook: Workbook
            val sheet: org.apache.poi.ss.usermodel.Sheet

            if (excelFile.exists()) {
                val fis = java.io.FileInputStream(excelFile)
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
            row.createCell(0).setCellValue(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()))
            row.createCell(1).setCellValue(textoAGuardar)

            val fos = FileOutputStream(excelFile)
            workbook.write(fos)
            fos.close()
            workbook.close()

            Toast.makeText(this, "Guardado en Excel ✅\n${excelFile.absolutePath}", Toast.LENGTH_LONG).show()

            // Preguntar si quiere abrir el archivo
            AlertDialog.Builder(this)
                .setTitle("¿Abrir el archivo Excel?")
                .setPositiveButton("Sí") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.fromFile(excelFile), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Ahora no", null)
                .show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
