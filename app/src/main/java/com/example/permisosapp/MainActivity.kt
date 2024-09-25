package com.example.permisosapp

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Vibrator
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.permisosapp.ui.theme.PermisosAppTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.io.OutputStream

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar el cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            PermisosAppTheme {
                // State variables para cada permiso
                val cameraGranted = remember { mutableStateOf(false) }
                val locationGranted = remember { mutableStateOf(false) }
                val storageGranted = remember { mutableStateOf(false) }
                val microphoneGranted = remember { mutableStateOf(false) }

                // Contexto para mostrar Toast y acceder al Vibrator
                val context = LocalContext.current

                // Inicializar el estado de los permisos al iniciar
                LaunchedEffect(Unit) {
                    cameraGranted.value = checkPermission(Manifest.permission.CAMERA)
                    locationGranted.value = checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    microphoneGranted.value = checkPermission(Manifest.permission.RECORD_AUDIO)

                    //Manejo de permisos multimedia dependiendo la version Android del teléfono
                    storageGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        checkPermission(Manifest.permission.READ_MEDIA_IMAGES) &&
                                checkPermission(Manifest.permission.READ_MEDIA_VIDEO) &&
                                checkPermission(Manifest.permission.READ_MEDIA_AUDIO)
                    } else {
                        checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }

                // Launchers para solicitar permisos
                val cameraLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    cameraGranted.value = isGranted
                    showToast(context, "Permiso de Cámara ${if (isGranted) "Concedido" else "Denegado"}")
                }

                val locationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    locationGranted.value = isGranted
                    showToast(context, "Permiso de Ubicación ${if (isGranted) "Concedido" else "Denegado"}")
                }

                val microphoneLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    microphoneGranted.value = isGranted
                    showToast(context, "Permiso de Micrófono ${if (isGranted) "Concedido" else "Denegado"}")
                }

                // Launcher para solicitar múltiples permisos de almacenamiento
                val storageLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions[Manifest.permission.READ_MEDIA_IMAGES] == true &&
                                permissions[Manifest.permission.READ_MEDIA_VIDEO] == true &&
                                permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
                    } else {
                        permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
                    }
                    storageGranted.value = granted
                    showToast(context, "Permiso de Almacenamiento ${if (granted) "Concedido" else "Denegado"}")
                }

                // Determinar las permisiones de almacenamiento a solicitar
                val storagePermissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO
                    )
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }

                // Launchers para acciones
                val takePictureLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicturePreview()
                ) { bitmap ->
                    if (bitmap != null) {
                        saveImageToGallery(context, bitmap)
                    } else {
                        showToast(context, "Error al capturar la foto.")
                    }
                }

                val pickFileLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    uri?.let {
                        readFileContent(context, it)
                    } ?: showToast(context, "No se seleccionó ningún archivo.")
                }

                // Interfaz de Usuario
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Gestión de Permisos y Acciones") }
                        )
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Sección de Permisos
                        Text(
                            text = "Solicitar Permisos",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Divider(modifier = Modifier.fillMaxWidth())

                        Spacer(modifier = Modifier.height(8.dp))

                        // Botón de Cámara
                        PermissionButton(
                            text = "Solicitar Permiso de Cámara",
                            onClick = {
                                vibratePhone(context)
                                cameraLauncher.launch(Manifest.permission.CAMERA)
                            }
                        )
                        PermissionStatusText(
                            permissionName = "Cámara",
                            isGranted = cameraGranted.value
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Botón de Ubicación
                        PermissionButton(
                            text = "Solicitar Permiso de Ubicación",
                            onClick = {
                                vibratePhone(context)
                                locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        )
                        PermissionStatusText(
                            permissionName = "Ubicación",
                            isGranted = locationGranted.value
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Botón de Almacenamiento
                        PermissionButton(
                            text = "Solicitar Permiso de Almacenamiento",
                            onClick = {
                                vibratePhone(context)
                                storageLauncher.launch(storagePermissionsToRequest)
                            }
                        )
                        PermissionStatusText(
                            permissionName = "Almacenamiento",
                            isGranted = storageGranted.value
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Botón de Micrófono
                        PermissionButton(
                            text = "Solicitar Permiso de Micrófono",
                            onClick = {
                                vibratePhone(context)
                                microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                        PermissionStatusText(
                            permissionName = "Micrófono",
                            isGranted = microphoneGranted.value
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Divisor entre permisos y acciones
                        Divider(modifier = Modifier.fillMaxWidth())

                        Spacer(modifier = Modifier.height(16.dp))

                        // Sección de Acciones
                        Text(
                            text = "Acciones",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Botón para Tomar una Foto
                        ActionButton(
                            text = "Tomar una Foto",
                            onClick = {
                                vibratePhone(context)
                                takePictureLauncher.launch(null)
                            },
                            enabled = cameraGranted.value
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Botón para Acceder a la Ubicación
                        ActionButton(
                            text = "Acceder a la Ubicación",
                            onClick = {
                                vibratePhone(context)
                                accessLocation()
                            },
                            enabled = locationGranted.value
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Botón para Leer un Archivo
                        ActionButton(
                            text = "Leer un Archivo",
                            onClick = {
                                vibratePhone(context)
                                pickFileLauncher.launch("*/*") // Filtra todos los tipos de archivos
                            },
                            enabled = storageGranted.value
                        )
                    }
                }
            }
        }
    }

    // Función para mostrar Toast
    private fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // Función para hacer vibrar el teléfono
    private fun vibratePhone(context: Context) {
        val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        100,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        }
    }

    // Función para guardar la imagen en la galería
    private fun saveImageToGallery(context: Context, bitmap: android.graphics.Bitmap) {
        val filename = "IMG_${System.currentTimeMillis()}.jpg"
        var uri: Uri? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    val outputStream: OutputStream? = context.contentResolver.openOutputStream(it)
                    outputStream?.use { stream ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, stream)
                        showToast(context, "Foto guardada en la galería.")
                    }
                }
            } else {
                // Para versiones anteriores a Q
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
                val image = java.io.File(imagesDir, filename)
                val outputStream: OutputStream = image.outputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.flush()
                outputStream.close()
                // Escanear el archivo para que aparezca en la galería
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(image)))
                showToast(context, "Foto guardada en la galería.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(context, "Error al guardar la foto.")
        }
    }

    // Función para acceder a la ubicación
    private fun accessLocation() {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        showToast(this@MainActivity, "Ubicación: Latitud ${it.latitude}, Longitud ${it.longitude}")
                    } ?: run {
                        showToast(this@MainActivity, "No se pudo obtener la ubicación.")
                    }
                }
                .addOnFailureListener { exception ->
                    showToast(this@MainActivity, "Error al obtener la ubicación.")
                }
        } else {
            showToast(this@MainActivity, "Permiso de Ubicación no concedido.")
        }
    }

    // Función para leer el contenido de un archivo
    private fun readFileContent(context: Context, uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val content = inputStream?.bufferedReader().use { it?.readText() }
            inputStream?.close()
            content?.let {
                showToast(context, "Contenido del archivo:\n$it")
            } ?: showToast(context, "No se pudo leer el archivo.")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(context, "Error al leer el archivo.")
        }
    }

    @Composable
    fun PermissionButton(text: String, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(text = text, fontSize = 16.sp)
        }
    }

    @Composable
    fun ActionButton(text: String, onClick: () -> Unit, enabled: Boolean) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = enabled
        ) {
            Text(text = text, fontSize = 16.sp)
        }
    }

    @Composable
    fun PermissionStatusText(permissionName: String, isGranted: Boolean) {
        Text(
            text = "$permissionName: ${if (isGranted) "Concedido" else "Denegado"}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    // Función para verificar el estado del permiso
    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}
