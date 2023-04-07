package jp.yuppe.camera2dual

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.TextureView

class DualCameraManager(activity: Activity, textureViewFront: TextureView, textureViewBack: TextureView) {
    private val TAG = DualCameraManager::class.simpleName
    private var activity: Activity
    private var shouldProceedWithOnResume: Boolean = true

    private lateinit var cameraManager: CameraManager

    private var textureViewFront: TextureView
    private lateinit var videoCaptureFront: VideoCapture
    private lateinit var cameraIdFront: String
    private lateinit var previewSizeFront: Size
    private lateinit var videoSizeFront: Size
    private lateinit var handlerThreadFront: HandlerThread
    private lateinit var handlerFront: Handler

    private var textureViewBack: TextureView
    private lateinit var videoCaptureBack: VideoCapture
    private lateinit var cameraIdBack: String
    private lateinit var previewSizeBack: Size
    private lateinit var videoSizeBack: Size
    private lateinit var handlerThreadBack: HandlerThread
    private lateinit var handlerBack: Handler

    init {
        this.activity = activity
        this.textureViewFront = textureViewFront
        this.textureViewBack = textureViewBack
        startWorkerThreadFront()
        startWorkerThreadBack()
    }

    fun onResume() {
        startWorkerThreadFront()
        startWorkerThreadBack()

        if (textureViewFront.isAvailable && textureViewBack.isAvailable && shouldProceedWithOnResume) {
            setupCameraFront()
            setupCameraBack()
        } else {
            if (!textureViewFront.isAvailable) {
                textureViewFront.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    @SuppressLint("MissingPermission")
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        setupCameraFront()
                        connectCameraFront()
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                    }
                }
            }
            if (!textureViewBack.isAvailable) {
                textureViewBack.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    @SuppressLint("MissingPermission")
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        setupCameraBack()
                        connectCameraBack()
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                    }
                }
            }
        }

        shouldProceedWithOnResume = !shouldProceedWithOnResume
    }

    fun setupCameraFront() {
        cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds: Array<String> = cameraManager.cameraIdList

        for (id in cameraIds) {
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(id)

            if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                val streamConfigurationMap: StreamConfigurationMap? = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                if (streamConfigurationMap != null) {
                    previewSizeFront = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                        .getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.height * it.width }!!
                    videoSizeFront = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                        .getOutputSizes(MediaRecorder::class.java).maxByOrNull { it.height * it.width }!!
                }
                cameraIdFront = id
            }
        }
    }
    fun setupCameraBack() {
        cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds: Array<String> = cameraManager.cameraIdList

        for (id in cameraIds) {
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(id)

            // If we want to choose the rear facing camera instead of the front facing one
            if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                val streamConfigurationMap: StreamConfigurationMap? = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

                if (streamConfigurationMap != null) {
                    previewSizeBack = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                        .getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.height * it.width }!!
                    videoSizeBack = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                        .getOutputSizes(MediaRecorder::class.java).maxByOrNull { it.height * it.width }!!
                }
                cameraIdBack = id
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectCameraFront() {
        cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraManager.openCamera(cameraIdFront, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                videoCaptureFront = VideoCapture(activity, camera, textureViewFront, previewSizeFront)
                videoCaptureFront.startPreview()
            }

            override fun onDisconnected(cameraDevice: CameraDevice) {
            }

            override fun onError(cameraDevice: CameraDevice, error: Int) {
                val errorMsg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                Log.e(TAG, "Error when trying to connect camera $errorMsg")
            }
        }, handlerFront)
    }
    @SuppressLint("MissingPermission")
    fun connectCameraBack() {
        cameraManager.openCamera(cameraIdBack, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                videoCaptureBack = VideoCapture(activity, camera, textureViewBack, previewSizeBack)
                videoCaptureBack.startPreview()
            }

            override fun onDisconnected(cameraDevice: CameraDevice) {
            }

            override fun onError(cameraDevice: CameraDevice, error: Int) {
                val errorMsg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                Log.e(TAG, "Error when trying to connect camera $errorMsg")
            }
        }, handlerBack)
    }

    /**
     * Background Thread
     */
    private fun startWorkerThreadFront() {
        handlerThreadFront = HandlerThread("CameraVideoThreadFront")
        handlerThreadFront.start()
        handlerFront = Handler(handlerThreadFront.looper)
    }
    private fun startWorkerThreadBack() {
        handlerThreadBack = HandlerThread("CameraVideoThreadBack")
        handlerThreadBack.start()
        handlerBack = Handler(handlerThreadBack.looper)
    }

    private fun stopWorkerThreadFront() {
        handlerThreadFront.quitSafely()
        handlerThreadFront.join()
    }
    private fun stopWorkerThreadBack() {
        handlerThreadBack.quitSafely()
        handlerThreadBack.join()
    }

    fun startRecordingFront() {
        videoCaptureFront.startRecording(videoSizeFront)
    }
    fun startRecordingBack() {
        videoCaptureBack.startRecording(videoSizeBack)
    }

    fun stopRecordingFront() {
        videoCaptureFront.stopRecording()
    }
    fun stopRecordingBack() {
        videoCaptureBack.stopRecording()
    }

    fun takePhotoFront(rotation: Int) {
        videoCaptureFront.takePhoto(rotation)
    }
    fun takePhotoBack(rotation: Int) {
        videoCaptureBack.takePhoto(rotation)
    }

}