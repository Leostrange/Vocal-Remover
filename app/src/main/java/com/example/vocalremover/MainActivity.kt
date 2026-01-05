package com.example.vocalremover

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : AppCompatActivity(), VocalProcessor.ProgressCallback {
    
    private lateinit var selectedFileButton: Button
    private lateinit var processVocalsButton: Button
    private lateinit var processStemsButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var statusText: TextView
    
    // Activity Result API для современной обработки результатов
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleSelectedFile(it) }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            openFilePicker()
        } else {
            Toast.makeText(this, "Требуются разрешения для доступа к файлам", Toast.LENGTH_LONG).show()
        }
    }
    
    private var selectedFile: File? = null
    private val vocalProcessor = VocalProcessor(this)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
    }
    
    private fun initViews() {
        selectedFileButton = findViewById(R.id.selectFileButton)
        processVocalsButton = findViewById(R.id.processVocalsButton)
        processStemsButton = findViewById(R.id.processStemsButton)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        statusText = findViewById(R.id.statusText)
    }
    
    private fun setupListeners() {
        selectedFileButton.setOnClickListener {
            checkPermissionsAndOpenFilePicker()
        }
        
        processVocalsButton.setOnClickListener {
            selectedFile?.let { file ->
                startVocalsSeparation(file)
            } ?: run {
                Toast.makeText(this, "Сначала выберите файл", Toast.LENGTH_SHORT).show()
            }
        }
        
        processStemsButton.setOnClickListener {
            selectedFile?.let { file ->
                startStemsSeparation(file)
            } ?: run {
                Toast.makeText(this, "Сначала выберите файл", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun checkPermissionsAndOpenFilePicker() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isEmpty()) {
            openFilePicker()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    private fun openFilePicker() {
        try {
            filePickerLauncher.launch("audio/*")
        } catch (e: Exception) {
            // Fallback для старых устройств
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "audio/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            startActivity(Intent.createChooser(intent, "Выберите аудио файл"))
        }
    }
    
    private fun handleSelectedFile(uri: Uri) {
        try {
            // Копируем файл во внутреннее хранилище для обработки
            selectedFile = copyUriToInternalStorage(uri)
            selectedFileButton.text = "Выбран: ${selectedFile?.name ?: "файл"}"
            statusText.text = "Файл готов к обработке"
            processVocalsButton.isEnabled = true
            processStemsButton.isEnabled = true
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки файла: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun copyUriToInternalStorage(uri: Uri): File? {
        return try {
            val contentResolver: ContentResolver = applicationContext.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return null
            
            // Получаем имя файла из URI или генерируем
            val fileName = getFileName(uri) ?: "audio_${System.currentTimeMillis()}.mp3"
            val outputFile = File(cacheDir, fileName)
            
            FileOutputStream(outputFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            
            outputFile
        } catch (e: Exception) {
            Log.e("MainActivity", "Error copying file from URI", e)
            null
        }
    }
    
    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
    
    private fun startVocalsSeparation(file: File) {
        val outputDir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "vocal_remover")
        
        updateUI(0, "Начинаем разделение вокала...")
        processVocalsButton.isEnabled = false
        processStemsButton.isEnabled = false
        
        vocalProcessor.separateVocals(file, outputDir, this)
    }
    
    private fun startStemsSeparation(file: File) {
        val outputDir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "stems_remover")
        
        updateUI(0, "Начинаем разделение на дорожки...")
        processVocalsButton.isEnabled = false
        processStemsButton.isEnabled = false
        
        vocalProcessor.separateStems(file, outputDir, 4, this)
    }
    
    private fun updateUI(progress: Int, message: String) {
        runOnUiThread {
            progressBar.progress = progress
            progressText.text = "$progress%"
            statusText.text = message
        }
    }
    
    private fun resetUI() {
        runOnUiThread {
            progressBar.progress = 0
            progressText.text = "0%"
            statusText.text = "Готов к обработке"
            processVocalsButton.isEnabled = true
            processStemsButton.isEnabled = true
        }
    }
    

    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Этот метод оставлен для обратной совместимости
        // Основная обработка теперь через ActivityResult API
    }
    
    // VocalProcessor.ProgressCallback implementation
    override fun onProgress(progress: Int, message: String) {
        updateUI(progress, message)
    }
    
    override fun onComplete(outputFile: File) {
        runOnUiThread {
            Toast.makeText(
                this,
                "Обработка завершена! Файл сохранен: ${outputFile.name}",
                Toast.LENGTH_LONG
            ).show()
            
            // Открываем файл с использованием FileProvider для Android 7+
            val uri = Uri.fromFile(outputFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (outputFile.name.endsWith(".zip")) "application/zip" else "audio/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            try {
                startActivity(Intent.createChooser(intent, "Открыть результат"))
            } catch (e: Exception) {
                // Если не удалось открыть, показываем путь
                Toast.makeText(this, "Результат сохранен: ${outputFile.absolutePath}", Toast.LENGTH_LONG).show()
            }
            
            resetUI()
        }
    }
    
    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(this, "Ошибка: $error", Toast.LENGTH_LONG).show()
            resetUI()
        }
    }

    override fun onIntermediateResult(type: String, data: Any) {
        // Промежуточные результаты обрабатываются здесь
        Log.d("MainActivity", "Intermediate result: $type")
    }
}
